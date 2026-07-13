# API Enhancements Design — #62, #63, #64

Three read-side API features for casehub-life: ExternalActor search/trust/activity (#62), pending actions surface (#63), and case outcome analytics (#64).

## Motivation

Life has 8 layers of infrastructure writing rich operational data (WorkItems, ledger entries, CBR cases, trust scores, case tracker records) but almost no read-side API. The ExternalActor API is CRUD-only with no search. LifeCaseResource is POST-only. There's no way to ask "what needs my attention?" or "how is the system performing?"

These three features fill that gap without new entities or migrations — they query existing data stores.

## API Structure

Three concerns, three natural homes:

| Concern | Resource | Path prefix |
|---------|----------|-------------|
| Entity queries | `ExternalActorResource` (extended) | `/external-actors` |
| Operational state | `PendingActionsResource` (new) | `/pending-actions` |
| Historical analysis | `LifeAnalyticsResource` (new) | `/analytics` |

## 1. ExternalActor Enhancements (#62)

### Search and pagination — `GET /external-actors`

Extends the existing endpoint. Auth change: GET endpoints (list, get, search) add `JUNIOR` for view-only access, consistent with pending actions visibility. Write endpoints (create, update, delete) remain ADMIN/MEMBER only.

New query parameters:

| Param | Type | Description |
|-------|------|-------------|
| `actorType` | LifeActorType | Existing — exact match filter |
| `name` | String | Case-insensitive substring match |
| `contactMethod` | String | Exact match filter |
| `erasedOnly` | boolean | Filter to GDPR-erased actors only |
| `page` | int | 0-based page index, default 0 |
| `size` | int | Page size, default 20, max 100 |

Returns `PagedResponse<ExternalActorResponse>`.

Uses Panache `find().page(Page.of(page, size))` — not `list()` which ignores query limits (GE-20260523-06e8b6).

### Trust history — `GET /external-actors/{id}/trust-history`

Queries `LedgerAttestation` entries via the existing `findBySubjectId` named query, using the actor's raw UUID as `subjectId` (not the `life-actor:{uuid}` prefixed format — that's `LedgerEntry.actorId`, a different field on a different entity). The `ExternalActorHistoryService` accesses ledger entities through the qhorus persistence unit EntityManager, since `LedgerEntryRepository` does not expose an attestation-by-subject query.

| Param | Type | Description |
|-------|------|-------------|
| `page` | int | 0-based page index, default 0 |
| `size` | int | Page size, default 20, max 100 |

Response item — `TrustHistoryEntry` (api/ record):

| Field | Type | Description |
|-------|------|-------------|
| occurredAt | Instant | When the attestation was recorded |
| capabilityTag | String | Which capability was attested |
| dimension | String | Trust dimension — nullable, absent on verdict-only attestations |
| score | Double | Dimension score — nullable, absent on verdict-only attestations |
| verdict | String | AttestationVerdict value (SOUND, FLAGGED, ENDORSED, or CHALLENGED) |

Verdict-only attestations (no dimension score) and dimension attestations (with score and dimension) are both returned. Consumers should handle null `score`/`dimension` for verdict-only records.

Returns `PagedResponse<TrustHistoryEntry>`. Returns 404 if actor not found.

### Activity timeline — `GET /external-actors/{id}/activity`

Joins `LifeTaskContext` (by `externalActorId`) → `WorkItem` (by `workItemId`). Most-recent-first.

| Param | Type | Description |
|-------|------|-------------|
| `page` | int | 0-based page index, default 0 |
| `size` | int | Page size, default 20, max 100 |

Response item — `ActorActivityEntry` (api/ record):

| Field | Type | Description |
|-------|------|-------------|
| workItemId | UUID | The WorkItem ID |
| title | String | WorkItem title |
| domain | LifeDomain | From LifeTaskContext |
| status | String | WorkItem status |
| scope | String | WorkItem scope path |
| createdAt | Instant | When the work was created |
| completedAt | Instant | When it completed (nullable) |
| outcome | String | WorkItem outcome (nullable) |

Returns `PagedResponse<ActorActivityEntry>`. Returns 404 if actor not found.

### Service: `ExternalActorHistoryService`

New service in `app/service/`. Owns trust history queries (ledger) and activity timeline queries (LifeTaskContext → WorkItem join). Keeps this logic out of `ExternalActorService`.

## 2. Pending Actions (#63)

