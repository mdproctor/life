# CBR Engine Integration — Design Spec

> **Issue:** life#56 · **Branch:** `issue-56-cbr-engine-integration`
> **Epic:** life#52 (CBR for adaptive life automation)
> **Depends on:** engine#707 (CLOSED — CBR experiences flow to WorkerContext)

---

## Dependency Tracker

| Dependency | Repo | Issue | Status | What it unblocks |
|-----------|------|-------|--------|------------------|
| CBR experiences on WorkerContext | engine | #707 | **CLOSED** | Workers can read historical experiences at execution time |
| Routing consumes CBR experiences | engine | #505 | **CLOSED** | Routing strategies use CBR for agent selection |
| RoutingPromptSection in engine-api | engine | #683 | **CLOSED** | Standard CBR prompt injection for LLM routing |
| CBR retention pipeline | life | #53, #57 | **CLOSED** | Data flows into CBR store |
| CbrCaseMemoryStore API | neocortex | — | DONE | Retrieval API for structured calibration |

All blockers resolved. This issue is self-contained within life.

---

## Scope

Wire CBR suggestions into case plan execution through two complementary paths:

1. **Prompt enrichment** (per-worker-execution, automatic) — every LLM worker sees formatted historical experiences in its user message via `Agent.inputTransformer`. Qualitative: the LLM reasons about patterns.

2. **Structured calibration** (per-case-start, deterministic) — `LifeCbrSuggestionService` queries the CBR store, computes statistical summaries of numeric features from similar past cases, and writes a `cbrCalibration` field to the case context. Quantitative: workers see p50 cost, typical duration, historical success rate.

