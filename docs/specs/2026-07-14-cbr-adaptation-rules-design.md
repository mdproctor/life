# CBR Domain Adaptation Rules — Design Spec

**Issue:** life#55
**Date:** 2026-07-14
**Status:** Approved

## Problem

Retrieving similar past cases is necessary but not sufficient. A past boiler
repair in summer with a 7-day SLA cannot be directly reused for a winter boiler
failure — the SLA needs tightening, the urgency is different. The CBR pipeline
currently retrieves and presents raw experiences to agents. Adaptation rules
close the gap: they take a retrieved past case's plan trace and adjust it for
the current context before agents see it.

### Design departures from issue #55

This spec departs from issue #55 in three areas, each intentional:

1. **Trust-score-aware adaptation deferred.** Issue #55 specifies contractor/
   provider retention based on trust scores. The Layer 6 trust infrastructure
   exists (`TrustGateService`, `ActorTrustScore`) but adaptation rules don't
   consume it yet. The SPI accommodates future trust-awareness by augmenting
   `currentFeatures` with trust scores before calling `adapt()` — no interface
   changes needed. Tracked as life#67.

2. **Output model: `AdaptedPlan` replaces `CbrSuggestion`.** Issue #55 proposes
   flat case-level `CbrSuggestion` records (proposed SLA, proposed contractor,
   proposed threshold). This spec uses the foundation `AdaptedPlan` SPI — a
   richer per-step model that can encode case-level parameters as step
   annotations. The issue's "engine can accept, modify, or reject" mechanism is
   replaced by advisory adaptation — agents see adapted step recommendations
   but make final decisions.

3. **YAML-driven rules rejected.** See Out of Scope for rationale.

## Foundation SPI

`casehub-neocortex-memory-api` provides the complete adaptation SPI. No
upstream changes needed.

| Type | Purpose |
|------|---------|
| `PlanAdapter` | SPI: `adapt(ScoredCbrCase<PlanCbrCase>, Map<String, FeatureValue>) → AdaptedPlan` |
| `AdaptedPlan` | Result: list of `AdaptedStep` |
| `AdaptedStep` | Per-step: bindingName, capabilityName, workerName, stepOutcome, priority, parameters, `AdaptationAction`, reason |
| `AdaptationAction` | RETAINED, SUBSTITUTED, BOOSTED, SUPPRESSED, ADDED, REMOVED |
| `AdaptationTrace` | Audit: traceId, retrievalTraceId, sourceCaseId, sourceScore, steps, currentFeatures, timestamp |
| `CbrAdaptationRecorded` | CDI event fired after adaptation completes |
| `NoOpPlanAdapter` | `@DefaultBean` — retains all steps unchanged |

## Architecture

### Layering

- **neocortex-memory-api:** SPI + data types (complete, no changes)
- **engine:** does NOT call `PlanAdapter` yet — future engine issue to wire
  it into `CbrRetrievalService` pipeline
- **life app/:** implements `PlanAdapter` with domain-specific rules, calls it
  directly from `LifeCaseService.startCase()` until engine absorbs the call

### Package structure

```
app/cbr/
  LifeAdaptationRule.java          — internal SPI
  LifePlanAdapter.java             — @Alternative, dispatches to per-domain rules

app/cbr/adapt/                     — parallel to existing describe/
  ContractorAdaptationRule.java
  HealthAdaptationRule.java
  HomeMaintenanceAdaptationRule.java
  FinancialAdaptationRule.java
  AppointmentCycleAdaptationRule.java
  TravelPlanAdaptationRule.java
```

Mirrors the existing `LifeCbrDescriptionProvider` + 6 impls in `describe/`.

### Module placement

`LifeAdaptationRule` is internal to `app/`, not `api/`, because it references
neocortex types (`ScoredCbrCase`, `PlanCbrCase`, `FeatureValue`).

## LifeAdaptationRule SPI

```java
interface LifeAdaptationRule {
    String caseType();
    Set<String> knownCapabilities();
    List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                            Map<String, FeatureValue> currentFeatures);
}
```

`knownCapabilities()` returns the capability names this rule handles,
used by `LifePlanAdapter` for case type inference from plan traces.

Each rule is a pure function — no injected dependencies, no database calls, no
side effects. Takes data in, returns adapted steps out.

## LifePlanAdapter

