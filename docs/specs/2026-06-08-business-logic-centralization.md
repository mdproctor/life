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

Pure Java, no framework imports, zero injected dependencies. Carries all **declarative knowledge** about a domain or type — everything you need to know about it without running any code. Lives in `api/` (domain descriptors) or `app/` (action type rules). Testable with plain `new` and plain JUnit.

### Layer 2 — Handler (CDI supplement, optional)

`@ApplicationScoped` CDI bean. Adds **execution behaviour** using infrastructure (repositories, preference providers). Discovered via `Instance<HandlerType>` at the service layer. Optional: if a domain has no handler for a given concern, the service falls back gracefully. Testable with Mockito.

The dispatcher pattern (`Instance<T>` filtered by type identity) replaces every switch statement. No registration step. No dispatcher modification when adding a new type.

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
    String capability();              // maps to LifeCapabilities
    String templateCategory();        // "health", "contractor", etc. — reverse of domainFromCategory()
    LifeRoutingPolicy routingPolicy();// trust threshold, minObservations, margin, fallback
    Set<String> workerCapabilities(); // fine-grained worker names routing to this domain
    boolean producesLedgerEntry();    // does this domain write audit records?
    LifeSlaPolicy slaPolicy();        // escalation group + deadline; LifeSlaBreachPolicy builds BreachDecision
}
```

`LifeRoutingPolicy` moves from `app/routing/` to `api/` — it is domain vocabulary, not app implementation.

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
        return Arrays.stream(values())
            .filter(d -> d.descriptor().templateCategory().equals(category))
            .findFirst();
    }
}
```

### Eight descriptor POJOs — `api/descriptor/`

One per domain. Example:

```java
public final class HealthDomainDescriptor implements LifeDomainDescriptor {
    public String capability()         { return LifeCapabilities.HEALTH_COORDINATION; }
    public String templateCategory()   { return "health"; }
    public boolean producesLedgerEntry() { return true; }
    public Set<String> workerCapabilities() {
        return Set.of("book-appointment", "find-alternative", "confirm-appointment",
                      "pre-visit-prep", "record-health-decision");
    }
    public LifeRoutingPolicy routingPolicy() {
        return new LifeRoutingPolicy(0.75, 10, 0.05,
            Optional.of("household-admin"),
            "High reliability required for health appointments and follow-ups");
    }
    public LifeSlaPolicy slaPolicy() {
        return new LifeSlaPolicy("household-admin", Duration.ofHours(24));
    }
}
```

HOUSEHOLD and FAMILY_SCHEDULING: `producesLedgerEntry() = false`. FINANCE: `producesLedgerEntry() = true` but ledger entry is written via `LifeCommitmentRecord`, not `LifeTaskContext` — handled internally by `FinanceDomainLedgerHandler`.

### What this eliminates

| Was | Becomes |
|---|---|
| `LifeOutcomeAttestationWriter.DOMAIN_TO_CAPABILITY` static map | `domain.descriptor().capability()` |
| `LifeTrustRoutingPolicyProvider.CAPABILITY_TO_DOMAIN` (32 entries) | derived: `stream(LifeDomain.values()).filter(d -> d.descriptor().workerCapabilities().contains(cap))` |
| `LifeTrustRoutingPolicyProvider.POLICIES` (8 entries) | `domain.descriptor().routingPolicy()` |
| `LifeTaskService.domainFromCategory()` switch | `LifeDomain.fromCategory(category)` |
| `LifeSlaBreachPolicy` universal 48h/household-admin | `domain.descriptor().slaBreachPolicy(ctx)` |

---

## Design: Domain Ledger Handlers

### `DomainLedgerHandler` interface — `app/service/ledger/`

```java
public interface DomainLedgerHandler {
    LifeDomain domain();
    void writeEntry(LifeDecisionEventType event, UUID workItemId, WorkItem workItem);
}
```

Covers all three event types: `CREATED`, `SLA_BREACH`, `COMPLETED`. Fetches its own context internally (LifeTaskContext for HEALTH/LEGAL; LifeCommitmentRecord for FINANCE).

### Three CDI implementations

- `HealthDomainLedgerHandler` — `LifeTaskContext` + `HealthDecisionLedgerEntry` + attestation
- `LegalDomainLedgerHandler` — `LifeTaskContext` + `LegalActionLedgerEntry` + attestation
- `FinanceDomainLedgerHandler` — `LifeCommitmentRecord` + `FinancialDecisionLedgerEntry`. Returns early on `CREATED` event — finance entries are only written when a commitment record exists (written by `LifeWatchdogAlertObserver` on SLA_BREACH, and by the observer on COMPLETED).

### Services after refactor

