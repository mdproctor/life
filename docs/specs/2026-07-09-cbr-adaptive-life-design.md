# CBR for Adaptive Life Automation — Design Spec

> **Epic:** life#52 · **Branch:** `issue-52-cbr-adaptive-life`
> **Delivers:** #53, #57. **Prepares:** #56 (YAML config, awaits engine#505/683). **Defers:** #55 (REVISE — no foundation SPI)

---

## Dependency Tracker

| Dependency | Repo | Issue | Status | What it unblocks |
|-----------|------|-------|--------|------------------|
| Routing strategy consumes experiences | engine | #505 | **OPEN** | CBR experiences influence worker selection |
| RoutingPromptSection in engine-api | engine | #683 | **OPEN** | Standard CBR prompt injection for LLM agents |
| CaseOutcomeObserver SPI | engine | #477 | DONE | Per-case retention — `LifeCaseOutcomeCbrWriter` |
| RoutingOutcomeRecorder SPI | engine | — | DONE | Per-routing-decision retention |
| CbrCaseMemoryStore + adapters | neocortex | — | DONE | Storage, retrieval, similarity scoring |
| CbrConfig YAML support | engine | #478 | DONE | `spec.cbr` on YAML case definitions |
| CbrSimilarityScorer + SimilaritySpec | neocortex | — | DONE | Weighted per-field similarity with decay functions |
| Add tenantId to CaseOutcomeEvent | engine | TBD | **OPEN** | Per-case retention uses correct tenant; interim: `"life-personal"` constant |

**When engine#505 lands:** No new life code needed — `TrustWeightedAgentStrategy` (or successor) will automatically consume the experiences that `CbrRetrievalService` already populates in `AgentRoutingContext`. Data is already flowing; #505 makes the strategy read it.

**When engine#683 lands:** Life implements `RoutingPromptSection` (or uses default `CbrRoutingPromptSection` if promoted from blocks). CBR store already has data from the two retention writers.

---

## Child Issue Restructuring

| Original Issue | Original Scope | Actual Status | Action |
|---------------|---------------|---------------|--------|
| #53 — case base schema | Feature schemas, construction | Foundation model exists; life needs domain schemas | **Keep — absorbs #54 domain work** |
| #54 — similarity engine | Feature extraction, distance metrics | Fully provided by neocortex (`CbrSimilarityScorer`) | **Close — absorbed into #53** |
| #55 — domain adaptation rules | REVISE step | No platform mechanism; long-term | **Keep — deferred, no foundation SPI yet** |
| #56 — engine integration | Wire suggestions into execution | Plumbing exists; gaps are engine#505, #683 | **Keep — tracks engine dependencies** |
| #57 — retention pipeline | Case completion feeds case base | `CaseOutcomeObserver` + `RoutingOutcomeRecorder` exist | **Keep — life implements both** |

---

## Scope

**What life builds:**

| Component | CBR Step | Foundation SPI |
|-----------|----------|----------------|
| `LifeCaseOutcomeCbrWriter` | Retain (per-case) | `CaseOutcomeObserver` |
| `LifeRoutingOutcomeRecorder` | Retain (per-routing-decision) | `RoutingOutcomeRecorder` |
| `LifeCbrFeatureSchemaRegistrar` | Retrieve (schema setup) | `CbrCaseMemoryStore.registerSchema()` |
| `LifeCbrDescriptionProvider` | Retain (problem/solution text) | Life-internal SPI |
| `spec.cbr` on 6 YAML case definitions | Retrieve (feature extraction) | `CbrConfig` + `CbrRetrievalService` |

**Case types covered:** appointment-cycle, care-coordination, contractor-coordination, financial-review, home-maintenance, travel-plan.

**Excluded:** family-vote (M-of-N sub-case), care-episode (sub-case).