**Not in scope:**
- Engine-level `Agent.execute()` changes — life handles the last mile
- `WorkerPromptSection` SPI — not yet available in engine; life uses `inputTransformer` (wsp-casehub-life#1)
- CBR-informed SLA templates — WorkItemTemplate SLA is static; workers use calibration data to *recommend* timelines, not override template SLA (wsp-casehub-life#2)
- Re-calibration on demand — structured calibration is computed once at case start; re-calibration mid-case is a future enhancement (wsp-casehub-life#3)
- Sentinel CBR enrichment — sentinels run outside the engine's worker execution pipeline (no `WorkerExecutionContext`); they see `cbrCalibration` via case context only (wsp-casehub-life#4)

---

## Architecture

```
Path 1: Prompt Enrichment (per-worker-execution, automatic)
  Engine routing → WorkerContext.experiences()
    → CbrInputTransformer (reads WorkerExecutionContext thread-local)
      → LifeCbrExperienceFormatter (formats List<RetrievedExperience>)
        → merged as _cbrContext in user message JSON
          → LLM reasons about historical patterns

Path 2: Structured Calibration (per-case-start, deterministic)
  LifeCaseService.startCase()
    → LifeCbrSuggestionService.suggest(caseType, initialContext)
      → CbrCaseMemoryStore.retrieveSimilar(query, PlanCbrCase.class)
        → FeatureStatistics extraction (min/max/median/p75 per numeric feature)
          → CbrSuggestions written to case context as cbrCalibration
            → workers see via JQ inputProjection
```

Five new components, no engine changes, no new config files:

| Component | Package | Type | Purpose |
|-----------|---------|------|---------|
| `LifeCbrExperienceFormatter` | `app/cbr/` | `@ApplicationScoped` | Format `List<RetrievedExperience>` into structured prompt text |
| `CbrInputTransformer` | `app/cbr/` | `UnaryOperator<JsonNode>` | Read `WorkerExecutionContext.current().experiences()`, merge into input |
| `LifeCbrFeatureExtractor` | `app/cbr/` | `@ApplicationScoped` | Shared feature extraction: registry lookup → CbrConfig → JQ evaluation → `FeatureValue` map |
| `LifeCbrSuggestionService` | `app/cbr/` | `@ApplicationScoped` | Query CBR store, compute feature statistics |
| `CbrSuggestions` + `FeatureStatistics` | `api/` | Records | Carry calibrated parameters |

### Dual-Path Relationship

Path 1 and Path 2 are intentionally complementary, not redundant:

- **Path 2** (case start) provides **aggregate statistics** — numerical parameters (cost ranges, success rates, sample counts) computed from similar past cases using the initial context before any workers have run. These are quantitative baselines for calibrating thresholds and estimates.
- **Path 1** (worker execution) provides **individual experience records** — narrative descriptions of specific similar cases (problem, solution, outcome) retrieved at execution time via `CbrRetrievalService`. These are qualitative context for LLM reasoning.

A worker receiving both `cbrCalibration` (Path 2) and `_cbrContext` (Path 1) sees data from two different retrieval queries that may have retrieved different cases — this is expected. The two paths serve distinct purposes: `cbrCalibration.featureStats.estimatedCost.median` tells the worker "similar jobs typically cost £380–450" while `_cbrContext` tells it "here's a specific case where a contractor quoted £450 and was negotiated to £380." Statistics calibrate; examples inform reasoning. They do not conflict.

---

## Path 1: Prompt Enrichment

### LifeCbrExperienceFormatter

Stateless service in `app/cbr/`. Formats `List<RetrievedExperience>` into structured text for LLM consumption.

**Input:** `List<RetrievedExperience>` from `WorkerContext.experiences()`

**Output:** `@Nullable String` — formatted text, or `null` if the list is empty.

**Format per experience:**
```
## Similar Case (similarity: 0.82)
Problem: Contractor quoted £450 for bathroom tiling, 3-day timeline
Solution: Negotiated to £380, completed in 5 days with one follow-up
Outcome: COMPLETED
Key features: tradeType=tiling, estimatedCost=450, expectedDuration=3
Most similar on: estimatedCost (0.91), tradeType (1.00)
```

**Behaviour:**
- Sorted by `similarityScore` descending (most relevant first)
- Capped at configurable max (default 5 — matches typical `topK` in YAML spec.cbr)
- `featureSimilarities` rendered only when non-empty — shows the LLM *why* this case matched
- `planTrace` included when non-empty — shows per-step execution history
- Empty input list → returns `null`

### CbrInputTransformer

Implements `UnaryOperator<JsonNode>`. Created once in `LifeTypedCaseHub`, shared across all agents. Stateless — receives `LifeCbrExperienceFormatter` at construction.

**Execution-time behaviour:**
```java
public JsonNode apply(JsonNode input) {
    WorkerContext ctx = WorkerExecutionContext.current();
    if (ctx == null || ctx.experiences().isEmpty()) {
        return input;  // pass-through — no CBR context available
    }
    String formatted = formatter.format(ctx.experiences());
    if (formatted == null) {
        return input;
    }
    // Defensive copy: Agent.execute() constructs fresh input today via convertValue(),
    // but the inputTransformer contract does not guarantee this. deepCopy() protects
    // against future engine changes that might pass a shared node.
    ObjectNode enriched = input.deepCopy();
    enriched.put("_cbrContext", formatted);
    return enriched;
}
```

**Why `inputTransformer` and not custom WorkerFunction:** `Agent` is `final` and `systemPrompt` is immutable at construction. The `inputTransformer` is the only dynamic hook that runs at execution time. It runs inside `Agent.execute()` after `WorkerExecutionContext.set(context)` has been called by `SyncAgentWorkerFunctionHandler` — so the thread-local is available by design, not by accident.

### agentWorker() Change

`LifeTypedCaseHub.agentWorker()` gains the transformer:

```java
protected Worker agentWorker(String capabilityName, String systemPrompt,
                              Class<?> responseSchema) {
    Agent a = Agent.builder()
            .model(openClawFactory.forAgent(agent))
            .systemPrompt(systemPrompt + "\n\n" + CBR_SYSTEM_PROMPT_SUFFIX)
            .inputTransformer(cbrInputTransformer)
            .responseSchema(responseSchema)
            .build();
    return Worker.builder()
            .name(capabilityName + "-agent")
            .capabilityName(capabilityName)
            .function(new AgentWorkerFunction(a))
            .build();
}
```

`cbrInputTransformer` is a field on `LifeTypedCaseHub`, initialized from the injected `LifeCbrExperienceFormatter`.

### CBR System Prompt Suffix

Appended to every worker's system prompt via `CBR_SYSTEM_PROMPT_SUFFIX`:

```
If a _cbrContext section is present in the input, it contains summaries of
similar past cases. Use these to calibrate your response — adjust cost
estimates, timeline predictions, and risk assessments based on historical
patterns. If no _cbrContext is present, proceed with your best judgment.
```

Always present — the LLM handles the absent-data case gracefully.

### Sentinel Exclusion

Sentinel agents (`LifeHeartbeatJob`) build agents manually outside `agentWorker()`. They run outside the engine's worker execution pipeline — no `WorkerExecutionContext` is set. Sentinels see CBR data only via `cbrCalibration` on the case context (Path 2), not via prompt enrichment (Path 1). No change to `LifeHeartbeatJob`.

---

## Path 2: Structured Calibration

### LifeCbrFeatureExtractor

`@ApplicationScoped` in `app/cbr/`. Consolidates the feature extraction pipeline shared by `LifeCaseOutcomeCbrWriter`, `LifeRoutingOutcomeRecorder`, and `LifeCbrSuggestionService` — eliminates three independent copies of the same registry-lookup → CbrConfig-check → JQ-evaluation → FeatureValue-conversion sequence.

**Dependencies:**
- `CaseDefinitionRegistry` — CbrConfig access
- `JQEvaluator` — JQ expression evaluation

```java
@ApplicationScoped
public class LifeCbrFeatureExtractor {

    public record ExtractionResult(CbrConfig config, Map<String, FeatureValue> features) {}

    public Optional<ExtractionResult> extract(String caseType, JsonNode context) {
        var definition = registry.findByName(caseType).orElse(null);
        if (definition == null || definition.getCbrConfig() == null) return Optional.empty();

        CbrConfig config = definition.getCbrConfig();
        if (!(config.featureExtractor() instanceof JqFeatureExtractor jq)) {
            return Optional.empty();
        }

        Map<String, Object> rawFeatures = new LinkedHashMap<>();
        for (var entry : jq.featureExpressions().entrySet()) {
            ValidationResult result = jqEvaluator.eval(entry.getValue(), context);
            if (!result.ok() || result.output().isEmpty()) continue;
            JsonNode node = result.output().get(0);
            if (node.isNull()) continue;
            rawFeatures.put(entry.getKey(), unwrap(node));
        }

        return Optional.of(new ExtractionResult(config, FeatureValue.toFeatureMap(rawFeatures)));
    }
}
```

Returns `Optional.empty()` when there is no CaseDefinition, no CbrConfig, or a `LambdaFeatureExtractor` (unsupported at retention/suggestion time). The `ExtractionResult` bundles the `CbrConfig` alongside extracted features because all callers need config fields (domain, weights, topK) for downstream operations.

**Refactoring scope:** `LifeCaseOutcomeCbrWriter.extractFeatures()` and `LifeRoutingOutcomeRecorder.extractFeatures()` are replaced by calls to this extractor. The `unwrap()` utility moves here from `LifeCaseOutcomeCbrWriter`.

### LifeCbrSuggestionService

`@ApplicationScoped` in `app/cbr/`. Queries `CbrCaseMemoryStore` directly (public API from `casehub-neocortex-memory-api`).

**Dependencies:**
- `LifeCbrFeatureExtractor` — shared feature extraction
- `CbrCaseMemoryStore` — retrieval
- `ObjectMapper` — JSON conversion

**Method:**
```java
public CbrSuggestions suggest(LifeCaseType caseType, Map<String, Object> initialContext)
```

**Error handling:** The entire method body is wrapped in try/catch. On any exception (Qdrant unreachable, JQ evaluation failure, malformed response), logs a warning and returns `CbrSuggestions.EMPTY`. CBR is advisory — a suggestion failure must never prevent a case from starting. This matches the graceful degradation pattern established by `LifeCaseOutcomeCbrWriter.onOutcome()` and `LifeRoutingOutcomeRecorder.record()`.

**Pipeline:**
1. Convert `initialContext` to `JsonNode` via `ObjectMapper.valueToTree()`.
2. Call `featureExtractor.extract(caseType.caseName(), contextNode)`. If empty → return `CbrSuggestions.EMPTY`.
3. Build `CbrQuery` using the builder pattern:
   ```java
   CbrQuery query = CbrQuery.of(
           TENANT_ID,                                    // "life-personal"
           new MemoryDomain(config.domain()),             // String → MemoryDomain wrapping
           caseType.caseName(),                           // YAML case type name
           result.features(),                             // Map<String, FeatureValue> from extractor
           config.topK()                                  // from CbrConfig
       )
       .withWeights(config.weights())                     // from CbrConfig
       .withMinSimilarity(config.minSimilarity())         // from CbrConfig
       .withVectorWeight(config.vectorWeight());           // from CbrConfig
   ```
   Remaining `CbrQuery` parameters use `of()` defaults: `filters` = empty map, `notBefore` = null (all historical cases eligible), `problem` = null (no semantic text search — feature-based retrieval is sufficient for structured calibration), `retrievalMode` = `HYBRID`, `fusionStrategy` = `RRF`.
4. Call `cbrCaseMemoryStore.retrieveSimilar(query, PlanCbrCase.class)` → `List<ScoredCbrCase<PlanCbrCase>>`.
5. If fewer than 2 results → return `CbrSuggestions.EMPTY`. Two cases is the minimum for meaningful statistics.
6. For each numeric feature across results: compute `FeatureStatistics` (min, max, median, p75, sampleCount). Non-numeric features (categorical) are excluded. Numeric features are identified by `FeatureValue.NumberVal` type.
7. Compute `historicalSuccessRate` = count(outcome == "COMPLETED") / total.
8. Compute `averageSimilarity` = mean of `score()` across results.
9. Return `CbrSuggestions`.

### CbrSuggestions (api/)

```java
public record CbrSuggestions(
    Map<String, FeatureStatistics> featureStats,
    double historicalSuccessRate,
    int experienceCount,
    double averageSimilarity
) {
    public static final CbrSuggestions EMPTY =
        new CbrSuggestions(Map.of(), 0.0, 0, 0.0);

    public boolean isEmpty() {
        return experienceCount == 0;
    }
}
```

### FeatureStatistics (api/)

```java
public record FeatureStatistics(
    double min, double max, double median, double p75,
    int sampleCount
) {}
```

**Percentile method:** Median and p75 use **nearest-rank** interpolation — `Math.ceil(rank * sampleCount) - 1` as the index into the sorted values array. Nearest-rank is the simplest correct method and adequate for advisory calibration with small sample counts (typically 3–10 cases). This removes ambiguity in `FeatureStatisticsTest` assertions for odd/even sample counts.

```java
// Example: nearest-rank p75 for [100, 200, 300, 400, 500]
// rank = 0.75, index = ceil(0.75 * 5) - 1 = 3 → value 400
```

Domain-agnostic — any numeric feature (`estimatedCost`, `durationDays`, `followUpDays`, `budget`) gets statistical summaries automatically. The domain specificity lives in:
1. YAML `spec.cbr.features` — defines what features exist per case type
2. Worker system prompts — tells the LLM which feature stats to reference

### Case-Start Integration

`LifeCaseService.startCase()` calls `suggest()` **between Phase 1 and Phase 2** of the three-phase case start pattern (PP-20260529-3ffe28):

```java
public LifeCaseResponse startCase(CreateLifeCaseRequest request) {
    UUID trackerId = UUID.randomUUID();
    try {
        // Phase 1: @Transactional - validate, track, build context
        Map<String, Object> initialContext = prepareAndTrack(trackerId, request);

        // CBR enrichment: between phases, no transaction held.
        // suggest() handles its own errors — returns EMPTY on failure.
        CbrSuggestions suggestions = cbrSuggestionService.suggest(
                request.caseType(), initialContext);
        if (!suggestions.isEmpty()) {
            initialContext.put("cbrCalibration",
                    objectMapper.convertValue(suggestions, Map.class));
        }

        // Phase 2: no transaction - start the case and wait for caseId
        CaseHub caseHub = resolve(request.caseType());
        UUID caseId = caseHub.startCase(initialContext).toCompletableFuture().join();

        // Phase 3: @Transactional - persist caseId, signal into context
        persistCaseId(trackerId, caseId);
        // ...
    }
}
```

**Placement rationale:** The suggest call runs between Phase 1 (`prepareAndTrack()`, `@Transactional`) and Phase 2 (`caseHub.startCase()`, no transaction). This avoids holding a database connection during the Qdrant network call (50–500ms typical), which would extend the Phase 1 transaction window in violation of the platform's short-transaction pattern. The case is already tracked at this point, and the enriched `initialContext` flows directly to the engine.

The calibration data is part of the case file — visible to all bindings and workers, included in `CaseOutcomeEvent.caseFileSnapshot()` for future CBR retention.

**Staleness:** Calibration is computed once at case start. For long-running cases, the calibration reflects the state at start, not current. Acceptable because:
- Structured calibration provides baseline parameters, not real-time signals
- Path 1 (prompt enrichment) provides fresh experiences per worker execution
- Re-calibration on demand is a future enhancement, not a design requirement

---

## YAML inputProjection Updates

Workers that benefit from structured calibration gain `cbrCalibration` in their inputProjection.

**Workers that get `cbrCalibration`:**

| Case Type | Worker | Calibration purpose |
|-----------|--------|---------------------|
| travel-plan | budget-assessment | Cost estimates from historical travel costs |
| travel-plan | booking | Booking decisions informed by historical success rate |
| home-maintenance | get-quotes | Quote assessment against historical cost ranges |
| home-maintenance | schedule-inspection | Timeline from historical maintenance duration |
| contractor-coordination | request-quote | Cost expectations from historical contractor data |
| appointment-cycle | book-appointment | Booking decisions from historical appointment patterns |
| financial-review | analyse-anomalies | Threshold calibration from historical review patterns |
| care-coordination | care-plan | Scheduling from historical care duration |

**Workers that do NOT get `cbrCalibration`:** destination-research, flight-search, hotel-search, confirmation, rebooking, and all sentinel capabilities. These are information gathering or downstream actions where historical calibration adds no value.

**Example inputProjection change** (travel-plan budget-assessment):
```yaml
# Before:
inputSchema: "{ flightResults: .flightResults, hotelResults: .hotelResults }"
# After:
inputSchema: "{ flightResults: .flightResults, hotelResults: .hotelResults, cbrCalibration: .cbrCalibration }"
```

---

## System Prompt Updates

Two layers:

### Layer 1: Generic CBR suffix (all workers)

Applied via `CBR_SYSTEM_PROMPT_SUFFIX` in `agentWorker()`. Handles `_cbrContext` from Path 1. See §Path 1 above.

### Layer 2: Calibration-specific instruction (workers receiving cbrCalibration)

Added to the worker's own system prompt. Domain-specific guidance on which feature stats to reference.

**Example — travel-plan budget-assessment:**
```
You are a travel planning agent. Assess the total travel budget
and determine if approval is required.
If cbrCalibration is provided, use featureStats.budget for typical cost
ranges and historicalSuccessRate to gauge risk. Adjust requiresApproval
thresholds accordingly — if similar trips consistently completed under
budget, lower the approval threshold.
```

**Example — contractor-coordination quote-request:**
```
You are a contractor coordination agent. Request quotes for the described work.
If cbrCalibration is provided, use featureStats.estimatedCost for typical
cost ranges in similar jobs. Flag quotes that fall outside the historical
p75 range as potentially over-priced.
```

The calibration instruction is baked into the system prompt at construction time. When `cbrCalibration` is absent from input (empty CBR store, insufficient similar cases), the LLM ignores the instruction gracefully.

---

## Testing Strategy

### Unit Tests

| Test Class | What it covers |
|-----------|---------------|
| `LifeCbrExperienceFormatterTest` | Formatting with 0, 1, N experiences; sorting by similarity; max cap; null/empty field handling; featureSimilarities rendering |
| `CbrInputTransformerTest` | Transformer with experiences present (merges `_cbrContext`); no `WorkerExecutionContext` (pass-through); empty experiences list (pass-through); null context (pass-through) |
| `LifeCbrFeatureExtractorTest` | Registry lookup, CbrConfig null/absent, JQ evaluation, FeatureValue conversion, LambdaFeatureExtractor rejection, empty features (all JQ expressions null) |
| `LifeCbrSuggestionServiceTest` | Mock `CbrCaseMemoryStore` and `LifeCbrFeatureExtractor`. Query construction from CbrConfig (builder pattern with weights, minSimilarity, vectorWeight), `FeatureStatistics` computation (min/max/median/p75 nearest-rank), success rate calculation, `EMPTY` for <2 cases, non-numeric features excluded, graceful degradation on exception |
| `CbrSuggestionsTest` | Record construction, `isEmpty()`, `EMPTY` constant, edge cases (single sample, all same values) |
| `FeatureStatisticsTest` | Median/p75 computation with odd/even sample counts, single-element, all-equal values |

### Integration Tests (`@QuarkusTest`)

| Test Class | What it covers |
|-----------|---------------|
| `LifeTypedCaseHubCbrIntegrationTest` | Agents built by `agentWorker()` have `inputTransformer` registered. Start a case, verify `cbrCalibration` appears on case context when experiences exist in the store |
| `LifeCbrSuggestionServiceIntegrationTest` | End-to-end: seed CBR store with known cases, call `suggest()`, verify statistics match expected values. Uses real `JQEvaluator` and `CaseDefinitionRegistry` |

### Existing Test Updates

`LifeCaseOutcomeCbrWriterTest` and `LifeRoutingOutcomeRecorderTest` currently verify feature extraction inline. After the `LifeCbrFeatureExtractor` refactoring, these tests should mock `LifeCbrFeatureExtractor` and verify `extract()` is called with the correct `caseType` and `JsonNode` arguments. Feature extraction logic itself is now covered by `LifeCbrFeatureExtractorTest` — the retention writer tests verify delegation and correct use of the returned `ExtractionResult`.

### Test Infrastructure

- `InMemoryCbrCaseMemoryStore` (`@Alternative @Priority(2)`) already active from Layer 8
- Seed test experiences via `store()` in `@BeforeEach`
- `TestLifeOpenClawChatModelFactory` handles canned responses — system prompt suffix is transparent

### Not Tested Here

- Engine's `WorkerContext.experiences()` population — engine#707 responsibility
- End-to-end CBR round-trip (retain → retrieve → calibrate → influence worker output) — requires real similarity scoring; deferred until Qdrant is configured

---

## Files Changed

**New files:**
- `api/src/main/java/io/casehub/life/api/CbrSuggestions.java`
- `api/src/main/java/io/casehub/life/api/FeatureStatistics.java`
- `app/src/main/java/io/casehub/life/app/cbr/LifeCbrFeatureExtractor.java`
- `app/src/main/java/io/casehub/life/app/cbr/LifeCbrExperienceFormatter.java`
- `app/src/main/java/io/casehub/life/app/cbr/CbrInputTransformer.java`
- `app/src/main/java/io/casehub/life/app/cbr/LifeCbrSuggestionService.java`
- Tests: 6 unit test classes + 2 integration test classes

**Modified files:**
- `app/src/main/java/io/casehub/life/app/engine/LifeTypedCaseHub.java` — inject formatter, create transformer, update `agentWorker()`
- `app/src/main/java/io/casehub/life/app/engine/LifeCaseService.java` — call `suggest()` between Phase 1 and Phase 2
- `app/src/main/java/io/casehub/life/app/cbr/LifeCaseOutcomeCbrWriter.java` — replace `extractFeatures()` with `LifeCbrFeatureExtractor` call
- `app/src/main/java/io/casehub/life/app/cbr/LifeRoutingOutcomeRecorder.java` — replace `extractFeatures()` with `LifeCbrFeatureExtractor` call
- 6 CaseHub subclasses — updated system prompts with calibration instructions
- 6 YAML case definitions — inputProjection updates for ~8 capabilities
- `CLAUDE.md` — Layer 8 status update

---

## Design Decisions

| Decision | Rationale |
|---------|-----------|
| `inputTransformer` over custom `WorkerFunction` | `Agent` is final, `SyncAgentWorkerFunctionHandler.supports()` only recognizes `Sync` and `AgentWorkerFunction`. inputTransformer is a first-class AgentBuilder hook that runs in WorkerExecutionContext scope. |
| Domain-agnostic `FeatureStatistics` over per-domain extractors | YAML `spec.cbr.features` already defines features per case type. Statistical summaries are generic. Domain specificity lives in system prompts. |
| CBR suffix always present in system prompt | No conditional prompt construction. LLM handles absent `_cbrContext` gracefully. Simpler than lazy agent construction. |
| Minimum 2 cases for statistics | Single-case statistics have no variance information. Returns `EMPTY` to signal insufficient data. |
| No sentinel CBR enrichment via Path 1 | Sentinels run outside engine worker execution pipeline — no `WorkerExecutionContext`. They see `cbrCalibration` via case context (Path 2). |
| `CbrSuggestions` in `api/` not `app/` | Pure record with no framework imports. May be referenced by cross-module consumers or tests. |