`@Alternative @Priority(10)` displaces `NoOpPlanAdapter` (`@DefaultBean`).

Injects all `LifeAdaptationRule` instances via `@All List<LifeAdaptationRule>`,
builds `Map<String, LifeAdaptationRule>` by `caseType()` at construction.
Constructor validates both caseType and capability uniqueness — throws
`IllegalStateException` on duplicates.

**Dual method surface:**

```java
// Foundation SPI — infers caseType from capability names in plan trace
public AdaptedPlan adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                         Map<String, FeatureValue> currentFeatures)

// Life-internal — called directly when caseType is known
public AdaptedPlan adapt(String caseType, ScoredCbrCase<PlanCbrCase> retrieved,
                         Map<String, FeatureValue> currentFeatures)
```

The SPI method infers caseType by matching capability names in the plan trace
against registered rules' known capabilities. Each case definition has unique
capability names. Falls back to retain-all if no rule matches.

## Domain Adaptation Rules

### ContractorAdaptationRule (`contractor-coordination`)

- **Season delta:** current=winter + heating/plumbing → BOOST `request-quote`
  priority, tighten `slaHours` in parameters
- **Budget delta:** scale cost expectation by budget ratio (`budgetRatio`
  parameter — crude inflation proxy)
- **Failed outcome:** if retrieved case outcome was FAILED → SUPPRESS same
  worker approach with reason. Sentinel step (`contractor-sentinel`) is
  exempt from suppression — monitoring should remain active regardless.

### HomeMaintenanceAdaptationRule (`home-maintenance`)

- **Seasonal SLA:** winter heating/plumbing issues get `resolutionDays` halved
  vs summer baseline
- **Cost delta:** adjust cost expectation from retrieved→current delta, put
  `costRatio` in parameters
- **Failed outcome:** SUPPRESS the same approach with reason. Sentinel step
  (`maintenance-sentinel`) is exempt from suppression.

### HealthAdaptationRule (`care-coordination`)

- **Severity delta:** higher current severity → BOOST follow-up priority.
  Severity-scaling logic in `SeverityScaling.scale()` static helper (shared
  with AppointmentCycle)
- **Care type change:** if current `careType` differs from retrieved → annotate
  with uncertainty reason, do not suppress
- **Past SLA breach:** if retrieved case had SLA breach → BOOST follow-up priority

### FinancialAdaptationRule (`financial-review`)

- **Amount delta:** significantly higher current amount → BOOST approval gate
  priority
- **Escalation pattern:** if retrieved cases for similar amounts had escalation
  in stepOutcome → flag threshold miscalibration in reason
- **Threshold retention:** RETAIN approval thresholds but annotate with trend
  data in parameters

### AppointmentCycleAdaptationRule (`appointment-cycle`)

- **Severity scaling:** higher severity → shorter follow-up interval, BOOST
  follow-up priority. Uses shared `SeverityScaling.scale()` static helper
- **Provider type change:** if current `providerType` differs → annotate with
  uncertainty reason
- **Prep-time adjustment:** appointment type delta → adjust preparation
  parameters

### TravelPlanAdaptationRule (`travel-plan`)

- **Budget delta:** scale booking expectations by budget ratio
- **Seasonal pricing:** different season from retrieved → annotate cost
  expectations with reason
- **Rejected booking:** if retrieved case had booking rejected → SUPPRESS same
  approach

### Adaptation Action Semantics

RETAINED, BOOSTED, and SUPPRESSED are used by the initial domain rules above.
The remaining actions defined by the foundation SPI:

- **ADDED:** a new step not in the retrieved plan trace, constructed entirely
  by the rule (e.g., winter boiler case adds an "emergency-assessment" step).
  The rule must populate all `AdaptedStep` fields — no source step to copy from.
- **REMOVED:** step dropped from the adapted plan entirely — not presented to
  workers. Distinct from SUPPRESSED, which retains the step with a discouraging
  annotation for visibility.
- **SUBSTITUTED:** step replaced with a fundamentally different approach
  (different capability, different worker). Retains the original step's
  position in the plan sequence.

No initial domain rule uses these actions. They are available for future rules
where domain logic requires adding emergency steps, removing inapplicable
steps, or substituting approaches.

## Integration

### Case start flow

Current:
```
prepareAndTrack() → suggest() → startCase() → persistCaseId()
```