**Deferred:** CBR-aware routing (engine#505), prompt enrichment (engine#683), REVISE/adaptation (#55).

---

## Feature Extraction — Unified Approach

Both retention paths (per-case and per-routing-decision) extract features using the **same JQ expressions** defined in each YAML case definition's `spec.cbr.features` section. This guarantees feature alignment by construction — the same expressions produce features at both retention and retrieval time.

**Mechanism:** At retention time, `LifeCaseOutcomeCbrWriter` and `LifeRoutingOutcomeRecorder` look up the `CaseDefinition` via `CaseDefinitionRegistry.findByName()`, retrieve the `CbrConfig`, and check its `featureExtractor()`:

- If `instanceof JqFeatureExtractor jq`: extract the JQ expression map from `jq.featureExpressions()` and evaluate using the injected `JQEvaluator` (`io.casehub.platform.expression.JQEvaluator`, `@ApplicationScoped`).
- If `instanceof LambdaFeatureExtractor`: skip CBR retention with a warning log. Lambda extractors require a `CaseContext` that is unavailable at retention time. YAML-defined case definitions always produce `JqFeatureExtractor`; this guard handles the sealed interface exhaustively.

**JQ evaluation:** Both writers inject `io.casehub.platform.expression.JQEvaluator` (public API from `casehub-platform-expression`). For each entry in `jq.featureExpressions()`, call `jqEvaluator.eval(expression, jsonNode)`, unwrap `ValidationResult.output()` to feature values, skip null/empty results with debug logging. This mirrors the evaluation pattern in `CbrRetrievalService.extractJqFeatures()`.

**Data type handling:**

- **Per-case path:** `CaseOutcomeEvent.caseFileSnapshot()` (`Map<String, Object>`) is converted to `JsonNode` via `ObjectMapper.valueToTree()`, then JQ expressions are evaluated against it.
- **Per-routing-decision path:** `AgentRoutingContext.caseContext()` is already `JsonNode` — JQ expressions are evaluated directly.

**Problem/solution descriptions** are domain-specific text summaries that cannot be derived from JQ expressions. These are provided by `LifeCbrDescriptionProvider`, a life-internal SPI with one implementation per case type:

```java
interface LifeCbrDescriptionProvider {
    String caseType();
    String describeProblem(Map<String, Object> caseData);
    String describeSolution(Map<String, Object> caseData);
    String extractEntityId(Map<String, Object> caseData, UUID caseId);
}
```

`extractEntityId()` returns the external actor ID for cases involving a data subject (contractor, provider, coordinator) or `caseId.toString()` for self-referential cases. The `caseId` fallback parameter ensures every case type can return a valid entityId.

Six implementations discovered via `Instance<LifeCbrDescriptionProvider>` and indexed by `caseType()`.

---

## Retention — Per-Case (`LifeCaseOutcomeCbrWriter`)

**Implements:** `CaseOutcomeObserver` from `casehub-engine-api`

**Trigger:** Engine calls `onOutcome(CaseOutcomeEvent)` when a case reaches COMPLETED, FAULTED, or CANCELLED.

**Behaviour:**

1. Filter: only process case types matching life's 6 CBR-eligible types (by `event.caseType()`)
2. Look up `CaseDefinition` via `CaseDefinitionRegistry.findByName(event.caseType())`. Skip if no definition or no `CbrConfig`.
3. Extract features from `event.caseFileSnapshot()` using the CbrConfig's JQ expressions (see §Feature Extraction — Unified Approach)
4. Derive `problem` and `solution` descriptions via the matching `LifeCbrDescriptionProvider`
5. Set `outcome` = `event.outcomeLabel()` ("COMPLETED" / "FAULTED" / "CANCELLED")
6. Build `PlanCbrCase` with `planTrace = List.of()` (per-case outcomes carry no per-step routing data)
7. Write to `CbrCaseMemoryStore` with parameters per the table below

**Store parameters:**

| Parameter | Value | Source |
|-----------|-------|--------|
| `cbrCase` | `PlanCbrCase` instance | Built in step 6 |
| `caseType` | `event.caseType()` | `CaseOutcomeEvent.caseType()` |
| `entityId` | `descriptionProvider.extractEntityId(snapshot, event.caseId())` | See §entityId Convention |
| `domain` | `cbrConfig.domain()` | `CaseDefinition` → `CbrConfig.domain()` |
| `tenantId` | `"life-personal"` (interim) | Well-known constant until engine adds `tenantId` to `CaseOutcomeEvent` |
| `caseId` | `event.caseId().toString()` | `CaseOutcomeEvent.caseId()` |

---

## Retention — Per-Routing-Decision (`LifeRoutingOutcomeRecorder`)

**Implements:** `RoutingOutcomeRecorder` from `casehub-engine-api`

**Contract:** `Uni<Void> record(AgentRoutingContext context, String workerId, String bindingName, RoutingOutcome outcome, @Nullable Duration executionDuration)` — returns a `Uni` that performs the work when subscribed. The engine subscribes fire-and-forget; recording failure never blocks execution.

**Trigger:** Engine calls `record()` after every worker execution (success and failure).

**Reactive pipeline:**

```
Uni.createFrom().item(() -> {
    // 1. Resolve case type
    // 2. Extract features via CbrConfig JQ
    // 3. Build PlanCbrCase with PlanTrace
    // 4. Write to store
    return null;
}).emitOn(Infrastructure.getDefaultWorkerPool())
  .replaceWithVoid()
```

**Steps:**

1. Resolve case type: `LifeCaseTracker.findByEngineCaseId(context.caseId())` returns `Optional<LifeCaseTracker>`. If absent, this is not a life case — return `Uni.createFrom().voidItem()` (skip recording). Extract `caseType` string from `tracker.caseType`.
2. Look up `CaseDefinition` via `CaseDefinitionRegistry.findByName(caseType)`. Skip if no definition or no `CbrConfig`.
3. Extract features from `context.caseContext()` (`JsonNode`) using the CbrConfig's JQ expressions (see §Feature Extraction — Unified Approach). No type conversion needed — `caseContext` is already `JsonNode`.
4. Build `PlanTrace` from call parameters: `bindingName`, `context.capabilityName()`, `workerId`, `outcome`.
5. Build `PlanCbrCase` with problem/solution from `LifeCbrDescriptionProvider` (convert `context.caseContext()` to `Map<String, Object>` via `ObjectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>(){})` for the description provider call), features from step 3, and planTrace from step 4.
6. Write to `CbrCaseMemoryStore` with parameters per the table below.

**Store parameters:**

| Parameter | Value | Source |
|-----------|-------|--------|
| `cbrCase` | `PlanCbrCase` instance | Built in step 5 |
| `caseType` | `tracker.caseType` | `LifeCaseTracker.caseType` |
| `entityId` | `"agent-routing"` | Fixed: per-routing entries are keyed by routing concept, not by entity |
| `domain` | `cbrConfig.domain()` | `CaseDefinition` → `CbrConfig.domain()` |
| `tenantId` | `context.tenancyId()` | `AgentRoutingContext.tenancyId()` |
| `caseId` | `context.caseId().toString()` | `AgentRoutingContext.caseId()` |

---

## entityId Convention and GDPR

The `entityId` parameter on `CbrCaseMemoryStore.store()` drives `eraseEntity(entityId, tenantId)` — the GDPR data erasure hook. Life uses two entityId conventions depending on whether the case involves an external data subject:

| Case Type | entityId | Rationale |
|-----------|----------|-----------|
| contractor-coordination | External contractor ID | Erasure removes all CBR data about that contractor (data subject) |
| appointment-cycle | Provider ID | Erasure removes all CBR data about that provider |
| care-coordination | Care coordinator ID | Erasure removes all CBR data about that coordinator |
| financial-review | Case ID | No external data subject — data belongs to household |
| home-maintenance | Case ID | No external data subject — data belongs to household |
| travel-plan | Case ID | No external data subject — data belongs to household |

**Privacy rationale:** When an external actor (contractor, provider, coordinator) is a data subject under GDPR, erasing their entity removes all CBR entries involving them across all cases. For self-referential cases with no external data subject, each case is independently erasable via its case ID.

---

## Feature Schemas and Registration

**`LifeCbrFeatureSchemaRegistrar`** — `@ApplicationScoped`, `@Observes StartupEvent`. Registers 6 `CbrFeatureSchema` instances.

### Feature definitions per case type

| Case Type | Categorical | Numeric |
|-----------|-------------|---------|
| contractor-coordination | problemType, season, propertyArea | budget, quotedCost, slaHours |
| home-maintenance | issueType, severity, season | estimatedCost, resolutionDays |
| appointment-cycle | conditionCategory, providerType | followUpIntervalDays |
| care-coordination | careType, patientRiskLevel | hoursPerWeek |
| financial-review | category, amountRange | amount, approvalThreshold |
| travel-plan | destination, travelType, season | budget, durationDays, partySize |

### Similarity specs (non-default)

- **season:** `CategoricalTable` — summer/spring=0.7, summer/autumn=0.5, summer/winter=0.3, etc.
- **severity / patientRiskLevel:** `CategoricalTable` — high/critical=0.8, high/medium=0.4, etc.
- **budget / amount / estimatedCost:** `GaussianDecay(sigma=0.3)` — smooth decay for cost similarity
- **slaHours / followUpIntervalDays / durationDays:** `GaussianDecay(sigma=0.25)` — smooth decay for time

### Numeric field ranges (normalisation)

| Field | Min | Max |
|-------|-----|-----|
| budget / cost fields | 0 | 10,000 |
| slaHours | 1 | 720 |
| followUpIntervalDays | 1 | 365 |
| durationDays | 1 | 90 |
| partySize | 1 | 20 |
| hoursPerWeek | 1 | 168 |
| approvalThreshold | 0 | 10,000 |

Starting points — tuned as data accumulates.

---

## Domain Resolution

The CBR `domain` must match exactly between retention (store) and retrieval (query). Both paths derive it from `CbrConfig.domain()` on the `CaseDefinition`, guaranteeing alignment by construction.

| Case Type | `spec.cbr.domain` |
|-----------|-------------------|
| contractor-coordination | `casehubio/life/contractor` |
| home-maintenance | `casehubio/life/household` |
| appointment-cycle | `casehubio/life/health` |
| care-coordination | `casehubio/life/eldercare` |
| financial-review | `casehubio/life/finance` |
| travel-plan | `casehubio/life/travel` |

Follows the existing scope convention: `casehubio/life/{domain}`.

---

## YAML CbrConfig

Each YAML case definition gets a `spec.cbr` section. Example for contractor-coordination:

```yaml
spec:
  cbr:
    features:
      problemType: ".contractorRequest.problemType"
      season: ".contractorRequest.season"
      propertyArea: ".contractorRequest.propertyArea"
      budget: ".contractorRequest.budget"
      quotedCost: ".quoteResponse.quotedAmount"
      slaHours: ".contractorRequest.slaHours"
    weights:
      problemType: 3.0
      budget: 2.0
      season: 1.5
      propertyArea: 1.0
      slaHours: 1.0
      quotedCost: 0.5
    topK: 5
    minSimilarity: 0.3
    domain: casehubio/life/contractor
    caseType: contractor-coordination
    timing: CASE_LIFETIME
```

**Key choices:**
- **`timing: CASE_LIFETIME`** — life cases are long-running; retrieve once and cache.
- **`minSimilarity: 0.3`** — permissive floor; better to surface loosely similar than miss relevant.
- **`topK: 5`** — enough context, not noise.
- **Weights reflect domain importance** — primary discriminator (e.g. `problemType`) weighted highest.
- **`domain` uses existing scope convention** — `casehubio/life/{domain}`.

The other 5 YAMLs follow the same pattern with their domain-specific features.

---

## Production Activation

**Default (no Qdrant):** `NoOpCbrCaseMemoryStore` (`@DefaultBean`) — all retention writes are silently discarded. Schema registrations are no-ops. Retrieval returns empty. This is the platform's graceful degradation pattern.

**To activate CBR storage:**

1. Add Maven dependency: `casehub-neocortex-memory-qdrant` (or `casehub-neocortex-memory-cbr-inmem` for local development)
2. Configure Qdrant connection in `application.properties`:
   ```properties
   quarkus.qdrant.host=localhost
   quarkus.qdrant.port=6334
   ```
3. `QdrantCbrCaseMemoryStore` is `@Alternative @Priority(1)` — overrides `NoOpCbrCaseMemoryStore` automatically when on the classpath.

**Verification:** After activation, the 6 schemas registered by `LifeCbrFeatureSchemaRegistrar` create Qdrant collections. Case completion and routing decisions produce stored entries visible in the Qdrant dashboard.

---

## Wiring and Test Infrastructure

**Maven dependencies (app/pom.xml):**
- `casehub-neocortex-memory-cbr-api` — CBR types
- `casehub-platform-expression` — `JQEvaluator` for retention-time feature extraction
- `casehub-neocortex-memory-cbr-inmem` (test scope) — `InMemoryCbrCaseMemoryStore`

**CDI:** `InMemoryCbrCaseMemoryStore` is `@Alternative @Priority(2)` — activates in tests automatically. Production: `NoOpCbrCaseMemoryStore` (`@DefaultBean`) — graceful degradation until Qdrant is configured (see §Production Activation).

**Test strategy:**
- Unit tests for each `LifeCbrDescriptionProvider` — problem/solution descriptions per case type
- Unit test for `LifeCaseOutcomeCbrWriter` — mock store, verify PlanCbrCase assembly with correct features (from CbrConfig JQ), problem/solution descriptions, empty planTrace, and all 6 store parameters
- Unit test for `LifeRoutingOutcomeRecorder` — mock store, verify PlanTrace assembly, Uni pipeline, and all 6 store parameters
- Unit test for `LifeCbrFeatureSchemaRegistrar` — verify all 6 schemas with correct fields and specs
- Integration test (`@QuarkusTest`) — seed case → complete → verify CBR entry in InMemory store → start similar case → verify retrieval returns experience in `AgentRoutingContext.experiences()`
- Alignment test — for each case type, verify that the feature names produced by JQ extraction against a sample caseFileSnapshot match the registered schema field names

**No Flyway migrations.** CBR data lives in `CbrCaseMemoryStore`, not relational DB.

---

## Package Structure

```
app/src/main/java/io/casehub/life/app/cbr/
    LifeCaseOutcomeCbrWriter.java
    LifeRoutingOutcomeRecorder.java
    LifeCbrFeatureSchemaRegistrar.java
    LifeCbrDescriptionProvider.java

app/src/main/java/io/casehub/life/app/cbr/describe/
    ContractorCoordinationDescriptionProvider.java
    HomeMaintenanceDescriptionProvider.java
    AppointmentCycleDescriptionProvider.java
    CareCoordinationDescriptionProvider.java
    FinancialReviewDescriptionProvider.java
    TravelPlanDescriptionProvider.java
```

No modifications to existing classes. The 6 YAML case definitions get `spec.cbr` sections — edits to existing files.
