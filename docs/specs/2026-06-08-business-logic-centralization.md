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
| `*CaseHub` worker methods | Worker lambdas buried in CDI beans alongside YAML augmentation |

**Cost:** Adding a new domain requires touching 4–6 files with no compiler enforcement between them. A missed update (e.g. forgetting `DOMAIN_TO_CAPABILITY`) silently degrades behaviour.

**Reference:** `LifeCommitmentStrategy` is already the correct pattern — three implementations discovered via `Instance<LifeCommitmentStrategy>`, each with `applies()` + `execute()`. No switch. No dispatcher. Adding a fourth commitment type is one new class.

---

## Pattern

Two layers, applied consistently:

### Layer 1 — Descriptor (POJO)

Pure Java, no framework imports, zero injected dependencies. Carries all **declarative knowledge** about a domain or type — everything you need to know about it without running any code. Lives in `api/` (domain descriptors) or `app/` (case descriptors, risk rules — these reference engine-api types and cannot be in api/). Testable with plain `new` and plain JUnit.

### Layer 2 — Handler or CDI shell (CDI supplement, optional)

`@ApplicationScoped` CDI bean. Adds **execution behaviour** using infrastructure (repositories, preference providers). Discovered via `Instance<HandlerType>` or `Instance<BaseClass>` at the service layer. Optional: if a domain has no handler for a given concern, the service falls back gracefully via `ifPresent()`. Testable with Mockito.

The dispatcher pattern (`Instance<T>` filtered by type identity) replaces every switch statement. No registration step. No dispatcher modification when adding a new type.

---

## Design 1: Domain Descriptors (`LifeDomain`)

### `LifeSlaPolicy` record — `api/`

Pure Java (no casehub-work-api dep). Carries the SLA escalation inputs; `LifeSlaBreachPolicy` in `app/` constructs the `BreachDecision` from these values. The two-tier detection logic (is `escalationGroup` already in `candidateGroups`?) stays in `LifeSlaBreachPolicy` where it belongs.

```java
public record LifeSlaPolicy(String escalationGroup, Duration escalationDeadline) {}
```

### `LifeDomainDescriptor` interface — `api/`

```java
public interface LifeDomainDescriptor {
    String capability();             // maps to LifeCapabilities constant
    String templateCategory();       // "health", "contractor", etc. — reverse of domainFromCategory()
    LifeRoutingPolicy routingPolicy(); // trust threshold, minObservations, margin, fallback
    Set<String> workerCapabilities(); // fine-grained worker names routing to this domain
    LifeSlaPolicy slaPolicy();       // escalation group + deadline; LifeSlaBreachPolicy builds BreachDecision
}
```

Note: `producesLedgerEntry()` is intentionally absent. The presence or absence of a `DomainLedgerHandler` bean is the canonical signal — having a flag that duplicates this introduces a false abstraction (FINANCE has a handler but uses `LifeCommitmentRecord`, not `LifeTaskContext`; the handler abstracts that entirely). Handler presence = ledger written.

### `LifeDomain` enum — gains `descriptor()` and `fromCategory()`

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
        if (category == null) return Optional.of(HOUSEHOLD);
        return Arrays.stream(values())
            .filter(d -> d.descriptor().templateCategory().equals(category))
            .findFirst();
    }
}
```

### Eight descriptor POJOs — `api/descriptor/`

One per domain. All pure Java — `LifeRoutingPolicy`, `LifeSlaPolicy`, `Set<String>`, `String` only. Example:

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
            OptionalDouble.of(0.75), OptionalInt.of(10), OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "High reliability required for health appointments and follow-ups");
    }
    public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(24));
    }
}
```

HOUSEHOLD and FAMILY_SCHEDULING use `OptionalDouble.empty()` and `OptionalInt.empty()` for threshold and margin — these domains have no trust threshold configured:

```java
public LifeRoutingPolicy routingPolicy() {
    return new LifeRoutingPolicy(
        OptionalDouble.of(0.50), OptionalInt.of(5), OptionalDouble.empty(),
        Optional.empty(),
        "Routine household tasks tolerate lower threshold, no escalation");
}
```