**`LifeDecisionLedgerObserver`** — no switch; delegates to handler:
```java
handlers.stream().filter(h -> h.domain() == domain).findFirst()
    .ifPresent(h -> h.writeEntry(event, workItemId, workItem));
```

**`LifeTaskService`** — `domainFromCategory()` switch removed (uses `LifeDomain.fromCategory()`); CREATED write removed (handled by `DomainLedgerHandler`).

**`LifeLedgerWriter`** — shrinks to `writeErasureEntry()` (GDPR) + `populateBase()` helper for handlers.

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
    HouseholdActionType actionType();
    RiskDecision evaluate(PlannedAction action, Preferences prefs);
}
```

### Eleven POJO rule implementations — `app/routing/rules/`

One per `HouseholdActionType` constant. Pure functions: `(PlannedAction, Preferences) → RiskDecision`. No injected deps. The dispatcher resolves `Preferences` once and passes it in.

`ThresholdCategory` removed from `HouseholdActionType` — it was always a classifier implementation detail. Each `AMOUNT_THRESHOLD` rule references its preference key directly.

Example:
```java
public final class SpendPurchaseRule implements HouseholdRiskRule {
    public HouseholdActionType actionType() { return SPEND_PURCHASE; }
    public RiskDecision evaluate(PlannedAction action, Preferences prefs) {
        double amount = parseAmount(action.context());
        if (amount < prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD).value())
            return new RiskDecision.Autonomous();
        return gate(SPEND_PURCHASE, action, prefs,
            "Spend of " + formatAmount(action.context()) + " requires household approval");
    }
}
```

### `LifeActionRiskClassifier` — thin dispatcher

Builds `Map<HouseholdActionType, HouseholdRiskRule>` at startup via `Instance<HouseholdRiskRule>`. **Startup validation:** throws `IllegalStateException` if any `HouseholdActionType` value has no registered rule — makes missing rules immediately visible rather than silently returning `Autonomous`.

---

## Design: Commitment Mode Escalation

### `LifeCommitmentStrategy` gains `escalationTitle()`

```java
public interface LifeCommitmentStrategy {
    boolean applies(CommitmentContext context);
    CommitmentOutcome execute(CommitmentContext context);
    String escalationTitle(LifeCommitmentRecord record);  // new — abstract, all 3 impls provide it
}
```

Each of the three existing strategies (`DelegationCommitmentStrategy`, `ContractorCommitmentStrategy`, `OversightGateStrategy`) implements `escalationTitle()` directly — each already knows its own message. `LifeWatchdogAlertObserver` looks up the matching strategy by commitment mode and calls `escalationTitle(record)`. The switch is removed.

The observer needs the strategy lookup: `strategies.stream().filter(s -> s.appliesForMode(record.mode)).findFirst()`. This requires a small addition to the interface: `CommitmentMode mode()` (or a separate mode-based lookup method) so the observer can identify the right strategy without re-evaluating `applies()` on a reconstituted context.

---

## Design: Case Hub Descriptors (`LifeCaseType`)

### Pattern

Replace each `*CaseHub` + `*CaseDefinitions` pair (16 classes for 8 definitions) with:
- **Descriptor POJO** — carries `caseName()`, `lifeDomain()`, `lifeCaseType()`, and the full `CaseDefinition` including FuncDSL worker lambdas. Dependencies needed by lambdas are passed via constructor.
- **Thin CDI shell** — extends `YamlCaseHub`, implements `LifeTypedCaseHub` (marker interface with `lifeCaseType()`). Injects CDI deps and passes them to the descriptor.

```java
// Pure POJO — all business logic for appointment cycle
public final class AppointmentCycleDescriptor {
    public LifeCaseType lifeCaseType()  { return LifeCaseType.APPOINTMENT_CYCLE; }
    public LifeDomain domain()          { return LifeDomain.HEALTH; }

    public CaseDefinition definition() {
        return CaseDefinition.of("appointment-cycle")
            .worker("book-appointment",       this::bookAppointment)
            .worker("confirm-appointment",    this::confirmAppointment)
            .worker("record-health-decision", this::recordDecision)
            .build();
    }
    private WorkerResult bookAppointment(WorkerContext ctx) { ... }
    // ...
}