New:
```
prepareAndTrack()
→ retrieveForAdaptation()   // NEW: returns LifeCbrRetrievalResult
→ adapt()                    // NEW: PlanAdapter on best match (≥1 case)
→ startCase()                // cbrCalibration AND adaptedPlan in initial context
→ persistCaseId()
```

`LifeCbrSuggestionService` gains a new method `retrieveForAdaptation()` returning
an app-internal `LifeCbrRetrievalResult` record:

```java
record LifeCbrRetrievalResult(
    CbrSuggestions suggestions,
    List<ScoredCbrCase<PlanCbrCase>> cases,
    Map<String, FeatureValue> currentFeatures) {}
```

This lives in `app/cbr/` — neocortex types stay out of `api/`. The existing
`suggest()` method (returning `CbrSuggestions`) is unchanged.

**Adaptation threshold:** `retrieveForAdaptation()` calls `cbrStore.retrieveSimilar()`
directly, independent of the ≥2 threshold in `suggest()`. Adaptation requires ≥1
matching case — a single highly-similar past case with a plan trace is the most
valuable adaptation scenario. Calibration statistics (`CbrSuggestions`) still
require ≥2 cases for meaningful means and distributions, so the `suggestions`
field in `LifeCbrRetrievalResult` may be `CbrSuggestions.EMPTY` even when `cases`
has one entry that gets adapted.

`LifeCaseService.startCase()` calls `retrieveForAdaptation()`, then
`planAdapter.adapt()` on the best-scoring case, and writes to initial context
conditionally:

| Cases | `cbrCalibration` | `adaptedPlan` |
|-------|-------------------|---------------|
| 0     | not written       | not written   |
| 1     | not written (empty statistics have no value) | written |
| ≥2    | written           | written       |

This matches the existing guard in `LifeCaseService`: `if (!suggestions.isEmpty())
{ initialContext.put("cbrCalibration", ...) }`. Adapted plan is written whenever
≥1 case exists and produces a non-empty adaptation.

### AdaptationTrace recording

After successful adaptation, construct and fire:

```java
event.fire(new CbrAdaptationRecorded(new AdaptationTrace(
    UUID.randomUUID().toString(),  // traceId
    retrievalTraceId,              // links to retrieval (nullable for now)
    scored.caseId(),               // sourceCaseId (nullable — see below)
    scored.score(),                // sourceScore
    adaptedSteps,                  // List<AdaptedStep>
    currentFeatures,               // current feature map
    Instant.now())));              // timestamp
```

`retrievalTraceId` links this adaptation to the retrieval that produced the
source case. When calling from `LifeCaseService` (before engine wires the
pipeline), pass `null` — no retrieval trace exists yet in the life-internal
path.

`sourceCaseId` may be null if the `ScoredCbrCase` was constructed without a
caseId (convenience constructors exist). Log a warning on null caseId — the
trace is still valid but untraceable to a specific source case.

No observer in life — the event is for future consumers (ledger, analytics).

### Worker consumption

Adapted plan data reaches workers through two mechanisms:

1. **Case context flow:** `adaptedPlan` is serialized to `initialContext`.
   YAML `inputSchema` expressions on relevant bindings include `.adaptedPlan`
   to map it into worker input (same pattern as existing `.cbrCalibration`).

2. **CbrInputTransformer formatting:** `CbrInputTransformer.apply()` checks
   the input `JsonNode` for an `adaptedPlan` key. If present, it formats the
   adapted plan alongside existing experience data in `_cbrContext`. The
   transformer reads experiences from `WorkerExecutionContext.current()`
   (engine-populated) and adapted plan from the input `JsonNode`
   (case-context-populated via inputSchema).

`LifeCbrExperienceFormatter` gains a `formatAdaptedPlan(AdaptedPlan)` method
producing structured text per step: capability name, action, priority,
reason, and adjusted parameters (key=value pairs). Example output:

    ## Adapted Plan
    ### request-quote — BOOSTED (priority: 8)
      Reason: Winter heating issue — tighter SLA needed
      Parameters: slaHours=24
    ### job-monitoring — RETAINED (priority: 5)
    ### previous-approach — SUPPRESSED
      Reason: Past case with this approach failed

Workers see both:
- "Here's what similar past cases did" (existing raw experiences)
- "Here's how we recommend adjusting for your current context" (adapted plan)

