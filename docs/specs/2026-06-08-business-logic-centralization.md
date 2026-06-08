# Business Logic Centralization — casehub-life
**Issue:** life#27  
**Date:** 2026-06-08  
**Status:** approved

---

## Problem

Business logic in casehub-life is organized by concern (ledger, routing, attestation, SLA) rather than by domain. Every domain/type enum has its distinct behaviour scattered across switch statements, static maps, and hardcoded conditionals in service and observer classes. The audit found 11 distinct locations across 6 files:

| Location | Scattered logic |
|---|---|
| `LifeOutcomeAttestationWriter` | `DOMAIN_TO_CAPABILITY` static map |
| `LifeTrustRoutingPolicyProvider` | Two correlated static maps (40 entries) |
| `LifeDecisionLedgerObserver` | Domain switch — which ledger writer to call |
| `LifeLedgerWriter` | Three per-domain write methods |
| `LifeTaskService.domainFromCategory()` | Category string → domain switch |
| `LifeTaskService` lines 95–98 | CREATED ledger write hardcoded per domain |
| `LifeActionRiskClassifier` | Action type switches (gate policy, reason, threshold) |
| `LifeWatchdogAlertObserver` | `CommitmentMode` → escalation title switch |
| `LifeSlaBreachPolicy` | Hardcoded 48h / household-admin for all domains |
| `LifeCaseService.resolve()` | Case type switch over injected CaseHub beans |
| `*CaseDefinitions` + `*CaseHub` pairs | 16 classes for 8 case definitions |

**Cost:** Adding a new domain requires touching 4–6 files with no compiler enforcement between them. A missed update (e.g. forgetting `DOMAIN_TO_CAPABILITY`) silently degrades behaviour.

**Reference:** `LifeCommitmentStrategy` is already the correct pattern — three implementations discovered via `Instance<LifeCommitmentStrategy>`, each with `applies()` + `execute()`. No switch. No dispatcher. Adding a fourth commitment type is one new class.

---

## Pattern

Two layers, applied consistently:

### Layer 1 — Descriptor (POJO)

Pure Java, no framework imports, zero injected dependencies. Carries all **declarative knowledge** about a domain or type — everything you need to know about it without running any code. Lives in `api/` (domain descriptors) or `app/` (action type rules, case workers). Testable with plain `new` and plain JUnit.

### Layer 2 — Handler (CDI supplement, optional)

`@ApplicationScoped` CDI bean. Adds **execution behaviour** using infrastructure (repositories, preference providers). Discovered via `Instance<HandlerType>` at the service layer. Optional: if a domain has no handler for a given concern, the service falls back gracefully. Testable with Mockito.

The dispatcher pattern (`Instance<T>` filtered by type identity) replaces every switch statement. No registration step. No dispatcher modification when adding a new type.

**Case hub variant:** `LifeTypedCaseHub` (abstract class) acts as the CDI lifecycle boundary. It owns `getDefinition()` (final, cached) and delegates worker construction to a descriptor POJO. YAML provides the case structure; the descriptor provides the workers. See §Case Hub Descriptors.

---

## Design: Domain Descriptors (`LifeDomain`)

### `LifeSlaPolicy` record — `api/`

Pure Java (no casehub-work-api dep). Carries the SLA escalation inputs; `LifeSlaBreachPolicy` in `app/` constructs the `BreachDecision` from these values.

```java
public record LifeSlaPolicy(String escalationGroup, Duration escalationDeadline) {}
```

### `LifeDomainDescriptor` interface — `api/`

```java
public interface LifeDomainDescriptor {
    String capability();              // maps to LifeCapabilities constants
    String templateCategory();        // "health", "contractor", etc. — reverse of domainFromCategory()
    LifeRoutingPolicy routingPolicy();// trust threshold, minObservations, margin, fallback
    Set<String> workerCapabilities(); // fine-grained worker names routing to this domain
    LifeSlaPolicy slaPolicy();        // escalation group + deadline; LifeSlaBreachPolicy builds BreachDecision
}
```

