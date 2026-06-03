# Layer 6: Trust Routing — Attestation Pipeline + Routing Policies + Ledger-Backed Scores

**Epic:** casehubio/life#7 (Layer 6: Trust routing)
**Task:** casehubio/life#11
**Date:** 2026-06-03
**Layer:** 6 (trust routing)

---

## Two Concerns — Explicitly Separated

This layer delivers two orthogonal trust capabilities that share dependency wiring but serve different purposes:

**Problem A — Agent routing:** Which AI worker handles a case step? `TrustWeightedAgentStrategy` selects among `AgentCandidate` workers based on trust scores keyed by worker capability name. The identity namespace is worker capability names from YAML case definitions (e.g., `book-appointment`, `request-quote`). At Layer 6 with FuncDSL workers, there is exactly one worker per capability — routing decisions are trivial (pick the only candidate). This infrastructure becomes consequential at Layer 7 when OpenClaw provides multiple competing agents per capability.

**Problem B — External actor trustworthiness:** How reliable is this contractor/doctor/institution? `TrustGateService` reads trust scores keyed by `life-actor:{uuid}`. These scores accumulate from the attestation pipeline (new in this layer) and are exposed on `GET /external-actors/{id}`. This delivers immediate value regardless of agent count.

---

## Platform Coherence

Trust scoring computation and storage is owned by casehub-ledger (`ActorTrustScore`, `TrustScoreJob`). casehub-life does NOT store trust scores locally — it reads from the ledger via `TrustGateService`. This follows the boundary rule: "Do not add trust scoring to casehub-work or casehub-engine. Trust lives in casehub-ledger."

Reference implementation: devtown#57 (`DevtownTrustRoutingPolicyProvider`).

---

## Dependencies

Add to `app/pom.xml`:

- **`casehub-engine-ledger`** — activates `TrustWeightedAgentStrategy @Alternative @Priority(1)`, displacing `LeastLoadedAgentStrategy`. Also brings `WorkerDecisionEventCapture` for routing decision audit. Auto-activates via CDI `@Alternative @Priority(1)` in Quarkus 3.x — no `selected-alternatives` entry needed.
- **`casehub-platform-config`** — `ConfigFilePreferenceProvider @ApplicationScoped` reads YAML from classpath by scope convention. Displaces `MockPreferenceProvider @DefaultBean` automatically. File at `casehub/life/trust-routing.yaml` under `src/main/resources/` is discovered via the scope prefix `casehubio/life/trust-routing/*`.

**Flyway config change** — add engine-ledger migrations to qhorus datasource:

```properties
quarkus.flyway."qhorus".locations=classpath:db/qhorus/migration,classpath:db/ledger/migration,classpath:db/life/ledger/migration,classpath:db/engine-ledger/migration
```

Engine-ledger uses V2000–V2005 on the qhorus datasource. No collision with life's V2100+ range.

**Trust scoring enablement:**

```properties
casehub.ledger.trust-score.enabled=true
```

Defaults to `false` — must be explicitly enabled. Without this, `TrustScoreJob` fires but immediately returns.

No new life domain migrations needed — trust scores are stored in the ledger's own `actor_trust_score` table.

---

## ActorId Convention

**Convention:** `life-actor:{uuid}` for ExternalActor entities in ledger entries.

**`LifeActorIds`** utility in `api/` — pure Java domain vocabulary (no framework deps, no JPA):

```java
public final class LifeActorIds {
    public static final String PREFIX = "life-actor:";

    public static String of(UUID externalActorId) {
        return PREFIX + externalActorId;
    }

    public static boolean isLifeActor(String actorId) {
        return actorId != null && actorId.startsWith(PREFIX);
    }

    public static UUID extractId(String actorId) {
        return UUID.fromString(actorId.substring(PREFIX.length()));
    }
}
```

---

## Ledger Writer Fix

`LifeLedgerWriter` currently hardcodes `"life-system"` as `actorId` on all entries, preventing trust accumulation against individual ExternalActors.

**Fix:** When a ledger entry records an ExternalActor's behaviour, use `LifeActorIds.of(externalActorId)` with `ActorType.HUMAN`.