### `GET /pending-actions`

Queries WorkItems with `scope LIKE 'casehubio/life/%'` and actionable status (PENDING, ASSIGNED, IN_PROGRESS, DELEGATED). SUSPENDED items are excluded — they are intentionally paused and not actionable.

| Param | Type | Description |
|-------|------|-------------|
| `domain` | LifeDomain | Filter by scope path segment |
| `candidateGroup` | String | Filter by candidateGroups substring |
| `dueSoonHours` | int | Hours before deadline to classify as DUE_SOON, default 24 |
| `page` | int | 0-based, default 0 |
| `size` | int | Default 20, max 100 |

Default sort: OVERDUE first (most overdue at top), then DUE_SOON (nearest deadline first), then NORMAL/NO_DEADLINE by creation date.

Response item — `PendingActionResponse` (api/ record):

| Field | Type | Description |
|-------|------|-------------|
| workItemId | UUID | |
| title | String | |
| description | String | |
| status | String | WorkItem status |
| domain | LifeDomain | From LifeTaskContext or derived from scope |
| candidateGroups | String | Who can act on this |
| createdAt | Instant | |
| expiresAt | Instant | SLA deadline (nullable) |
| urgency | Urgency | Computed classification |
| daysOverdue | Long | Days past expiresAt, null if not overdue |

Returns `PagedResponse<PendingActionResponse>`.

### Urgency enum (api/)

| Value | Condition |
|-------|-----------|
| `OVERDUE` | `expiresAt < now` and status is actionable |
| `DUE_SOON` | `expiresAt` within `dueSoonHours` window |
| `NORMAL` | Actionable, deadline beyond `dueSoonHours` |
| `NO_DEADLINE` | No `expiresAt` set |

### Domain resolution

If a WorkItem has a `LifeTaskContext` record, use its `domain` field directly. Otherwise derive from scope path: `casehubio/life/household` → `HOUSEHOLD`. Scope paths use lowercase; `LifeDomain` values are uppercase.

### Service: `PendingActionsService`

New service in `app/service/`. Owns WorkItem query, urgency computation, domain enrichment from LifeTaskContext. Auth: `@RolesAllowed({ADMIN, MEMBER, JUNIOR})`.

### Sentinel escalations

Sentinel escalations that need human attention are already WorkItems — the case plan's `sentinel-escalation` binding creates a human task when `sentinelReport.escalationRequired == true`. These appear naturally in the pending actions query. No separate sentinel alert table or engine context queries needed.

## 3. Case Outcome Analytics (#64)

### `GET /analytics/cases`

Case statistics grouped by LifeCaseType. Queries `LifeCaseTracker`.

**Dependency:** `LifeCaseTracker.status` is currently only set to COMPLETED by `LifeCaseTrackerObserver`; FAILED is never set because the observer does not handle failure events from the engine. A prerequisite fix (filed as issue) must extend the observer to handle `CaseFailed`/`CaseTerminated` event types. Until then, `failed` will be 0 and `completionRate` will always be 1.0.

Optional `caseType` query param to filter to a single type.

Response — `CaseStatisticsResponse`:

| Field | Type | Description |
|-------|------|-------------|
| entries | List\<CaseTypeStats\> | Per-type statistics |

`CaseTypeStats` record:

| Field | Type | Description |
|-------|------|-------------|
| caseType | String | LifeCaseType case name |
| total | long | Total cases of this type |
| active | long | Currently active |
| completed | long | Successfully completed |
| failed | long | Failed |
| avgResolutionHours | Double | Avg completedAt - createdAt for COMPLETED (nullable) |
| p50ResolutionHours | Double | Median resolution time for COMPLETED (nullable, native query) |
| p95ResolutionHours | Double | 95th percentile resolution time for COMPLETED (nullable, native query) |
| completionRate | Double | completed / (completed + failed), nullable if no terminal cases |

Percentile metrics use native SQL `PERCENTILE_CONT` (supported by both H2 and PostgreSQL). JPQL does not support window function percentiles, so `LifeAnalyticsService` uses `getEntityManager().createNativeQuery()` for these.

### `GET /analytics/sla`

SLA compliance metrics. Queries WorkItems with `scope LIKE 'casehubio/life/%'` that have `expiresAt` set.

Optional `domain` query param.

Response — `SlaComplianceResponse`:

| Field | Type | Description |
|-------|------|-------------|
| entries | List\<DomainSlaStats\> | Per-domain statistics |