`LifeRoutingPolicy` moves from `app/routing/` to `api/` — it is domain vocabulary, not app implementation. Its record signature is unchanged: `(OptionalDouble threshold, OptionalInt minimumObservations, OptionalDouble borderlineMargin, Optional<String> fallbackType, String rationale)`.

### `LifeDomain` enum — gains `descriptor()`

```java
public enum LifeDomain {
    HEALTH(new HealthDomainDescriptor()),
    LEGAL(new LegalDomainDescriptor()),
    FINANCE(new FinanceDomainDescriptor()),
    HOUSEHOLD(new HouseholdDomainDescriptor()),
    FAMILY_SCHEDULING(new FamilySchedulingDomainDescriptor()),
    TRAVEL(new TravelDomainDescriptor()),
    CONTRACTOR_COORDINATION(new ContractorCoordinationDomainDescriptor()),
    ELDER_CARE(new ElderCareDomainDescriptor());

    private final LifeDomainDescriptor descriptor;
    LifeDomain(LifeDomainDescriptor d) { this.descriptor = d; }
    public LifeDomainDescriptor descriptor() { return descriptor; }

    public static Optional<LifeDomain> fromCategory(String category) {
        if (category == null) return Optional.empty();
        return Arrays.stream(values())
            .filter(d -> d.descriptor().templateCategory().equals(category))
            .findFirst();
    }
}
```

The null guard preserves the original `domainFromCategory()` contract: null input returns no match. All callers use `.orElse(LifeDomain.HOUSEHOLD)` as the fallback, matching the original method's default for unknown categories.

### Eight descriptor POJOs — `api/descriptor/`

One per domain. Example — domain with all routing fields present (HEALTH):

```java
public final class HealthDomainDescriptor implements LifeDomainDescriptor {
    public String capability()       { return LifeCapabilities.HEALTH_COORDINATION; }
    public String templateCategory() { return "health"; }
    public Set<String> workerCapabilities() {
        return Set.of("book-appointment", "find-alternative", "confirm-appointment",
                      "pre-visit-prep", "record-health-decision");
    }
    public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(
            OptionalDouble.of(0.75),
            OptionalInt.of(10),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "High reliability required for health appointments and follow-ups");
    }
    public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(24));
    }
}
```

Example — domain with absent margin and no fallback (HOUSEHOLD):

```java
public final class HouseholdDomainDescriptor implements LifeDomainDescriptor {
    public String capability()       { return LifeCapabilities.HOUSEHOLD_MANAGEMENT; }
    public String templateCategory() { return "household"; }
    public Set<String> workerCapabilities() {
        return Set.of("schedule-inspection", "get-quotes", "issue-commitment",
                      "monitor-job", "record-completion");
    }
    public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(
            OptionalDouble.of(0.50),
            OptionalInt.of(5),
            OptionalDouble.empty(),
            Optional.empty(),
            "Routine household tasks tolerate lower threshold, no escalation");
    }
    public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(48));
    }
}
```

HOUSEHOLD and FAMILY_SCHEDULING have `OptionalDouble.empty()` for `borderlineMargin` and `Optional.empty()` for `fallbackType` — these are the only two domains without a fallback escalation path.

### What this eliminates

| Was | Becomes |
|---|---|
| `LifeOutcomeAttestationWriter.DOMAIN_TO_CAPABILITY` static map (ctx.domain path) | `ctx.domain.descriptor().capability()` |
| `LifeOutcomeAttestationWriter` scope-parse fallback: `DOMAIN_TO_CAPABILITY.get(domain)` | `LifeDomain.valueOf(segments[2].toUpperCase()).descriptor().capability()` |
| `LifeTrustRoutingPolicyProvider.CAPABILITY_TO_DOMAIN` (32 entries) | `@PostConstruct` capability index derived from descriptors — see §Trust Routing below |
| `LifeTrustRoutingPolicyProvider.POLICIES` (8 entries) | `domain.descriptor().routingPolicy()` |
| `LifeTaskService.domainFromCategory()` switch | `LifeDomain.fromCategory(category)` |
| `LifeSlaBreachPolicy` universal 48h/household-admin | `domain.descriptor().slaPolicy()` |