| Method | Change |
|---|---|
| `writeHealthEntry` | If `ctx.externalActorId != null`, use `LifeActorIds.of(ctx.externalActorId)` + `ActorType.HUMAN`; otherwise keep `"life-system"` + `ActorType.SYSTEM` |
| `writeLegalEntry` | Same contextual actorId pattern as `writeHealthEntry` — legal tasks can involve external actors (solicitors, notaries) |
| `writeFinancialEntry` | No change — system action (oversight gate) |
| `writeErasureEntry` | No change — already uses `erasedBy` parameter |

`LifeDecisionLedgerObserver` already has access to `LifeTaskContext.externalActorId` — no new queries needed.

---

## Attestation Pipeline

**This is the critical piece that makes trust scores accumulate.** Without attestations, `TrustScoreComputer` produces Beta(1,1) = 0.5 for all actors permanently. The javadoc confirms: "Unattested decisions contribute nothing — they do not inflate the score."

### LifeOutcomeAttestationWriter

New `@ApplicationScoped` service in `app/`. Called by `LifeLedgerWriter` after persisting a ledger entry, in the same transaction. Creates a `LedgerAttestation` on the just-persisted entry.

**LedgerAttestation fields:**

| Field | Source |
|---|---|
| `ledgerEntryId` | The just-persisted `LedgerEntry.id` |
| `subjectId` | Same as the entry's `subjectId` |
| `attestorId` | `"life-system"` (system-generated assessment) |
| `attestorType` | `ActorType.SYSTEM` |
| `attestorRole` | `"OutcomeAssessor"` |
| `verdict` | Derived from event type (see mapping below) |
| `confidence` | 0.9 for objective signals (SLA met/missed), 0.7 for inferred signals |
| `capabilityTag` | Derived from WorkItem scope path (see below) |
| `trustDimension` | Dimension name when a dimension score is computable; null otherwise |
| `dimensionScore` | Continuous [0.0, 1.0] quality score when computable; null otherwise |

### Verdict Mapping

| Event | Verdict | Confidence | Rationale |
|---|---|---|---|
| WorkItem COMPLETED, positive outcome | SOUND | 0.9 | Task completed as expected |
| SLA_BREACH | FLAGGED | 0.9 | Deadline missed — objective, binary signal |
| Commitment FULFILLED | SOUND | 0.9 | Obligation met |
| Commitment FAILED | FLAGGED | 0.9 | Obligation breached |

### Capability Tag Derivation

The attestation's `capabilityTag` drives CAPABILITY-scoped trust scores. Derived from the WorkItem's scope path:

- WorkItem scope: `casehubio/life/{domain}` (Layer 5 retrofit)
- Extract domain segment → map to `LifeCapabilities` constant
- Example: scope `casehubio/life/health` → `capabilityTag = "health-coordination"`

If scope is missing or domain segment doesn't map, use `CapabilityTag.GLOBAL` (`"*"`).

### Dimension Scores

Two dimensions have automatic signals from WorkItem/commitment data:

| Dimension | Signal | Score computation |
|---|---|---|
| `deadline-reliability` | SLA deadline vs actual completion | `clamp(1.0 - (daysLate / gracePeriod), 0.0, 1.0)`. On-time = 1.0. Late scales linearly to 0.0 over a configurable grace period (default 7 days). |
| `cost-accuracy` | Quoted amount vs actual (from `LifeCommitmentRecord.amountThreshold`) | `clamp(1.0 - abs(actual - quoted) / quoted, 0.0, 1.0)`. Exact match = 1.0. |

Two dimensions have NO automatic signal at Layer 6:

| Dimension | Why deferred |
|---|---|
| `factual-accuracy` | Requires human or AI assessment of information quality — Layer 7 (OpenClaw agents can assess) |
| `proactive-alerting` | Requires human assessment of whether risks were surfaced early — Layer 7 |

These are created as separate attestations (one per dimension) alongside the verdict attestation, only when the relevant data is available.

### LifeLedgerWriter Changes

`LifeLedgerWriter` methods gain a return type — they return the persisted `LedgerEntry` so the caller can write attestations:

```java
public LedgerEntry writeHealthEntry(...) {
    // ... existing code ...
    ledgerRepository.save(entry);
    attestationWriter.attestOutcome(entry, eventType, ctx, workItem);
    return entry;
}
```

`LifeOutcomeAttestationWriter.attestOutcome()` creates:
1. A verdict attestation (SOUND/FLAGGED) with `capabilityTag`
2. Optionally a `deadline-reliability` dimension attestation if SLA data exists
3. Optionally a `cost-accuracy` dimension attestation if cost data exists

All in the same transaction as the ledger entry.

---

## TrustRoutingPolicyProvider (Problem A)

### Capability Name → Domain Policy Mapping

Workers in YAML case definitions use fine-grained capability names (`book-appointment`, `flight-search`, etc.). Routing policies are defined at the domain level (`health-coordination`, `travel-planning`). `LifeTrustRoutingPolicyProvider.forCapability()` resolves fine-grained names to domain policies via a static mapping.

The mapping is many-to-one — all workers in `appointment-cycle` share the `health-coordination` policy:

```java
private static final Map<String, String> CAPABILITY_TO_DOMAIN = Map.ofEntries(
    // appointment-cycle workers → health-coordination
    entry("book-appointment", HEALTH_COORDINATION),
    entry("find-alternative", HEALTH_COORDINATION),
    entry("confirm-appointment", HEALTH_COORDINATION),
    entry("pre-visit-prep", HEALTH_COORDINATION),
    entry("record-health-decision", HEALTH_COORDINATION),
    // contractor-coordination workers → contractor-coordination
    entry("request-quote", CONTRACTOR_COORDINATION),
    entry("watchdog-escalation", CONTRACTOR_COORDINATION),
    entry("quote-received", CONTRACTOR_COORDINATION),
    entry("job-monitoring", CONTRACTOR_COORDINATION),
    entry("record-payment", CONTRACTOR_COORDINATION),
    // ... etc for all 8 case definitions
);
```

When `forCapability()` receives an unmapped capability name, returns `TrustRoutingPolicy.DEFAULT`.

### Domain Routing Policies