`DomainSlaStats` record:

| Field | Type | Description |
|-------|------|-------------|
| domain | String | Domain name |
| totalWithSla | long | WorkItems with expiresAt |
| breachedCount | long | Items where SLA was missed: `completedAt > expiresAt` (completed late), or terminal status EXPIRED/ESCALATED, or `expiresAt < now` and still actionable (currently overdue) |
| complianceRate | Double | (total - breached) / total |
| avgBreachLatencyHours | Double | Avg `completedAt - expiresAt` for items completed after their deadline — measures how late breaches are, not the SLA window duration (nullable) |

### `GET /analytics/trust`

Trust score aggregates across all non-erased ExternalActors.

Aggregation strategy: query non-erased `ExternalActor` IDs, build `life-actor:{uuid}` actor IDs via `LifeActorIds.of()`, then batch-load `ActorTrustScore` entries via custom JPQL on the qhorus PU EntityManager. `ActorTrustScoreRepository` does not expose the needed batch queries (its `findCapabilityScoresByActorIds` filters by a specific `capabilityKey`, not suitable for global/dimension aggregation). Two custom queries:

1. **Global scores:** `SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey IS NULL`
2. **Dimension scores:** `SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.dimensionKey IS NOT NULL`

This follows the same qhorus EntityManager pattern established for trust history queries. Compute averages in Java. Total: 3 queries (1 for actors, 2 for scores) regardless of actor count, not 3N.

Response — `TrustAnalyticsResponse`:

| Field | Type | Description |
|-------|------|-------------|
| actorCount | int | Non-erased actors with scores |
| avgGlobalScore | Double | Average global trust score (nullable) |
| dimensionAverages | Map\<String, Double\> | Average per dimension |
| lowestScoreActors | List\<ActorScoreSummary\> | Bottom 5 by global score |

`ActorScoreSummary`: `{actorId: UUID, name: String, globalScore: Double}`.

### Service: `LifeAnalyticsService`

New service in `app/service/`. Owns all three analytics query methods. No domain switch statements — queries aggregate by scope path string or caseType string. Analytics are read-only — no `@Transactional`.

Auth: `@RolesAllowed({ADMIN, MEMBER})`. Juniors cannot see analytics.

### Deferred: CBR store summary

Issue #64 includes a CBR store summary requirement (entry count by domain, feature distribution preview). This is deferred to a follow-up issue — `CbrCaseMemoryStore` lives in `casehub-neocortex-memory-api` with an opaque persistence model (retrieval-oriented API, not analytics-oriented), and its storage representation varies across implementations (JPA, in-memory, vector DB). CBR analytics requires its own investigation into data access patterns and a separate design spec.

## Cross-Cutting

### Pagination — `PagedResponse<T>`

Generic pagination wrapper in `api/`:

```java
public record PagedResponse<T>(List<T> items, int page, int size, long totalCount) {}
```

Reused across all paginated endpoints.

### Module placement

| What | Where |
|------|-------|
| Response records, Urgency enum, PagedResponse | `api/` |
| Resources, services | `app/` |
| New entities or migrations | None needed |

### REST conventions

Per established protocols (PP-20260526-d0b921):
- `@Blocking @ApplicationScoped` on all resources
- Class-level `@Produces/@Consumes(APPLICATION_JSON)`
- `@RolesAllowed` on every endpoint
- `@Transactional` on paginated service methods (count + page must share a transaction for consistency) and write methods. Analytics aggregate queries (single-query aggregates, no count+page split) do not require `@Transactional`

### Relevant garden entries

- GE-20260518-e4fa52: RESTEasy Reactive endpoints calling `.await()` need `@Blocking`
- GE-20260523-06e8b6: Panache `list()` ignores query limit — use `find().page()` for pagination

### Protocol constraints

- `descriptor-handler-no-domain-switches`: No switch/if-else on LifeDomain in services. Domain resolution from scope paths uses mapping, not switches.

## Testing

- **Unit tests**: Urgency classification, domain extraction from scope paths, compliance rate computation, resolution time averaging
- **Integration tests** (`@QuarkusTest`): Each endpoint — happy path, filters, pagination boundaries, empty results, 404 on unknown actor, auth role enforcement
- **Seed data**: Via `LifeTestFixtures` — WorkItems, LifeTaskContext, LifeCaseTracker, ExternalActor records with varied statuses and domains