### Trust routing — capability index

`LifeTrustRoutingPolicyProvider` builds a reverse `Map<String, LifeDomain>` at startup instead of the static `CAPABILITY_TO_DOMAIN` map. O(1) lookup is preserved:

```java
@PostConstruct
void buildCapabilityIndex() {
    Map<String, LifeDomain> index = new HashMap<>();
    for (LifeDomain domain : LifeDomain.values()) {
        for (String cap : domain.descriptor().workerCapabilities()) {
            index.put(cap, domain);
        }
    }
    this.capabilityIndex = Map.copyOf(index);
}

@Override
public TrustRoutingPolicy forCapability(String capabilityName) {
    LifeDomain domain = capabilityIndex.get(capabilityName);
    if (domain == null) return TrustRoutingPolicy.DEFAULT;

    LifeRoutingPolicy base = domain.descriptor().routingPolicy();
    // Scope key uses capability() — same string as old POLICIES map key.
    // trust-routing.yaml keys are unchanged: "health-coordination", "legal-deadline", etc.
    SettingsScope scope = SettingsScope.of("casehubio", "life", "trust-routing",
        domain.descriptor().capability());
    Preferences prefs = preferenceProvider.resolve(scope);
    // ... build TrustRoutingPolicy from base + YAML overlays (blend factor, quality floors)
}
```

`domain.descriptor().capability()` returns the same coarse-grained strings the old `POLICIES` map used as keys (`LifeCapabilities.HEALTH_COORDINATION`, etc.), so `trust-routing.yaml` requires no changes.

---

## Design: Domain Ledger Handlers

### `DomainLedgerHandler` interface — `app/service/ledger/`

```java
public interface DomainLedgerHandler {
    LifeDomain domain();
    // WorkItem-based write: used by LifeDecisionLedgerObserver and LifeTaskService (CREATED)
    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);
    // Commitment-based write: default no-op; only FinanceDomainLedgerHandler implements
    default void writeEntry(LifeDecisionEventType event, LifeCommitmentRecord record) { }
}
```

The commitment-based overload covers two FINANCE write paths where a `LifeCommitmentRecord` exists but no WorkItem does: CREATED entries generated by `OversightGateStrategy` at gate-creation time, and SLA_BREACH entries generated by `LifeWatchdogAlertObserver` when an OVERSIGHT gate expires without a response. No other domain needs this overload.

### Four CDI implementations

- `HealthDomainLedgerHandler` — fetches `LifeTaskContext` internally; writes `HealthDecisionLedgerEntry` + attestation; active for CREATED, SLA_BREACH, COMPLETED. The context is no longer fetched twice explicitly by the observer to pass as an argument to the writer — domain resolution uses `workItem.scope` first, with a `LifeTaskContext` lookup only as fallback in `resolveDomain()`. In scope-present cases (the common path), the handler is the only site that fetches context. **Null-context guard:** if `LifeTaskContext.findByIdOptional(workItemId)` returns empty, the handler returns immediately without writing an entry and logs a warning — a HEALTH task should always have context; absence indicates a data integrity problem worth surfacing.
- `LegalDomainLedgerHandler` — same structure as HEALTH; writes `LegalActionLedgerEntry` + attestation. Same null-context guard and warning behaviour.
- `FinanceDomainLedgerHandler` — implements both overloads:
  - `writeEntry(event, workItemId, workItem)` (WorkItem-based): used by observer for SLA_BREACH and COMPLETED. Returns immediately if no `LifeCommitmentRecord` found for the workItemId. Returns immediately for CREATED event — FINANCE task CREATED writes are commitment-initiated, not task-initiated.
  - `writeEntry(event, record)` (commitment-based): used by `OversightGateStrategy` for FINANCE CREATED, and by `LifeWatchdogAlertObserver` for FINANCE SLA_BREACH on OVERSIGHT records. Takes the `LifeCommitmentRecord` directly (no WorkItem exists in either case).