### `LifeRoutingPolicy` — moves from `app/routing/` to `api/`

Unchanged record signature (`OptionalDouble`, `OptionalInt`, `Optional<String>`, `String`). Package change only. All callers update imports.

### What this eliminates

| Was | Becomes |
|---|---|
| `LifeOutcomeAttestationWriter.DOMAIN_TO_CAPABILITY` static map | `domain.descriptor().capability()` |
| `LifeTrustRoutingPolicyProvider.CAPABILITY_TO_DOMAIN` (32 entries) | `@PostConstruct`-built `Map<String, LifeDomain>` derived from all `descriptor().workerCapabilities()` |
| `LifeTrustRoutingPolicyProvider.POLICIES` (8 entries) | `domain.descriptor().routingPolicy()` |
| `LifeTaskService.domainFromCategory()` switch | `LifeDomain.fromCategory(category)` |
| `LifeSlaBreachPolicy` universal 48h/household-admin | `domain.descriptor().slaPolicy()` |

### `LifeTrustRoutingPolicyProvider` — capability index

`forCapability()` is called on every worker execution during case runs. Replacing the O(1) HashMap lookup with a stream scan would be an O(n×m) regression. Instead, build the reverse index at startup:

```java
private Map<String, LifeDomain> capabilityIndex;

@PostConstruct
void buildCapabilityIndex() {
    Map<String, LifeDomain> index = new HashMap<>();
    for (LifeDomain domain : LifeDomain.values()) {
        // fine-grained worker capabilities
        domain.descriptor().workerCapabilities().forEach(cap -> index.put(cap, domain));
        // coarse-grained self-map (for direct capability name lookups)
        index.put(domain.descriptor().capability(), domain);
    }
    this.capabilityIndex = Map.copyOf(index);
}
```

O(1) lookup preserved. The map is derived from descriptors rather than declared.

**YAML scope keys are unchanged.** The current code derives the YAML settings scope key from the coarse-grained capability string (e.g. `"health-coordination"`). After the refactor, `domain.descriptor().capability()` returns the identical strings — `LifeCapabilities.HEALTH_COORDINATION` etc. Trust-routing YAML files require no changes.

---

## Design 2: Domain Ledger Handlers

### `DomainLedgerHandler` interface — `app/service/ledger/`

```java
public interface DomainLedgerHandler {
    LifeDomain domain();
    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);
}
```

Covers all three event types: `CREATED`, `SLA_BREACH`, `COMPLETED`. Each handler fetches its own context internally, eliminating the double-fetch currently in `LifeDecisionLedgerObserver` (which calls `LifeTaskContext.findByIdOptional` in `resolveDomain()` and then again inside the switch branch).

### Three CDI implementations

- `HealthDomainLedgerHandler` — fetches `LifeTaskContext` → builds `HealthDecisionLedgerEntry` + attestation. Handles all three event types.
- `LegalDomainLedgerHandler` — fetches `LifeTaskContext` → builds `LegalActionLedgerEntry` + attestation. Handles all three event types.
- `FinanceDomainLedgerHandler` — fetches `LifeCommitmentRecord` → builds `FinancialDecisionLedgerEntry`. **Returns early on `CREATED` event** — finance entries are written only when a commitment record exists (created by the commitment strategy, not by task creation).

### CREATED event path in `LifeTaskService`

`LifeTaskService` currently writes CREATED entries for HEALTH and LEGAL directly (lines 95–98). After refactor, `LifeTaskService` injects `Instance<DomainLedgerHandler>` and dispatches through it for CREATED events — the same pattern as `LifeDecisionLedgerObserver`:

```java
@Inject Instance<DomainLedgerHandler> ledgerHandlers;

// After WorkItem and LifeTaskContext are persisted:
LifeDomain domain = LifeDomain.fromCategory(template.category).orElse(LifeDomain.HOUSEHOLD);
ledgerHandlers.stream()
    .filter(h -> h.domain() == domain)
    .findFirst()
    .ifPresent(h -> h.writeEntry(LifeDecisionEventType.CREATED, workItem.id, workItem));
```

Domains with no handler (HOUSEHOLD, FAMILY_SCHEDULING, TRAVEL, etc.) silently produce no CREATED entry — the same as today.

