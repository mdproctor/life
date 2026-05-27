# casehub-life Layer 2 — casehub-work SLA enforcement

**Branch:** `issue-3-layer2-casehub-work`  
**Issues:** life#3 (Layer 2 SLA), life#12 (DTO layer), life#13 (scope fix)  
**Date:** 2026-05-27  
**Rev:** 2 — post-review corrections

---

## Context and design pivot

Brainstorming surfaced a design correction to Layer 1: `HouseholdTask`, `LifeGoal`, and
`LifeEvent` duplicated foundation primitives (parent#79, PP-20260527-da1f66). This branch
corrects Layer 1 and builds Layer 2 on the corrected model.

**casehub-life positioning:** headless personal accountability application. Primary
interaction through existing tools (calendar, messaging, voice). Dedicated UI surfaces
(dashboards, oversight consoles) where governance requires them.

---

## Domain model (corrected)

### Entities owned by casehub-life

**`ExternalActor`** (retained, unchanged) — typed tracked external party with contact
details and trust dimensions. Cross-case; lifecycle independent of any single WorkItem or
case.

**`LifeTaskContext`** (new) — thin domain context entity linking a foundation `WorkItem`
to life-domain metadata that has no foundation home. Not a WorkItem wrapper: carries only
fields the foundation cannot represent. Does NOT duplicate title, deadline, slaHours,
status, or assignedTo — those remain in WorkItem.

| Field | Type | Notes |
|---|---|---|
| `workItemId` | `UUID @Id` | FK to foundation WorkItem (raw UUID — cross-schema, no JPA relation) |
| `domain` | `LifeDomain` | Life domain enum for queryable domain filtering |
| `externalActorId` | `UUID` | FK to `external_actor.id` — JPA relation within casehub-life schema |
| `recurrence` | `String` | Nullable cron expression; populated when template gains `cronExpression` support |

Distinction from PP-20260527-da1f66 supplement threshold: four domain-specific fields
with query requirements warrant a named entity rather than a supplement join table.
`LifeTaskContext` IS the supplement pattern correctly applied at four-field scale.

**`WorkItemTemplate` seeds** — domain vocabulary: named configurations of casehub-work's
`WorkItemTemplate` entity. Seeded as data, not modelled as entities.

**`CasePlanModel` YAMLs** — domain orchestration (Layer 5).

**`LifeSlaBreachPolicy`** — `@ApplicationScoped` SPI implementation.

### Foundation primitive mapping

| Removed entity | Foundation equivalent | Field mapping |
|---|---|---|
| `HouseholdTask` | `WorkItem` + `LifeTaskContext` | All fields mapped — see below |
| `LifeGoal` | `CaseInstance` | domain/title/description → context; targetDate → caseBudgetDeadline; status lifecycle maps directly (PAUSED→SUSPENDED, ABANDONED→CANCELLED) |
| `LifeEvent` | completed `CaseInstance` + `CaseLedgerEntry` | occurredAt = case completion time |

**HouseholdTask field mapping (complete):**

| HouseholdTask field | Maps to | Notes |
|---|---|---|
| `id` | `WorkItem.id` | |
| `domain` | `LifeTaskContext.domain` | First-class queryable field |
| `title` | `WorkItem.title` | |
| `description` | `WorkItem.description` | |
| `deadline` | `WorkItem.claimDeadline` | |
| `slaHours` | `WorkItemTemplate.default_expiry_hours` | Template-level default |
| `status` (PENDING/IN_PROGRESS/COMPLETED/CANCELLED) | `WorkItem.status` | All four present in WorkItem's 10-status set |
| `assignedTo` | `WorkItem.assigneeId` | |
| `externalActorId` | `LifeTaskContext.externalActorId` | |
| `recurrence` | `LifeTaskContext.recurrence` | Nullable; populated when casehub-work adds cronExpression support |
| `createdAt` | `WorkItem.createdAt` | |
| `updatedAt` | `WorkItem.updatedAt` | |

One planned gap: `recurrence` captured in `LifeTaskContext` pending `WorkItemTemplate.cronExpression` in casehub-work.

---

## Changes in this branch

### Part 1 — Layer 1 redesign

**Files to delete:**
- `app/src/main/java/io/casehub/life/app/entity/HouseholdTask.java`
- `app/src/main/java/io/casehub/life/app/entity/LifeGoal.java`
- `app/src/main/java/io/casehub/life/app/entity/LifeEvent.java`
- `app/src/main/java/io/casehub/life/app/service/HouseholdTaskService.java`
- `app/src/main/java/io/casehub/life/app/service/LifeGoalService.java`
- `app/src/main/java/io/casehub/life/app/service/LifeEventService.java`
- `app/src/main/java/io/casehub/life/app/resource/HouseholdTaskResource.java`
- `app/src/main/java/io/casehub/life/app/resource/LifeGoalResource.java`
- `app/src/main/java/io/casehub/life/app/resource/LifeEventResource.java`
- `app/src/test/java/io/casehub/life/app/HouseholdTaskResourceTest.java`
- `app/src/test/java/io/casehub/life/app/LifeGoalResourceTest.java`
- `app/src/test/java/io/casehub/life/app/LifeEventResourceTest.java`
- `app/src/test/java/io/casehub/life/app/ShowcaseScenarioTest.java` (full rewrite — see Part 4e)
- `api/src/main/java/io/casehub/life/api/model/HouseholdTaskStatus.java`
- `api/src/main/java/io/casehub/life/api/model/LifeGoalStatus.java`
- `api/src/test/java/io/casehub/life/api/model/HouseholdTaskStatusTest.java`
- `app/src/main/resources/db/migration/V101__create_household_task.sql`
- `app/src/main/resources/db/migration/V102__create_life_goal.sql`
- `app/src/main/resources/db/migration/V103__create_life_event.sql`

Migrations V101–V103 are removed from source (no production deployments exist). Their
tables are not created on fresh install once removed — correct. The Flyway path rename
(Part 4a) resets the migration history location, making removal clean.

**`ExternalActorService` changes:**
- `delete()` guard: `HouseholdTask.count("externalActorId", id)` → `LifeTaskContext.count("externalActorId", id)`
- `listTasks()`: return type changes from `List<HouseholdTask>` to `List<LifeTaskContext>` (or removed if scope changes — see Part 4d)
- `ExternalActorResource.listTasks()`: return type follows service

**LAYER-LOG.md:** Layer 1 entry describes the pre-redesign baseline (HouseholdTask, LifeGoal,
LifeEvent). Update the entry to note that these entities were removed in Layer 2 and link to
this spec for rationale. The Layer 1 teaching objective (showing accountability gaps in a naive
domain model) remains valid — the gaps existed and the entities were the vehicle to show them.

### Part 2 — `ExternalActor` DTO records (life#12 rescoped)

**`io.casehub.life.api.request`:**
- `CreateExternalActorRequest` — `@NotNull name`, `@NotNull actorType (LifeActorType)`, `@NotNull contactMethod`, `@NotNull contactValue`
- `UpdateExternalActorRequest` — same four fields

**`io.casehub.life.api.response`:**
- `ExternalActorResponse` — `id (UUID)`, `name`, `actorType`, `contactMethod`, `contactValue`, `createdAt (Instant)`

`@NotNull` moves from JPA entity to request records. `ExternalActorService` accepts
request records and returns response records. Entity not exposed beyond service
(PP-20260512-9b8847).

### Part 3 — engine-persistence-memory scope fix (life#13)

- `app/pom.xml`: `casehub-engine-persistence-memory` → `<scope>test</scope>`
- `app/src/main/resources/application.properties`: remove `MemoryPlanItemStore` and
  `MemorySubCaseGroupRepository` from `quarkus.arc.selected-alternatives`
- Production uses `@DefaultBean` no-ops from engine `blackboard` module. Verify at
  implementation that `@DefaultBean PlanItemStore` and `@DefaultBean SubCaseGroupRepository`
  exist in casehub-engine-blackboard before removing the production alternatives.

### Part 4 — Layer 2: casehub-work SLA enforcement (life#3)

**4a. Flyway path fix (PP-20260525-607b33)**

- Rename `src/main/resources/db/migration/` → `src/main/resources/db/life/migration/`
- Only `V100__create_external_actor.sql` moves (V101–V103 deleted in Part 1)
- Production `application.properties`:
  ```
  quarkus.flyway.locations=classpath:db/life/migration,classpath:db/work/migration
  ```
  Flyway sorts all migrations by version number across locations: casehub-work occupies
  V1–V31, casehub-life domain starts at V100. work_item_template (V5) exists before
  life seed (V102) runs — not because of path order but because 5 < 102.
- Test config: Flyway still disabled (drop-and-create); no test change needed.

**4b. `LifeTaskContext` entity and migration**

`V101__life_task_context.sql`:
```sql
CREATE TABLE life_task_context (
    work_item_id     UUID         NOT NULL,
    domain           VARCHAR(50)  NOT NULL,
    external_actor_id UUID,
    recurrence       VARCHAR(100),
    CONSTRAINT pk_life_task_context PRIMARY KEY (work_item_id),
    CONSTRAINT fk_ltc_external_actor
        FOREIGN KEY (external_actor_id) REFERENCES external_actor(id)
);

CREATE INDEX idx_ltc_external_actor ON life_task_context (external_actor_id);
```

`work_item_id` is not a FK to casehub-work (cross-schema; raw UUID per design, consistent
with `externalActorId` on Layer 1 HouseholdTask). `external_actor_id` is a JPA FK within
the casehub-life schema.

JPA entity `LifeTaskContext` in `app/entity/`: maps above columns with `@Id workItemId`,
`@Enumerated(STRING) domain`, nullable `externalActorId`, nullable `recurrence`.

**4c. WorkItemTemplate seeds**

`V102__life_workitem_template_seeds.sql` — inserts into casehub-work's `work_item_template`
table (V5 schema). Exact columns from V5 schema:

```sql
INSERT INTO work_item_template
    (id, name, description, category, priority, candidate_groups,
     default_expiry_hours, created_by, created_at)
VALUES
    (gen_random_uuid(), 'household-task', 'Routine household coordination task',
     'household', 'NORMAL', 'household-member', 24, 'life-system', now()),
    (gen_random_uuid(), 'health-appointment', 'Health appointment or follow-up',
     'health', 'NORMAL', 'household-member', 48, 'life-system', now()),
    (gen_random_uuid(), 'contractor-coordination', 'Contractor task with commitment tracking',
     'contractor', 'NORMAL', 'household-member', 72, 'life-system', now());
```

H2 compatibility: `gen_random_uuid()` is available in `MODE=PostgreSQL`. `now()` is
standard. No dialect-specific types used.

**4d. `POST /life-tasks` resource and service**

`CreateLifeTaskRequest` in `api/request`:
- `@NotNull templateRef (String)` — must be a known life-domain template name
- `@NotNull title (String)`
- `externalActorId (UUID)` — optional
- `deadline (Instant)` — optional; overrides template default_expiry_hours
- NO `candidateGroups` — groups come from the template exclusively. Callers cannot
  override routing groups; doing so would break tier detection in `LifeSlaBreachPolicy`.

`LifeTaskResponse` in `api/response`:
- `workItemId (UUID)`, `templateRef (String)`, `domain (LifeDomain)`, `status (String)`,
  `externalActorId (UUID)` nullable, `createdAt (Instant)`

`LifeTaskService.create()` (`@Transactional`, `@ApplicationScoped`):
1. Resolve template by name — 422 if not found
2. Validate `externalActorId` exists if provided — 422 if not
3. Resolve `candidateGroups` from template (`candidate_groups` column) — validate non-null
   before calling `WorkItemService.create()` (GE-20260522-4e806e)
4. Compute `claimDeadline`: `deadline` if provided, else `now + template.default_expiry_hours`
5. Create `WorkItem` via `WorkItemService.create()` with:
   - `callerRef: "life:task/{templateRef}"` (correlates WorkItem to life context without supplement join)
   - `scope: "life"` (fixed scope string avoids `Path.root()` — GE-20260522-9cd6d5)
   - `candidateGroups`: from template
   - `claimDeadline`: computed above
6. Create `LifeTaskContext` (workItemId from step 5, domain derived from template category,
   externalActorId if provided). Verify `WorkItemService.create()` uses `@Transactional
   (TxType.REQUIRED)` at implementation — atomicity of steps 5+6 depends on it.
7. Return `LifeTaskResponse`

`ExternalActorService.listTasks(UUID actorId)` — updated to return `List<LifeTaskContext>`.
`ExternalActorResource.GET /{id}/tasks` — returns list of `LifeTaskContext` entries with
their associated `workItemId`. Callers can use the `workItemId` to fetch full WorkItem
details from casehub-work directly.

**4e. `LifeSlaBreachPolicy`**

```java
@ApplicationScoped
public class LifeSlaBreachPolicy implements SlaBreachPolicy {
    @Override
    public BreachDecision onBreach(SlaBreachContext ctx) {
        // Tier 2 detected: EscalateTo previously added household-admin to candidateGroups
        if (ctx.task().candidateGroups().contains("household-admin")) {
            return new BreachDecision.Fail("life-sla-exhausted");
        }
        // Tier 1: escalate to household-admin.
        // 48h extension is a Layer 2 constant; a production policy would derive
        // the escalation deadline from the original template's default_expiry_hours.
        return EscalateTo.to("household-admin").withDeadline(Duration.ofHours(48));
    }
}
```

Tier detection is safe because `CreateLifeTaskRequest` forbids `candidateGroups` overrides —
the only way `household-admin` appears in `candidateGroups` is if `EscalateTo` put it there.

**4f. Test strategy — SLA enforcement**

Remove `io.casehub.work.runtime.service.ExpiryLifecycleService` from
`quarkus.arc.exclude-types` in test `application.properties`. Keep scheduler jobs
excluded (`ExpiryCleanupJob`, `ClaimDeadlineJob`, `RoutingCursorCleanupJob`).

In `@QuarkusTest` Layer 2 tests, inject `ExpiryLifecycleService` and call
`checkExpired()` directly after creating a WorkItem with `claimDeadline` in the past.
This demonstrates SLA enforcement without scheduler interference.

**4g. ShowcaseScenarioTest rewrite**

Full rewrite of `ShowcaseScenarioTest`. Layer 2 narrative shows the same household week
as Layer 1 but with gaps closed:

| Order | Test | Demonstrates |
|---|---|---|
| 1 | Create ExternalActor (Bob's Plumbing) | Actor registry unchanged |
| 2 | `POST /life-tasks` (contractor-coordination template, externalActorId=Bob) | WorkItem created, LifeTaskContext created, callerRef present |
| 3 | Verify `GET /external-actors/{id}/tasks` returns the LifeTaskContext entry | Cross-entity correlation via supplement |
| 4 | Create health-appointment task with past deadline | WorkItem created with past claimDeadline |
| 5 | Call `expiryLifecycleService.checkExpired()` directly | SLA breach fires, `LifeSlaBreachPolicy.onBreach()` called |
| 6 | Verify WorkItem status is EXPIRED, candidateGroups updated to include household-admin | Escalation recorded in WorkItem state |
| 7 | Call `checkExpired()` again on the escalated item | Second breach → Fail("life-sla-exhausted") |
| 8 | Contrast: assert no SLA breach audit without casehub-work — show the gap comment | Layer 1 gap referenced |

**Unit tests (no Quarkus):**

`LifeSlaBreachPolicyTest` — pure JUnit 5:
- First breach (candidateGroups = ["household-member"]) → `EscalateTo.to("household-admin")`
- Second breach (candidateGroups includes "household-admin") → `Fail("life-sla-exhausted")`
- Second breach (candidateGroups = ["household-admin", "household-member"]) → same `Fail`

`LifeTaskServiceTest` — unit:
- Unknown templateRef → 422
- Non-existent externalActorId → 422
- Null candidateGroups from template → rejected before WorkItemService.create() (GE-20260522-4e806e guard)

---

## Protocols applied

| Protocol | Applies to |
|---|---|
| PP-20260527-da1f66 | LifeTaskContext as domain context entity (four fields exceed supplement threshold) |
| PP-20260525-607b33 | Flyway path renamed to db/life/migration |
| PP-20260512-9b8847 | ExternalActor DTOs in api/, mapping in service |
| PP-20260512-module-tiers | api/ stays pure Java |
| PP-20260526-d0b921 | @Blocking @ApplicationScoped on all resources |
| PP-20260526-75d9c9 | @Transactional on service methods only |
| GE-20260511-3e5a75 | candidateGroups + claimDeadline pattern |
| GE-20260522-f7db12 | Stateless two-tier escalation via candidateGroups |
| GE-20260522-4e806e | Non-empty groups enforced before WorkItemService.create() |
| GE-20260522-9cd6d5 | Fixed scope string "life" avoids Path.root() |

---

## Deferred

| Item | Tracking |
|---|---|
| `WorkItemTemplate.cronExpression` (recurrence support) | casehub-work backlog |
| WorkItemLifecycleEvent → LifeTaskContext status sync | Layer 3 or follow-on |
| Flyway in tests (PostgreSQL dialect validation) | separate issue |
| `/life-goals` (CaseInstance facade), `/life-events` (ledger facade) | Layer 5 |
| Escalation deadline derived from template SLA (production policy) | follow-on to LifeSlaBreachPolicy |