HOUSEHOLD and FAMILY_SCHEDULING have no handler — no ledger entries are written for these domains. Handler absence is the canonical signal; no flag on the descriptor is needed.

### `OversightGateStrategy` migration

`OversightGateStrategy` currently injects `LifeLedgerWriter` to call `writeFinancialEntry(CREATED, record, null)` after persisting the oversight commitment record. After this refactor, `LifeLedgerWriter.writeFinancialEntry()` is removed. `OversightGateStrategy` instead injects `Instance<DomainLedgerHandler>` and calls the commitment-based overload:

```java
handlers.stream()
    .filter(h -> h.domain() == LifeDomain.FINANCE)
    .findFirst()
    .ifPresent(h -> h.writeEntry(LifeDecisionEventType.CREATED, record));
```

`LifeLedgerWriter` is no longer injected in `OversightGateStrategy`.

### `LifeWatchdogAlertObserver` migration

`LifeWatchdogAlertObserver` currently injects `LifeLedgerWriter` (for FINANCE SLA_BREACH writes) and neither `Instance<DomainLedgerHandler>` nor `Instance<LifeCommitmentStrategy>`. After the refactor, it:

- **Loses** `LifeLedgerWriter` injection — replaced by `Instance<DomainLedgerHandler>`
- **Gains** `Instance<DomainLedgerHandler>` — for FINANCE commitment-based SLA_BREACH writes
- **Gains** `Instance<LifeCommitmentStrategy>` — for mode-matched escalation title lookup (replaces the `CommitmentMode` switch)

FINANCE write path after refactor:
```java
if (record.mode == CommitmentMode.OVERSIGHT) {
    handlers.stream()
        .filter(h -> h.domain() == LifeDomain.FINANCE)
        .findFirst()
        .ifPresent(h -> h.writeEntry(LifeDecisionEventType.SLA_BREACH, record));
}
```

Escalation title after refactor:
```java
String title = strategies.stream()
    .filter(s -> s.commitmentMode() == record.mode)
    .findFirst()
    .map(s -> s.escalationTitle(record))
    .orElse("Commitment expired — action required");
```

### Services after refactor

**`LifeDecisionLedgerObserver`** — no switch; delegates to handler. The null guard from `resolveDomain()` is preserved — if domain cannot be resolved from scope or context, nothing is written:

```java
LifeDomain domain = resolveDomain(workItemId, workItem);
if (domain == null) return; // guard preserved — scope-less tasks produce no ledger entry
handlers.stream()
    .filter(h -> h.domain() == domain)
    .findFirst()
    .ifPresent(h -> h.writeEntry(event, workItemId, workItem));
```

`null == LifeDomain.HEALTH` is `false` in Java (no NPE from the stream), but the guard is retained to make intent explicit: an unresolvable domain is a deliberate no-op, not an error.

**`LifeTaskService`** — `domainFromCategory()` switch removed; injects `Instance<DomainLedgerHandler>` instead of `LifeLedgerWriter`. CREATED dispatch:

```java
handlers.stream()
    .filter(h -> h.domain() == domain)
    .findFirst()
    .ifPresent(h -> h.writeEntry(LifeDecisionEventType.CREATED, workItem.id, workItem));
```

HEALTH and LEGAL handlers write on CREATED. FINANCE handler returns immediately for CREATED (its CREATED write goes through `OversightGateStrategy`). HOUSEHOLD/FAMILY_SCHEDULING have no handler — nothing is written.

**`LifeOutcomeAttestationWriter.resolveCapabilityTag()`** — both lookup paths use the descriptor. Path 1 (ctx.domain present): `ctx.domain.descriptor().capability()`. Path 2 (scope-parse fallback, the only production path that fires for LEGAL and ELDER_CARE tasks whose `LifeTaskContext.domain` is null): `LifeDomain.valueOf(segments[2].toUpperCase()).descriptor().capability()`. `DOMAIN_TO_CAPABILITY` is fully removed; neither path references it.