**`LifeRoutingPolicy`** record in `app/` — pure Java (no JPA references; placed in `app/` because it lives with the implementation and there's no separate domain module):

```java
public record LifeRoutingPolicy(
    OptionalDouble threshold,
    OptionalInt minimumObservations,
    OptionalDouble borderlineMargin,
    Optional<String> fallbackType,
    String rationale
) {}
```

| Capability | Threshold | Min Obs | Borderline | Fallback | Rationale |
|---|---|---|---|---|---|
| `health-coordination` | 0.75 | 10 | 0.05 | `household-admin` | Missed follow-ups have real consequences |
| `legal-deadline` | 0.80 | 12 | 0.05 | `household-admin` | Hard deadlines; highest trust requirement |
| `financial-planning` | 0.70 | 10 | 0.10 | `household-admin` | Oversight gate exists; wider borderline band |
| `contractor-coordination` | 0.65 | 8 | 0.05 | `household-admin` | Watchdog catches failures |
| `elder-care` | 0.75 | 10 | 0.05 | `household-admin` | Vulnerable people |
| `household-management` | 0.50 | 5 | — | — | Low-stakes routine |
| `family-scheduling` | 0.50 | 5 | — | — | Low-stakes scheduling |
| `travel-planning` | 0.55 | 6 | 0.05 | `household-admin` | Booking mistakes cost money |

### Assembly: LifeRoutingPolicy + YAML → TrustRoutingPolicy

`LifeTrustRoutingPolicyProvider.forCapability()` assembles the engine SPI type from two sources:

```java
@Override
public TrustRoutingPolicy forCapability(String capabilityName) {
    String domainKey = CAPABILITY_TO_DOMAIN.getOrDefault(capabilityName, capabilityName);
    LifeRoutingPolicy policy = POLICIES.get(domainKey);
    if (policy == null) {
        return TrustRoutingPolicy.DEFAULT;
    }

    Preferences prefs = preferenceProvider.resolve(
        SettingsScope.of("casehubio", "life", "trust-routing", domainKey));

    double threshold = policy.threshold()
        .orElse(TrustRoutingPolicy.DEFAULT.threshold());
    int minObs = policy.minimumObservations()
        .orElse(TrustRoutingPolicy.DEFAULT.minimumObservations());
    double margin = policy.borderlineMargin()
        .orElse(TrustRoutingPolicy.DEFAULT.borderlineMargin());

    DoublePreference blendPref = prefs.get(LifeTrustRoutingPolicyKeys.BLEND_FACTOR);
    double blendFactor = blendPref != null
        ? blendPref.value()
        : TrustRoutingPolicy.DEFAULT.blendFactor();

    Map<String, Double> qualityFloors = new HashMap<>();
    LifeTrustRoutingPolicyKeys.allFloorKeys().forEach((dim, key) -> {
        DoublePreference v = prefs.get(key);
        if (v != null && v.value() > 0.0) {
            qualityFloors.put(dim, v.value());
        }
    });

    return new TrustRoutingPolicy(threshold, minObs, margin, blendFactor,
        Map.copyOf(qualityFloors));
}
```

**`fallbackType` and `rationale`** live on `LifeRoutingPolicy` only — they are domain concepts not present on the engine SPI type. `fallbackType` is used by the engine's `AgentRoutingEscalationHandler` indirectly (see EscalateToOversight section below).

### EscalateToOversight Handling

When `TrustWeightedAgentStrategy` determines all non-bootstrap candidates are borderline, it returns `AgentAssignment.EscalateToOversight(capabilityName)`. The engine handles this automatically — no casehub-life code needed:

1. `WorkOrchestrator` receives `EscalateToOversight` → publishes `AgentRoutingEscalationEvent` to the event bus
2. `AgentRoutingEscalationHandler` consumes the event → posts a QUERY to the case's oversight channel
3. casehub-life's `life/oversight` channel (from Layer 3) receives the QUERY

The `fallbackType` field on `LifeRoutingPolicy` (`"household-admin"`) documents the intended escalation target but is not wired programmatically at this layer. The engine posts to the case's oversight channel generically. Routing the QUERY to a specific human role requires auth integration (Layer 7+).

### YAML Config

`app/src/main/resources/casehub/life/trust-routing.yaml` — runtime-tunable knobs (blend factor, quality floors):

```yaml
entries:
  - scope: casehubio/life/trust-routing/health-coordination
    casehubio.life.trust-routing.blend-factor: "0.70"
    casehubio.life.trust-routing.floor.factual-accuracy: "0.60"

  - scope: casehubio/life/trust-routing/legal-deadline
    casehubio.life.trust-routing.blend-factor: "0.80"
    casehubio.life.trust-routing.floor.deadline-reliability: "0.70"

  - scope: casehubio/life/trust-routing/contractor-coordination
    casehubio.life.trust-routing.blend-factor: "0.60"
    casehubio.life.trust-routing.floor.deadline-reliability: "0.50"
    casehubio.life.trust-routing.floor.cost-accuracy: "0.50"

  - scope: casehubio/life/trust-routing/financial-planning
    casehubio.life.trust-routing.blend-factor: "0.65"
    casehubio.life.trust-routing.floor.cost-accuracy: "0.60"

  - scope: casehubio/life/trust-routing/elder-care
    casehubio.life.trust-routing.blend-factor: "0.70"

  - scope: casehubio/life/trust-routing/travel-planning
    casehubio.life.trust-routing.blend-factor: "0.50"

  - scope: casehubio/life/trust-routing/household-management
    casehubio.life.trust-routing.blend-factor: "0.40"

  - scope: casehubio/life/trust-routing/family-scheduling
    casehubio.life.trust-routing.blend-factor: "0.40"
```

### PreferenceKey Constants

**`LifeTrustRoutingPolicyKeys`** in `app/` — one `BLEND_FACTOR` key + one floor key per trust dimension (4 dimensions):

- `FLOOR_DEADLINE_RELIABILITY`
- `FLOOR_COST_ACCURACY`
- `FLOOR_FACTUAL_ACCURACY`
- `FLOOR_PROACTIVE_ALERTING`

Qualified name prefix: `casehubio.life.trust-routing`. `allFloorKeys()` returns `Map<String, PreferenceKey<DoublePreference>>`.

---

## ExternalActor Trust Score Enrichment (Problem B)

`GET /external-actors/{id}` response enriched with trust data read from the ledger at query time.

**`ExternalActorResponse`** gains a `TrustProfile` field:

```java
public record ExternalActorResponse(
    UUID id,
    String name,
    LifeActorType actorType,
    String contactMethod,
    String contactValue,
    Instant createdAt,
    Instant gdprErasedAt,
    TrustProfile trustProfile
) {
    public record TrustProfile(
        Double globalScore,
        Map<String, Double> dimensionScores,
        Map<String, Double> capabilityScores
    ) {
        public static final TrustProfile EMPTY =
            new TrustProfile(null, Map.of(), Map.of());
    }
}
```

**Assembly in `ExternalActorService`:**

```java
String actorId = LifeActorIds.of(entity.id);
Double global = trustGateService.currentScore(actorId).orElse(null);
Map<String, Double> dimensions = trustGateService.dimensionScores(actorId);
Map<String, Double> capabilities = trustGateService.allCapabilityScores(actorId);
var profile = new TrustProfile(global, dimensions, capabilities);
```

This is 3 queries per actor. For the list endpoint (`GET /external-actors`), that's 3N queries for N actors. Acceptable for a personal life harness (tens of actors). If it becomes a problem, `TrustExportService.exportAll()` provides a bulk read.

**GDPR-erased actors:** return `TrustProfile.EMPTY`. The erasure state is already distinguishable via `gdprErasedAt != null` on the parent response — no additional flag needed on `TrustProfile`.

**No-data actors** (new ExternalActor, no trust history): return `TrustProfile(null, {}, {})` which is structurally identical to `EMPTY`. The consumer distinguishes by checking `gdprErasedAt` — null means no data yet, non-null means erased.

---

## Known Limitations

**Single-candidate routing (Problem A):** At Layer 6, each capability has exactly one FuncDSL worker. `TrustWeightedAgentStrategy` always selects the only candidate — the routing decision is trivial. Trust routing becomes consequential at Layer 7 when OpenClaw provides multiple competing agents per capability. The infrastructure is exercised correctly; the decision is just uninteresting.

**Deferred dimension signals:** `factual-accuracy` and `proactive-alerting` dimensions require human or AI assessment — no automatic signal at Layer 6. These dimensions will populate at Layer 7 when OpenClaw agents can assess information quality.

**End-to-end agent routing untestable:** Trust-weighted routing cannot be end-to-end tested until engine#410 (CaseDefinition not found after registration) is resolved. Integration tests verify the attestation pipeline, policy provider, and trust enrichment independently.

**Protocol tension:** trust-maturity-model.md says "Never hard-code trust thresholds." The devtown reference implementation puts thresholds in code. This design follows the reference impl (thresholds in code, blend/floors in YAML). Filed as parent#148 to clarify the protocol.

---

## Testing

### Unit tests (no Quarkus)

- `LifeActorIdsTest` — round-trip `of()`/`extractId()`/`isLifeActor()`; null/empty guards
- `LifeTrustRoutingPolicyKeysTest` — all floor keys present per dimension; `allFloorKeys()` size
- Routing policy validation — all 8 domain policies have entries in `POLICIES`; all fine-grained capabilities in `CAPABILITY_TO_DOMAIN` map to a valid domain; thresholds in [0.0, 1.0]; observations > 0
- `LifeRoutingPolicy` → `TrustRoutingPolicy` assembly — given a domain policy with known threshold/observations/margin, verify `forCapability()` produces the correct `TrustRoutingPolicy` with those values plus YAML-sourced blend factor and quality floors

### Integration tests (`@QuarkusTest`)

- `LifeTrustRoutingPolicyProviderTest` — inject the provider, verify `forCapability("book-appointment")` resolves to health-coordination policy; `forCapability("unknown")` returns `DEFAULT`; YAML quality floors wired correctly
- `TrustWeightedAgentStrategyDisplacementTest` — inject `AgentRoutingStrategy`, verify it is an instance of `TrustWeightedAgentStrategy` (not `LeastLoadedAgentStrategy`)
- `LifeOutcomeAttestationWriterTest` — write a health ledger entry for a completed task, verify a SOUND attestation is created with correct `capabilityTag` and `deadline-reliability` dimension score. Write an SLA breach entry, verify FLAGGED attestation.
- `ExternalActorTrustEnrichmentTest` — create ExternalActor, seed trust score via `ActorTrustScoreRepository.upsert()` (per GE-20260531-769f9c; requires qhorus datasource access — `ActorTrustScoreRepository` is on the qhorus persistence unit, injectable via `@Inject` in `@QuarkusTest` when `casehub-ledger` runtime is on classpath), verify `GET /external-actors/{id}` response contains trust profile. Verify GDPR-erased actors return `TrustProfile.EMPTY`.
- `LifeLedgerWriterActorIdTest` — health entry with ExternalActor uses `life-actor:{uuid}`; entry without uses `"life-system"`
- `ColdStartBehaviorTest` — new deployment with no trust data: `forCapability()` returns policies (code-driven, no data dependency); `TrustProfile` for a new ExternalActor returns `null`/empty maps; `TrustWeightedAgentStrategy` bootstrap-routes (Phase 0) when no scores exist

### Not tested here

- Trust score computation — casehub-ledger `TrustScoreJob` (owns this)
- `TrustWeightedAgentStrategy` routing logic — casehub-engine-ledger (owns this)
- Engine integration (case start → worker routing → attestation) — blocked on engine#410
- EscalateToOversight end-to-end — requires engine integration

---

## Files Changed

### New files

| File | Module | Purpose |
|---|---|---|
| `LifeActorIds.java` | `api/` | ActorId convention utility (pure Java domain vocabulary) |
| `LifeRoutingPolicy.java` | `app/` | Domain routing policy record (pure Java; `app/` because no separate domain module) |
| `LifeTrustRoutingPolicyProvider.java` | `app/` | `TrustRoutingPolicyProvider` implementation with capability→domain mapping |
| `LifeTrustRoutingPolicyKeys.java` | `app/` | `PreferenceKey` constants for YAML config |
| `LifeOutcomeAttestationWriter.java` | `app/` | Attestation pipeline — converts outcomes to attestations |
| `trust-routing.yaml` | `app/resources/` | Blend factors and quality floors |
| `LifeActorIdsTest.java` | `api/test/` | Unit tests |
| `LifeTrustRoutingPolicyKeysTest.java` | `app/test/` | Unit tests |
| `LifeTrustRoutingPolicyProviderTest.java` | `app/test/` | Integration test |
| `TrustWeightedAgentStrategyDisplacementTest.java` | `app/test/` | Wiring verification |
| `LifeOutcomeAttestationWriterTest.java` | `app/test/` | Attestation pipeline tests |
| `ExternalActorTrustEnrichmentTest.java` | `app/test/` | REST enrichment test |
| `LifeLedgerWriterActorIdTest.java` | `app/test/` | ActorId convention test |
| `ColdStartBehaviorTest.java` | `app/test/` | No-data graceful behavior |

### Modified files

| File | Change |
|---|---|
| `app/pom.xml` | Add `casehub-engine-ledger`, `casehub-platform-config` dependencies |
| `application.properties` | Add `classpath:db/engine-ledger/migration` to qhorus Flyway locations; set `casehub.ledger.trust-score.enabled=true` |
| `ExternalActorResponse.java` | Add `TrustProfile` field and nested record |
| `ExternalActorService.java` | Inject `TrustGateService`, build `TrustProfile` on response construction |
| `LifeLedgerWriter.java` | Contextual actorId; return persisted entry; call attestation writer |
| `ExternalActorResourceTest.java` | Update expected response shape |
| `ExternalActorGdprResourceTest.java` | Verify GDPR-erased returns `TrustProfile.EMPTY` |