### `LifeDecisionLedgerObserver` after refactor

No switch. Shared resolution + handler dispatch:

```java
@Inject Instance<DomainLedgerHandler> handlers;

private void resolveAndWrite(UUID workItemId, LifeDecisionEventType eventType) {
    WorkItem workItem = WorkItem.findByIdOptional(workItemId).orElse(null);
    if (workItem == null) return;
    LifeDomain domain = resolveDomain(workItemId, workItem);
    if (domain == null) return;
    handlers.stream()
        .filter(h -> h.domain() == domain)
        .findFirst()
        .ifPresent(h -> h.writeEntry(eventType, workItemId, workItem));
}
```

### `LifeLedgerWriter` after refactor

Loses all three domain-specific write methods. Becomes a shared utility with:
- `writeErasureEntry(ExternalActor, String)` — GDPR erasure, no domain dispatch
- `populateBase(LedgerEntry, UUID, String, ActorType, String)` — shared field population used by handlers

---

## Design 3: Action Type Rules (`HouseholdActionType`)

### `HouseholdRiskRule` interface — `app/routing/`

```java
public interface HouseholdRiskRule {
    HouseholdActionType actionType();
    RiskDecision evaluate(PlannedAction action, Preferences prefs);
}
```

### Eleven POJO rule implementations — `app/routing/rules/`

One per `HouseholdActionType` constant. Pure functions: `(PlannedAction, Preferences) → RiskDecision`. No injected dependencies. The dispatcher resolves `Preferences` once and passes it in.

`ThresholdCategory` removed from `HouseholdActionType` — it was always a classifier implementation detail. Each `AMOUNT_THRESHOLD` rule references its preference key directly.

Example:
```java
public final class SpendPurchaseRule implements HouseholdRiskRule {
    public HouseholdActionType actionType() { return SPEND_PURCHASE; }
    public RiskDecision evaluate(PlannedAction action, Preferences prefs) {
        double amount = parseAmount(action.context());
        if (amount < prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD).value())
            return new RiskDecision.Autonomous();
        return new RiskDecision.GateRequired(
            "Spend of " + formatAmount(action.context()) + " requires household approval",
            SPEND_PURCHASE.reversible(), SPEND_PURCHASE.candidateGroups(),
            Duration.ofHours((long) prefs.get(LifeRiskPolicyKeys.APPROVAL_EXPIRES_HOURS).value()),
            "casehubio/life/oversight");
    }
}
```

### `LifeActionRiskClassifier` — thin dispatcher with startup validation

Builds `Map<HouseholdActionType, HouseholdRiskRule>` at construction from `Instance<HouseholdRiskRule>`. **Startup validation:** throws `IllegalStateException` if any `HouseholdActionType` value has no registered rule — missing rules fail fast rather than silently returning `Autonomous`.

---

## Design 4: Commitment Mode Escalation

### `LifeCommitmentStrategy` gains two methods

```java
public interface LifeCommitmentStrategy {
    boolean applies(CommitmentContext context);
    CommitmentOutcome execute(CommitmentContext context);
    CommitmentMode commitmentMode();              // new — identifies which mode this strategy owns
    String escalationTitle(LifeCommitmentRecord record); // new — all three impls provide this
}
```

`commitmentMode()` returns the `CommitmentMode` enum constant this strategy handles. The observer uses it for a type-safe lookup:

```java
strategies.stream()
    .filter(s -> s.commitmentMode() == record.mode)
    .findFirst()
    .map(s -> s.escalationTitle(record))
    .orElse("Commitment expired — action required");
```

Both new methods are abstract. All three existing implementations (`DelegationCommitmentStrategy`, `ContractorCommitmentStrategy`, `OversightGateStrategy`) implement them directly — each already encodes its mode and knows its escalation message. The switch in `LifeWatchdogAlertObserver.createEscalationTask()` is removed.

### `LifeSlaBreachPolicy` — thin dispatcher