**`LifeLedgerWriter`** — shrinks to `writeErasureEntry()` (GDPR) + `populateBase()` helper for handlers. Methods `writeHealthEntry`, `writeFinancialEntry`, `writeLegalEntry` removed.

**`LifeSlaBreachPolicy`** — becomes thin dispatcher:
```java
LifeDomain domain = LifeDomain.fromCategory(ctx.task().category()).orElse(LifeDomain.HOUSEHOLD);
LifeSlaPolicy policy = domain.descriptor().slaPolicy();
if (ctx.task().candidateGroups().contains(policy.escalationGroup()))
    return new BreachDecision.Fail("life-sla-exhausted");
return BreachDecision.EscalateTo.to(policy.escalationGroup()).withDeadline(policy.escalationDeadline());
```

---

## Design: Action Type Rules (`HouseholdActionType`)

### `HouseholdRiskRule` interface — `app/routing/`

```java
public interface HouseholdRiskRule {
    String OVERSIGHT_SCOPE = "casehubio/life/oversight"; // public static final

    HouseholdActionType actionType();
    RiskDecision evaluate(PlannedAction action, Preferences prefs);

    default RiskDecision.GateRequired gate(HouseholdActionType type, PlannedAction action,
                                           Preferences prefs, String reason) {
        long hours = (long) prefs.get(LifeRiskPolicyKeys.APPROVAL_EXPIRES_HOURS).value();
        return new RiskDecision.GateRequired(
            reason, type.reversible(), type.candidateGroups(),
            Duration.ofHours(hours), OVERSIGHT_SCOPE);
    }

    default OptionalDouble parseAmount(Map<String, Object> context) {
        Object raw = context.get("amount");
        if (raw == null) return OptionalDouble.empty();
        try { return OptionalDouble.of(Double.parseDouble(raw.toString())); }
        catch (NumberFormatException e) { return OptionalDouble.empty(); }
    }

    default String formatAmount(Map<String, Object> context) {
        Object amount   = context.get("amount");
        Object currency = context.getOrDefault("currency", "GBP");
        return amount != null ? currency + " " + amount : "unspecified amount";
    }
}
```

`OVERSIGHT_SCOPE`, `gate()`, `parseAmount()`, and `formatAmount()` move here from `LifeActionRiskClassifier` (where they were private methods). The classifier becomes a pure dispatcher: it resolves `Preferences` once and passes it to `rule.evaluate(action, prefs)`. Each rule uses the interface's default helpers for gate construction — no per-rule duplication.

### Eleven POJO rule implementations — `app/routing/rules/`

One per `HouseholdActionType` constant. Pure functions: `(PlannedAction, Preferences) → RiskDecision`. No injected deps. The dispatcher resolves `Preferences` once and passes it in.

`ThresholdCategory` removed from `HouseholdActionType` — it was always a classifier implementation detail. Each `AMOUNT_THRESHOLD` rule references its preference key directly.

Example:
```java
public final class SpendPurchaseRule implements HouseholdRiskRule {
    public HouseholdActionType actionType() { return SPEND_PURCHASE; }
    public RiskDecision evaluate(PlannedAction action, Preferences prefs) {
        return parseAmount(action.context())
            .filter(a -> a >= prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD).value())
            .mapToObj(a -> (RiskDecision) gate(SPEND_PURCHASE, action, prefs,
                "Spend of " + formatAmount(action.context()) + " requires household approval"))
            .orElse(new RiskDecision.Autonomous());
    }
}
```

### `LifeActionRiskClassifier` — thin dispatcher

Builds `Map<HouseholdActionType, HouseholdRiskRule>` at startup via `Instance<HouseholdRiskRule>`. **Startup validation:** throws `IllegalStateException` if any `HouseholdActionType` value has no registered rule — makes missing rules immediately visible rather than silently returning `Autonomous`.

---

## Design: Commitment Mode Escalation

### `LifeCommitmentStrategy` gains `escalationTitle()` and `commitmentMode()`

```java
public interface LifeCommitmentStrategy {
    boolean applies(CommitmentContext context);
    CommitmentOutcome execute(CommitmentContext context);
    String escalationTitle(LifeCommitmentRecord record);  // new — mode-specific message
    CommitmentMode commitmentMode();                       // new — mode identity for observer lookup
}
```