Adaptation is advisory — agents make the final call.

### Case type inference (SPI method)

When the engine eventually calls `PlanAdapter.adapt()` via the SPI (no explicit
caseType), `LifePlanAdapter` infers caseType by matching capability names from
the retrieved case's `planTrace` against each registered rule. Each life case
definition has unique capabilities, so matching is unambiguous.

Capability uniqueness is a maintained invariant: new case definitions must use
distinct capability names. `LifePlanAdapterTest` asserts no overlap across all
registered rules. If no rule matches, the adapter falls back to retain-all
(returns all steps as RETAINED with no modifications).

This inference is a defensive fallback — the life-internal path always passes
`caseType` explicitly. When the engine wires `PlanAdapter` into its pipeline,
it should pass `caseType` from `CbrQuery.caseType()` (see Engine Issue below).

## Engine Issue (cross-repo)

The engine's `CbrRetrievalService` should eventually call `PlanAdapter` as part
of its pipeline: retrieve → adapt → map to `RetrievedExperience`. This makes
adaptation automatic for all apps. Two engine requirements:

1. **PlanAdapter wiring** — `CbrRetrievalService` calls `PlanAdapter.adapt()`
   after retrieval, before mapping to `RetrievedExperience`. New engine issue
   needed (engine#707 covers experience flow to workers, not adapter wiring).
2. **caseType pass-through** — the engine should pass `CbrQuery.caseType()` to
   the adapter, avoiding capability-name inference. This may require an SPI
   extension (e.g., `adapt(String caseType, ScoredCbrCase, Map)` overload on
   `PlanAdapter`).

Issue #56 is CLOSED — routing integration is complete. engine#707 (open) tracks
experience flow to workers. PlanAdapter wiring is a separate concern to be filed.

Until the engine wires it, life calls `PlanAdapter` directly from
`LifeCaseService.startCase()`.

## Testing

### Unit tests (no Quarkus)

Each `LifeAdaptationRule` — pure function, no dependencies:
- Feature delta fires correct adaptation (season, budget, severity, amount)
- No delta → all steps RETAINED
- Missing features → graceful handling (no crash, degrade to RETAINED)
- Edge cases — zero budget, null severity
- Empty plan trace → return empty `List<AdaptedStep>` (no adaptation needed)
- Null `ScoredCbrCase.caseId()` → log warning, trace still valid
- Failed outcome → SUPPRESS with reason

`LifePlanAdapter` dispatch:
- Known caseType → correct rule
- Unknown caseType → retain-all fallback
- SPI inference from capability names
- Capability name uniqueness across all registered rules (no overlaps)
- caseType uniqueness across all registered rules (no silent `Map.put()` overwrites)

### Integration tests (`@QuarkusTest`)

- CDI wiring: `PlanAdapter` resolves to `LifePlanAdapter` (displaces NoOp)
- `CbrInputTransformer` includes adapted plan in formatted `_cbrContext`

### Component tests (no Quarkus)

- `LifeCbrSuggestionService.retrieveForAdaptation()`: ≥1 threshold, conditional
  `CbrSuggestions` population, feature pass-through
- `LifeCbrExperienceFormatter.formatAdaptedPlan()`: heading/action/priority/
  reason/parameter formatting
- `CbrInputTransformer` adapted plan path: deserialization, formatting,
  combination with experience text, raw JSON removal

## Out of Scope

- Engine wiring of `PlanAdapter` into `CbrRetrievalService` — cross-repo, filed
  as engine issue
- Directive adaptation (changing binding priorities/suppressing at engine level)
  — future enhancement, current approach is advisory
- YAML-driven adaptation rules — procedural logic doesn't fit a declarative DSL.
  Issue #55 considers "rules should be declarative where possible (YAML config)
  with code fallback for complex adaptation logic." Rejected: adaptation rules
  require conditional logic on feature deltas, step-level iteration, and
  domain-specific branching that a YAML DSL cannot express without becoming a
  programming language. Pure Java functions are simpler, testable, and
  debuggable.
- Trust-score-aware adaptation — tracked as life#67. The SPI accommodates this
  by augmenting `currentFeatures` with trust scores before calling `adapt()`.
  No interface changes needed.
- `FeatureStatistics` upstream move — filed as life#66