```java
LifeDomain domain = LifeDomain.fromCategory(ctx.task().category()).orElse(LifeDomain.HOUSEHOLD);
LifeSlaPolicy policy = domain.descriptor().slaPolicy();
if (ctx.task().candidateGroups().contains(policy.escalationGroup()))
    return new BreachDecision.Fail("life-sla-exhausted");
return BreachDecision.EscalateTo.to(policy.escalationGroup())
    .withDeadline(policy.escalationDeadline());
```

The two-tier detection logic (check if escalation group already present = tier 2 exhausted) stays in `LifeSlaBreachPolicy` — it reads `ctx.task().candidateGroups()` which is casehub-work territory. `LifeSlaPolicy` carries only the domain-specific inputs (`escalationGroup`, `escalationDeadline`).

---

## Design 5: Case Hub Descriptors (`LifeCaseType`)

### Architecture

The current 8 `*CaseHub` beans each: (a) pass a YAML path to `super()`, and (b) augment the loaded `CaseDefinition` with worker lambdas. The `*CaseDefinitions` FuncDSL companions build the full structure in Java as an alternative representation.

After the refactor:
- **YAML files stay** — they are the source of truth for case structure (bindings, goals, plan items, signals). They have external tooling value and are already validated.
- **`*CaseDescriptor` POJOs** — carry worker lambdas + case metadata (`lifeCaseType()`, `domain()`). Workers are business logic; they belong here.
- **`*CaseHub` CDI shells** — become thin, extending `LifeTypedCaseHub` (see below). No worker methods. `*CaseDefinitions` FuncDSL classes are superseded; they can remain as test utilities if needed.
- **`FamilyVoteCaseHub` and `CareEpisodeCaseHub` are out of scope** — both are spawned as sub-cases only, not registered with `LifeCaseType`, and have no `LifeCaseService` dispatch. `FamilyVoteCaseHub` has no workers (YAML-only). `CareEpisodeCaseHub` has workers — these move to a `CareEpisodeDescriptor`, but the hub remains a plain `YamlCaseHub` subclass, not a `LifeTypedCaseHub`.

### `LifeCaseDescriptor` interface — `app/engine/`

```java
public interface LifeCaseDescriptor {
    LifeCaseType lifeCaseType();
    LifeDomain domain();
    List<Worker> workers(); // lambdas that run on Quartz worker threads
}
```

Lives in `app/engine/` (not `api/`) because `Worker` is from `casehub-engine-api`.

### `LifeTypedCaseHub` abstract class — `app/engine/`

An abstract class (not an interface) extending `YamlCaseHub`. This is the correct type for the constraint — Java interfaces cannot extend abstract classes, so the type safety (all `LifeTypedCaseHub` beans are also `CaseHub`) requires an abstract class. Provides double-checked locking for the augmented definition (matching the existing pattern in `AppointmentCycleCaseHub`):

```java
public abstract class LifeTypedCaseHub extends YamlCaseHub {

    private volatile CaseDefinition augmentedDefinition;

    protected LifeTypedCaseHub(String yamlPath) { super(yamlPath); }

    public abstract LifeCaseType lifeCaseType();
    protected abstract LifeCaseDescriptor descriptor();

    @Override
    public final CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    CaseDefinition base = super.getDefinition();
                    base.getWorkers().addAll(descriptor().workers());
                    augmentedDefinition = base;
                }
            }
        }
        return augmentedDefinition;
    }
}
```

`getDefinition()` is `final` — subclasses must not override it. Caching is provided once, correctly.

The inheritance is intentional: `YamlCaseHub.super.getDefinition()` IS used — it loads and caches the YAML. The abstract class augments it with workers from the descriptor. Extending `YamlCaseHub` is not misleading.

### Thin CDI shell example

```java
@ApplicationScoped
public class AppointmentCycleCaseHub extends LifeTypedCaseHub {

    public AppointmentCycleCaseHub() { super("life/appointment-cycle.yaml"); }

    @Override public LifeCaseType lifeCaseType() { return LifeCaseType.APPOINTMENT_CYCLE; }
    @Override protected LifeCaseDescriptor descriptor() { return new AppointmentCycleDescriptor(); }
}
```

If a descriptor needs a CDI dep (e.g. a service the workers call), the CDI shell injects it and passes via constructor:

```java
@ApplicationScoped
public class ContractorCoordinationCaseHub extends LifeTypedCaseHub {
    @Inject SomeService service;

    public ContractorCoordinationCaseHub() { super("life/contractor-coordination.yaml"); }

    @Override public LifeCaseType lifeCaseType() { return LifeCaseType.CONTRACTOR_COORDINATION; }
    @Override protected LifeCaseDescriptor descriptor() { return new ContractorCoordinationDescriptor(service); }
}
```

`descriptor()` is called inside the double-checked lock. All `@Inject` fields on an `@ApplicationScoped` bean are resolved before any method dispatch arrives via the CDI proxy — no null risk.

### `LifeCaseService.resolve()` — type-safe CDI discovery, no switch

`Instance<LifeTypedCaseHub>` returns only the 6 beans that extend `LifeTypedCaseHub`. Each is also a `CaseHub` (through the class hierarchy: `LifeTypedCaseHub → YamlCaseHub → CaseHub`). No cast required:

```java
@Inject Instance<LifeTypedCaseHub> caseHubs;

private CaseHub resolve(LifeCaseType type) {
    return caseHubs.stream()
        .filter(h -> h.lifeCaseType() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No LifeTypedCaseHub for type: " + type));
}
```

The six directly-injected `@Inject AppointmentCycleCaseHub` fields in `LifeCaseService` are removed.

---

## Module layout after

```
api/
  LifeDomain               ← add descriptor() + fromCategory()
  LifeDomainDescriptor     ← new interface (no engine-api dep)
  LifeRoutingPolicy        ← moved from app/routing/ (package change only, unchanged record)
  LifeSlaPolicy            ← new record (pure Java)
  descriptor/
    HealthDomainDescriptor
    LegalDomainDescriptor
    FinanceDomainDescriptor
    HouseholdDomainDescriptor
    FamilySchedulingDomainDescriptor
    TravelDomainDescriptor
    ContractorCoordinationDomainDescriptor
    ElderCareDomainDescriptor
  HouseholdActionType      ← remove ThresholdCategory + ThresholdCategory enum

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
    DomainLedgerHandler        ← new interface
    HealthDomainLedgerHandler  ← new CDI handler
    LegalDomainLedgerHandler   ← new CDI handler
    FinanceDomainLedgerHandler ← new CDI handler (no-op on CREATED)
    LifeLedgerWriter           ← shrinks: erasure + populateBase only
    LifeOutcomeAttestationWriter ← simplified: no DOMAIN_TO_CAPABILITY map
  engine/
    LifeCaseDescriptor         ← new interface (in app/ — references Worker from engine-api)
    LifeTypedCaseHub           ← new abstract class (extends YamlCaseHub, caching + template)
    AppointmentCycleDescriptor ← new POJO
    HomeMaintenanceDescriptor
    TravelPlanDescriptor
    CareCoordinationDescriptor
    ContractorCoordinationDescriptor (engine)
    FinancialReviewDescriptor
    CareEpisodeDescriptor      ← new POJO (workers only; hub stays plain YamlCaseHub subclass)
    AppointmentCycleCaseHub    ← shrinks to CDI shell extending LifeTypedCaseHub
    HomeMaintenanceCaseHub
    TravelPlanCaseHub
    CareCoordinationCaseHub
    ContractorCoordinationCaseHub
    FinancialReviewCaseHub
    FamilyVoteCaseHub          ← unchanged (sub-case hub, YAML-only, no LifeCaseType)
    CareEpisodeCaseHub         ← shrinks to CDI shell extending YamlCaseHub (not LifeTypedCaseHub)
  commitment/
    LifeCommitmentStrategy    ← add commitmentMode() + escalationTitle()
  spi/
    LifeSlaBreachPolicy       ← thin dispatcher using domain.descriptor().slaPolicy()
```

---

## Testing

### Domain descriptor tests (pure JUnit, `api/`)
- One test class per descriptor: `capability()`, `templateCategory()`, `routingPolicy()` values (verify `OptionalDouble.of(N)` present or `.empty()` for HOUSEHOLD/FAMILY_SCHEDULING), `workerCapabilities()` set, `slaPolicy()` escalation group and deadline
- `LifeDomain.fromCategory()` roundtrip for all 8 descriptors + null → HOUSEHOLD default