Each of the three existing strategies implements both new methods directly — each already knows its mode and its escalation message. `LifeWatchdogAlertObserver` removes the switch and uses:

```java
strategies.stream()
    .filter(s -> s.commitmentMode() == record.mode)
    .findFirst()
    .ifPresent(s -> title = s.escalationTitle(record));
```

`commitmentMode()` is a mode identity declaration for lookup purposes, distinct from `applies(CommitmentContext)` which tests execution eligibility against a reconstituted context. Both serve different callers.

---

## Design: Case Hub Descriptors (`LifeCaseType`)

### `LifeTypedCaseHub` abstract class — `app/engine/`

`LifeTypedCaseHub` is an abstract class, not an interface — it extends `YamlCaseHub` and owns the augmentation lifecycle. CDI shells extend it directly:

```java
public abstract class LifeTypedCaseHub extends YamlCaseHub {
    private volatile CaseDefinition augmented;

    protected LifeTypedCaseHub(String yamlPath) {
        super(yamlPath);
    }

    public abstract LifeCaseType lifeCaseType();
    protected abstract List<Worker> workers();

    @Override
    public final CaseDefinition getDefinition() {
        if (augmented == null) {
            synchronized (this) {
                if (augmented == null) {
                    CaseDefinition base = super.getDefinition(); // YAML load (reentrant — same lock)
                    base.getWorkers().addAll(workers());
                    augmented = base;
                }
            }
        }
        return augmented;
    }
}
```

`getDefinition()` is `final` — caching and augmentation logic cannot be overridden. `workers()` is called exactly once under the lock. `super.getDefinition()` is also synchronized on `this`, which is safe because Java `synchronized` is reentrant for the same thread.

`LifeTypedCaseHub extends YamlCaseHub extends CaseHub`. `LifeCaseService` injects `Instance<LifeTypedCaseHub>`, which returns `CaseHub`-typed results through the inheritance chain — no cast required.

### Six descriptor POJOs — `app/engine/`

One per `LifeCaseType`. Provides `workers()` only — YAML owns the case structure (bindings, goals, capabilities). Example:

```java
public final class AppointmentCycleDescriptor {
    public List<Worker> workers() {
        return List.of(
            bookAppointmentWorker(),
            findAlternativeWorker(),
            confirmAppointmentWorker(),
            preVisitPrepWorker(),
            recordHealthDecisionWorker()
        );
    }
    private Worker bookAppointmentWorker() { ... }
    // ...
}
```

The descriptor has no CDI annotations and no injected deps. When Layer 7 (OpenClaw) wires real implementations, CDI deps are passed via constructor — the CDI shell injects them and passes them through.

### Six thin CDI shells — extend `LifeTypedCaseHub`

```java
@ApplicationScoped
public class AppointmentCycleCaseHub extends LifeTypedCaseHub {
    public AppointmentCycleCaseHub() { super("life/appointment-cycle.yaml"); }

    @Override public LifeCaseType lifeCaseType() { return LifeCaseType.APPOINTMENT_CYCLE; }
    @Override protected List<Worker> workers() { return new AppointmentCycleDescriptor().workers(); }
}
```