// Thin CDI shell
@ApplicationScoped
public class AppointmentCycleCaseHub extends YamlCaseHub implements LifeTypedCaseHub {
    @Override public LifeCaseType lifeCaseType() { return LifeCaseType.APPOINTMENT_CYCLE; }
    @Override public CaseDefinition getDefinition() {
        return new AppointmentCycleDescriptor().definition();
    }
}
```

### `LifeTypedCaseHub` marker interface — `app/engine/`

```java
public interface LifeTypedCaseHub {
    LifeCaseType lifeCaseType();
}
```

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

---

## Module layout after

```
api/
  LifeDomain               ← add descriptor() + fromCategory()
  LifeDomainDescriptor     ← new interface
  LifeRoutingPolicy        ← moved from app/routing/
  descriptor/
    HealthDomainDescriptor     ← new POJO
    LegalDomainDescriptor      ← new POJO
    FinanceDomainDescriptor    ← new POJO
    HouseholdDomainDescriptor  ← new POJO
    FamilySchedulingDomainDescriptor ← new POJO
    TravelDomainDescriptor     ← new POJO
    ContractorCoordinationDomainDescriptor ← new POJO
    ElderCareDomainDescriptor  ← new POJO
  HouseholdActionType      ← remove ThresholdCategory + ThresholdCategory enum

app/
  routing/
    HouseholdRiskRule        ← new interface
    LifeActionRiskClassifier ← thin dispatcher with startup validation
    rules/
      SpendPurchaseRule         ← new POJO
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
    DomainLedgerHandler      ← new interface
    HealthDomainLedgerHandler ← new CDI handler
    LegalDomainLedgerHandler  ← new CDI handler
    FinanceDomainLedgerHandler ← new CDI handler
    LifeLedgerWriter          ← shrinks: erasure + populateBase only
    LifeOutcomeAttestationWriter ← simplified: no DOMAIN_TO_CAPABILITY map
  engine/
    LifeTypedCaseHub         ← new marker interface
    AppointmentCycleDescriptor ← new POJO (replaces AppointmentCycle*CaseHub + *CaseDefinitions)
    HomeMaintenanceDescriptor
    TravelPlanDescriptor
    CareCoordinationDescriptor
    CareEpisodeDescriptor
    ContractorCoordinationDescriptor
    FinancialReviewDescriptor
    FamilyVoteDescriptor
    (8 thin CDI shells remain — extend YamlCaseHub + implement LifeTypedCaseHub)
  commitment/
    LifeCommitmentStrategy    ← add escalationTitle()
  spi/
    LifeSlaBreachPolicy       ← thin dispatcher using domain.descriptor().slaBreachPolicy()
```

---

## Testing

### Descriptor tests (pure JUnit, `api/`)
- One test class per domain descriptor: verify `capability()`, `templateCategory()`, `routingPolicy()` values, `workerCapabilities()` set, `producesLedgerEntry()` flag, SLA breach decision shapes
- `LifeDomain.fromCategory()` roundtrip test for all 8 descriptors

### Risk rule tests (pure JUnit, `app/routing/rules/`)
- One test class per rule: verify ALWAYS/NEVER/AMOUNT_THRESHOLD decisions with mock `Preferences`
- Boundary: at/below/above threshold, missing amount, unparseable amount

### Handler tests (Mockito, `app/service/ledger/`)
- Mock `LedgerEntryRepository` + dependencies
- Verify correct `LedgerEntry` subclass constructed, correct fields set, attestation triggered (HEALTH/LEGAL only)

### Case descriptor tests (pure JUnit, `app/engine/`)
- Verify `definition()` builds without throwing, correct worker names present, correct case name

### Dispatcher tests (Mockito)
- `LifeActionRiskClassifier`: startup validation throws on missing rule; correct rule called per action type; unknown type → Autonomous
- `LifeDecisionLedgerObserver`: correct handler called per domain; domains without handler produce no entry
- `LifeCaseService`: correct hub resolved per case type; missing hub throws

### Updated integration tests
- `LifeActionRiskClassifierQuarkusTest` — CDI wiring still satisfied
- `LifeTrustRoutingPolicyProviderTest` — routing policy values unchanged, YAML overlay still works
- `LifeSlaBreachPolicyTest` (new) — per-domain escalation deadlines verified via @QuarkusTest

---

## Deferred / follow-up

| Issue | Description |
|---|---|
| life#30 | Second-pass audit — verify nothing was missed in this sweep |
| parent#202 | Universal descriptor+handler coherence protocol for all casehubio application repos |
| life#26 | RBAC-differentiated risk thresholds (blocked on auth retrofit) |

---

## Platform coherence

- All descriptor POJOs are in `api/` (pure Java) or `app/` — no framework leakage ✅
- `LifeRoutingPolicy` move from `app/` to `api/` is a package rename only — zero logic change ✅
- `ThresholdCategory` removal from `HouseholdActionType` is a breaking API change — no external consumers, acceptable ✅
- `DomainLedgerHandler` interface in `app/` (not `api/`) — correct: references JPA entities ✅
- CDI shell pattern for case hubs follows existing `YamlCaseHub` extension contract ✅
- `LifeCommitmentStrategy.escalationTitle()` is additive — existing implementations get a default if needed ✅