### Risk rule tests (pure JUnit, `app/routing/rules/`)
- One test class per rule: ALWAYS/NEVER/AMOUNT_THRESHOLD decisions with mock `Preferences`
- `SpendPurchaseRule`: at threshold → GateRequired, below → Autonomous, missing amount → Autonomous, unparseable → Autonomous
- Each rule: verify `actionType()`, `reversible`, `candidateGroups`, scope, reason string structure

### Handler tests (Mockito, `app/service/ledger/`)
- Mock `LedgerEntryRepository` and `LifeOutcomeAttestationWriter`
- HEALTH/LEGAL: verify `HealthDecisionLedgerEntry` / `LegalActionLedgerEntry` fields on CREATED, SLA_BREACH, COMPLETED; verify attestation called on SLA_BREACH and COMPLETED, not CREATED
- FINANCE: verify CREATED is a no-op; SLA_BREACH and COMPLETED write `FinancialDecisionLedgerEntry` from `LifeCommitmentRecord`

### Case descriptor tests (pure JUnit, `app/engine/`)
- `new AppointmentCycleDescriptor().lifeCaseType()` == APPOINTMENT_CYCLE
- `new AppointmentCycleDescriptor().workers()` non-empty, correct worker names

### Dispatcher tests (Mockito)
- `LifeActionRiskClassifier`: startup `IllegalStateException` on missing rule; correct rule called per action type; unknown type → Autonomous
- `LifeDecisionLedgerObserver`: correct handler called for HEALTH/LEGAL/FINANCE; domains without handler (HOUSEHOLD) produce no invocation
- `LifeTaskService`: CREATED calls handler for HEALTH/LEGAL; HOUSEHOLD produces no handler call

### Updated integration tests (`@QuarkusTest`)
- `LifeActionRiskClassifierQuarkusTest` — CDI wiring still satisfied; `Instance<ActionRiskClassifier>` non-empty
- `LifeTrustRoutingPolicyProviderTest` — routing policy values unchanged, YAML overlay still works, O(1) index built correctly
- `LifeSlaBreachPolicyTest` (new) — HEALTH domain 24h deadline, HOUSEHOLD domain 48h deadline, tier-2 exhaustion returns Fail

---

## Deferred / follow-up

| Issue | Description |
|---|---|
| life#30 | Second-pass audit — verify nothing was missed in this sweep |
| parent#202 | Universal descriptor+handler coherence protocol for all casehubio application repos |
| life#26 | RBAC-differentiated risk thresholds (blocked on auth retrofit) |

---

## Platform coherence

- All `api/` descriptor POJOs are pure Java — `LifeRoutingPolicy` (moved), `LifeSlaPolicy` (new), `LifeDomainDescriptor` (new) carry no framework deps ✅
- `LifeDomainDescriptor` and `LifeRoutingPolicy` live in `api/`; `LifeCaseDescriptor` lives in `app/` (references `Worker` from engine-api) — correct per module-tier-structure ✅
- `ThresholdCategory` removal from `HouseholdActionType` is a breaking API change — no external consumers, acceptable ✅
- `DomainLedgerHandler` in `app/` — correct: implementations reference JPA entities ✅
- `LifeTypedCaseHub` abstract class preserves `YamlCaseHub` YAML-load caching and adds augmentation caching — no double initialization ✅
- `FamilyVoteCaseHub` and `CareEpisodeCaseHub` are sub-case hubs; `FamilyVoteCaseHub` needs no descriptor (YAML-only); `CareEpisodeCaseHub` gets a `CareEpisodeDescriptor` for its workers but remains a plain `YamlCaseHub` extension — not a `LifeTypedCaseHub` ✅
- `LifeTrustRoutingPolicyProvider` O(1) lookup preserved via `@PostConstruct` index; YAML scope keys unchanged (same `LifeCapabilities` string values) ✅
- `LifeCommitmentStrategy.commitmentMode()` enables mode-safe lookup without reconstituting `CommitmentContext`; `escalationTitle()` is abstract — all three implementations provide it ✅