The descriptor is instantiated inside `workers()`, which is called at most once (under `getDefinition()`'s lock). No separate caching needed in the shell.

### Sub-case hubs — explicit scoping

**`FamilyVoteCaseHub`** — YAML-only; no workers; no descriptor. Stays exactly as-is. `FamilyVote` has no `LifeCaseType` (it is spawned only as a sub-case by the engine). Does NOT extend `LifeTypedCaseHub`.

**`CareEpisodeCaseHub`** — has workers but no `LifeCaseType` (spawned as sub-case by `care-coordination`). Gets a `CareEpisodeDescriptor` that provides `workers()`, but stays a direct `YamlCaseHub` extension — it does NOT extend `LifeTypedCaseHub`. Manages its own augmentation lifecycle (same double-checked locking pattern as the current code, updated to delegate worker construction to the descriptor).

### `LifeCaseService.resolve()` — no switch

```java
@Inject Instance<LifeTypedCaseHub> caseHubs;

private CaseHub resolve(LifeCaseType type) {
    return caseHubs.stream()
        .filter(h -> h.lifeCaseType() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No CaseHub for type: " + type));
}
```

`FamilyVoteCaseHub` and `CareEpisodeCaseHub` do not extend `LifeTypedCaseHub`, so they are not in the `Instance` — the resolver cannot accidentally return them for direct-start requests.

### DSL companion deprecation

The 8 `*CaseDefinitions` companion classes are removed as part of this change. PP-20260518 is updated: the paired DSL companion pattern is deprecated for case hubs. YAML is the canonical case structure source; `*Descriptor` POJOs are the testable Java artifact for workers. A follow-up task (see Deferred) records the protocol update.

---

## Module layout after

```
api/
  LifeDomain               ← add descriptor() + fromCategory()
  LifeDomainDescriptor     ← new interface (no producesLedgerEntry)
  LifeRoutingPolicy        ← moved from app/routing/ (record signature unchanged)
  LifeSlaPolicy            ← new record
  descriptor/
    HealthDomainDescriptor
    LegalDomainDescriptor
    FinanceDomainDescriptor
    HouseholdDomainDescriptor
    FamilySchedulingDomainDescriptor
    TravelDomainDescriptor
    ContractorCoordinationDomainDescriptor
    ElderCareDomainDescriptor
  HouseholdActionType      ← remove ThresholdCategory enum + field

app/
  routing/
    HouseholdRiskRule        ← new interface
    LifeActionRiskClassifier ← thin dispatcher with startup validation
    rules/
      SpendPurchaseRule
      SpendSubscriptionCancelRule
      SpendSubscriptionModifyRule
      BookingNonrefundableRule
      BookingRefundableRule
      HealthSpecialistAppointmentRule
      GpAppointmentRule
      HealthMedicationFlagRule
      ContractorEngageRule
      LegalDocumentSubmitRule
      ElderCareDecisionRule
  service/ledger/
    DomainLedgerHandler       ← new interface (two writeEntry overloads)
    HealthDomainLedgerHandler ← new CDI handler
    LegalDomainLedgerHandler  ← new CDI handler
    FinanceDomainLedgerHandler← new CDI handler (both overloads)
    LifeLedgerWriter          ← shrinks: erasure + populateBase only
    LifeOutcomeAttestationWriter ← simplified: no DOMAIN_TO_CAPABILITY map
  commitment/
    LifeCommitmentStrategy    ← add escalationTitle() + commitmentMode()
  spi/
    LifeSlaBreachPolicy       ← thin dispatcher using domain.descriptor().slaPolicy()
  engine/
    LifeTypedCaseHub          ← new abstract class (extends YamlCaseHub)
    AppointmentCycleDescriptor← new POJO (workers only)
    HomeMaintenanceDescriptor
    TravelPlanDescriptor
    CareCoordinationDescriptor
    ContractorCoordinationDescriptor
    FinancialReviewDescriptor
    CareEpisodeDescriptor     ← new POJO (workers only; for CareEpisodeCaseHub)
    AppointmentCycleCaseHub   ← thin shell (extends LifeTypedCaseHub)
    HomeMaintenanceCaseHub
    TravelPlanCaseHub
    CareCoordinationCaseHub
    ContractorCoordinationCaseHub
    FinancialReviewCaseHub
    CareEpisodeCaseHub        ← updated: delegates workers() to descriptor; stays YamlCaseHub
    FamilyVoteCaseHub         ← unchanged
    (8 *CaseDefinitions companions removed)
```

---

## Testing

### Descriptor tests (pure JUnit, `api/`)
- One test class per domain descriptor: verify `capability()`, `templateCategory()`, `routingPolicy()` values (including empty optionals for HOUSEHOLD/FAMILY_SCHEDULING), `workerCapabilities()` set, `slaPolicy()` escalation group and deadline
- `LifeDomain.fromCategory()` roundtrip for all 8 descriptors

### Risk rule tests (pure JUnit, `app/routing/rules/`)
- One test class per rule: verify ALWAYS/NEVER/AMOUNT_THRESHOLD decisions with mock `Preferences`
- Boundary: at/below/above threshold, missing amount, unparseable amount

### Handler tests (Mockito, `app/service/ledger/`)
- Mock `LedgerEntryRepository` + dependencies
- `HealthDomainLedgerHandler`, `LegalDomainLedgerHandler`: verify correct `LedgerEntry` subclass constructed, fields set, attestation triggered
- `FinanceDomainLedgerHandler`: verify WorkItem-based overload writes on SLA_BREACH/COMPLETED; verify no-op on CREATED (WorkItem-based); verify commitment-based overload writes correctly for oversight CREATED (via `OversightGateStrategy` path) and OVERSIGHT SLA_BREACH (via `LifeWatchdogAlertObserver` path)
- Do not assert on `entry.id` or `entry.occurredAt` — set by `@PrePersist`, bypassed in Mockito tests

### Commitment strategy tests (Mockito, `app/commitment/`)
- Verify `commitmentMode()` returns correct enum for each implementation
- Verify `escalationTitle(record)` produces correct message for each mode

### Case descriptor tests (pure JUnit, `app/engine/`)
- One test class per descriptor: verify `workers()` returns non-empty list with correct worker names
- No Quarkus startup needed

### Dispatcher tests (Mockito)
- `LifeActionRiskClassifier`: startup validation throws on missing rule; correct rule called per action type
- `LifeDecisionLedgerObserver`: correct handler called per domain; domains without handler produce no entry
- `LifeCaseService`: correct hub resolved per case type; missing hub throws; `FamilyVoteCaseHub` not in `Instance<LifeTypedCaseHub>`

### Updated integration tests
- `LifeActionRiskClassifierQuarkusTest` — CDI wiring still satisfied
- `LifeTrustRoutingPolicyProviderTest` — routing policy values unchanged (derived from descriptors), YAML overlay still works; verify `@PostConstruct` index resolves all expected worker capability names
- `LifeSlaBreachPolicyTest` — per-domain escalation deadlines verified via `@QuarkusTest`

---

## Deferred / follow-up

| Issue | Description |
|---|---|
| life#30 | Second-pass audit — verify nothing was missed in this sweep |
| parent#202 | Universal descriptor+handler coherence protocol for all casehubio application repos |
| parent#204 | Update PP-20260518 — DSL companion pattern deprecated for case hubs; `*Descriptor` POJO replaces `*CaseDefinitions` as testable Java artifact; YAML is canonical structure |
| life#26 | RBAC-differentiated risk thresholds (blocked on auth retrofit) |

---

## Platform coherence

- All descriptor POJOs are in `api/` or `app/` — no framework leakage ✅
- `LifeRoutingPolicy` move from `app/` to `api/` is a package rename; record signature unchanged ✅
- `ThresholdCategory` removal from `HouseholdActionType` — no external consumers ✅
- `DomainLedgerHandler` interface in `app/` — correct: references JPA entities ✅
- `LifeTypedCaseHub` as abstract class (not interface) — Java requirement; extends `YamlCaseHub`; `CaseHub` return type preserved through inheritance ✅
- `FamilyVoteCaseHub` and `CareEpisodeCaseHub` excluded from `LifeTypedCaseHub` — both are sub-case hubs with no `LifeCaseType`; engine discovers them as `CaseHub` beans regardless ✅
- `LifeCommitmentStrategy.escalationTitle()` and `commitmentMode()` are additive — all three implementations provide them ✅
- YAML trust-routing scope keys unchanged — `domain.descriptor().capability()` returns the same coarse-grained strings as the old `POLICIES` map ✅
- `OversightGateStrategy` migrates `LifeLedgerWriter` injection to `Instance<DomainLedgerHandler>` — FINANCE CREATED write path preserved via commitment-based overload ✅
