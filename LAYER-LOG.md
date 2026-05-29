# casehub-life Agentic Harness — Layer Log

Structured record of what was built at each integration layer, optimised for LLM consumption.
Each entry is the raw material needed to reproduce the layer in a different domain harness.
Entries are ordered for learning, not chronology. Each entry is complete when the layer closes.

Cross-references:
- Platform compliance gap analysis: `docs/specs/life-automation.md`
- Actor model: `docs/specs/life-actor-model.md`
- Tutorial teaching objectives: `../parent/docs/tutorial-strategy.md`
- AML reference implementation: `../aml/LAYER-LOG.md`
- Clinical reference implementation: `../clinical/LAYER-LOG.md`
- Research spec: `../parent/docs/specs/2026-05-25-openclaw-casehub-integration.md`

**Architectural note — hexagonal pattern:** casehub-life uses the AML api/app split:
- `api/` — pure Java, zero framework imports, zero JPA. Domain records and constants.
- `app/` — Quarkus application: Panache entities, REST resources, Flyway, foundation wiring.

**Strategic positioning note:** casehub-life is a developer showcase in the devtown/clinical
tradition — integration layers by foundation module adoption sequence. The open question (spec §5.8)
of consumer product vs developer showcase is noted and does not foreclose either direction.

---

## Layer 1 — Domain baseline (no CaseHub foundation)

> **Redesign note (2026-05-27):** The Layer 1 entities `HouseholdTask`, `LifeGoal`, and
> `LifeEvent` were removed in Layer 2 — they duplicated foundation primitives (see
> `docs/specs/2026-05-27-layer2-casehub-work-sla.md` §Context and design pivot and parent#79).
> This entry documents the original Layer 1 baseline and the accountability gaps it showed.
> The Layer 2 spec describes the corrected domain model.

**Status:** Complete
**Issue:** casehubio/life#2
**Navigation:** `git log --grep="#2" --oneline`
→ c43e2cf feat(#2): Layer 1 domain baseline — entities, REST, tests, migrations
→ 9fa2146 docs(#2): Layer 1 domain baseline spec
→ 38f3efb docs(#2): update LAYER-LOG.md — domain baseline terminology, accountability gaps table, navigation lines

### What it shows

Household domain model with no CaseHub foundation modules. Core entities in `app/`
(Quarkus Panache) and domain constants in `api/` (pure Java). A REST API that persists
household tasks, goals, events, and external actors directly — no accountability, SLA, or
obligation tracking.

This is the starting point every subsequent layer improves. The gaps are structural: REST calls
go directly to the database. No record of who committed to what. No SLA governs how long a task
sits. No formal obligation exists when a contractor says they will come on Thursday.

### Accountability gaps this layer leaves open

These gaps are what the subsequent layers close, one foundation module at a time:

| Gap | What breaks | Closed by |
|-----|-------------|-----------|
| No SLA enforcement | A contractor task created here can sit indefinitely | Layer 2 (casehub-work) |
| No commitment tracking | "Plumber committed to come Thursday" is a mental note, not a machine-tracked obligation | Layer 3 (casehub-qhorus) |
| No tamper-evident audit | Health and financial decisions have no independently verifiable record | Layer 4 (casehub-ledger) |
| No formal obligation | "Pick up kids at 3:30" has no required RESPONSE, no Watchdog | Layer 3 (casehub-qhorus) |
| No escalation path | Missed task sits silently — no automatic notification or escalation | Layer 2 (casehub-work) |

### Key files

**api/ — pure Java, zero framework:**
- `api/src/main/java/io/casehub/life/api/LifeDomain.java` — enum: HOUSEHOLD, HEALTH, FINANCE, FAMILY_SCHEDULING, TRAVEL, LEGAL, CONTRACTOR_COORDINATION, ELDER_CARE
- `api/src/main/java/io/casehub/life/api/LifeCapabilities.java` — String capability tag constants (household-management, health-coordination, etc.)
- `api/src/main/java/io/casehub/life/api/LifeTrustDimensions.java` — String trust dimension constants (deadline-reliability, cost-accuracy, etc.)
- `api/src/main/java/io/casehub/life/api/LifeActorType.java` — enum: AI_AGENT, HOUSEHOLD_PRINCIPAL, EXTERNAL_HUMAN (named `LifeActorType` to avoid collision with `io.casehub.platform.api.identity.ActorType`)
- `api/src/main/java/io/casehub/life/api/model/HouseholdTaskStatus.java` — PENDING, IN_PROGRESS, COMPLETED, CANCELLED
- `api/src/main/java/io/casehub/life/api/model/LifeGoalStatus.java` — ACTIVE, PAUSED, COMPLETED, ABANDONED

**app/ — Quarkus Panache Active Record:**
- `app/src/main/java/io/casehub/life/app/entity/ExternalActor.java` — id (UUID), name, actorType, contactMethod, contactValue, createdAt
- `app/src/main/java/io/casehub/life/app/entity/HouseholdTask.java` — id, domain, title, description, deadline, slaHours (Integer nullable), status, assignedTo (String, opaque), externalActorId (UUID nullable, no @ManyToOne), recurrence, createdAt, updatedAt
- `app/src/main/java/io/casehub/life/app/entity/LifeGoal.java` — id, domain, title, description, targetDate, status, createdAt, updatedAt
- `app/src/main/java/io/casehub/life/app/entity/LifeEvent.java` — id, domain, title, description, occurredAt, createdAt (no updatedAt — events are immutable)
- `app/src/main/java/io/casehub/life/app/service/HouseholdTaskService.java` — create, findById, list (4 filters), update, delete (throws NotFoundException)
- `app/src/main/java/io/casehub/life/app/service/ExternalActorService.java` — create, findById, list, update, delete (throws 409 if tasks reference actor), listTasks
- `app/src/main/java/io/casehub/life/app/service/LifeGoalService.java`, `LifeEventService.java` — standard CRUD
- `app/src/main/java/io/casehub/life/app/resource/ExternalActorResource.java` — @Blocking @ApplicationScoped, CRUD + /tasks sub-resource (GET /external-actors/{id}/tasks)
- `app/src/main/java/io/casehub/life/app/resource/HouseholdTaskResource.java` — @Blocking @ApplicationScoped, CRUD + list filters
- `app/src/main/java/io/casehub/life/app/resource/LifeGoalResource.java`, `LifeEventResource.java` — @Blocking @ApplicationScoped (LifeEvent has no PUT)

**Migrations (V100–V103):**
- `app/src/main/resources/db/migration/V100__create_external_actor.sql`
- `app/src/main/resources/db/migration/V101__create_household_task.sql`
- `app/src/main/resources/db/migration/V102__create_life_goal.sql`
- `app/src/main/resources/db/migration/V103__create_life_event.sql`

**Tests (51 total):**
- `app/src/test/java/io/casehub/life/app/ShowcaseScenarioTest.java` — household week @QuarkusTest narrative (6 ordered methods, state carries between methods)
- `app/src/test/java/io/casehub/life/app/*ResourceTest.java` — REST CRUD + filter + 404/409 coverage
- `api/src/test/java/io/casehub/life/api/*Test.java` — pure-Java enum/constant validation

### Architectural decisions

- **Direct Panache, no service SPI** — casehub-life is an application, not a library. Services grow across layers (Layer 2 adds a WorkItem call alongside the existing persist) rather than being replaced by alternative implementations. No `@DefaultBean` substitution pattern needed.
- **`slaHours` in Layer 1** — declared on `HouseholdTask` even though nothing enforces it until Layer 2. Correct domain modelling: a household task has an expected SLA whether or not the platform enforces it.
- **`ExternalActor` is life-specific** — not in casehub-qhorus-api. The actor model spec left this open; Layer 1 resolves it as a life-domain entity. Layers 2-3 add Qhorus commitment tracking against it.
- **`api/` is domain vocabulary only** — enums, constants, value records. No service interfaces. The api/app split follows the hexagonal pattern from AML and clinical.
- **`LifeActorType` not `ActorType`** — `io.casehub.platform.api.identity.ActorType` (HUMAN/AGENT/SYSTEM) is on the classpath once foundation modules activate. Naming conflict resolved at Layer 1 to avoid import collisions in later layers.
- **`externalActorId` as raw UUID, no `@ManyToOne`** — consistent with clinical (`AdverseEvent.enrollmentId`). Avoids cascade decisions and ORM entanglement before the Store SPI pattern is introduced in Layer 2. A future FK constraint can be added as a Flyway migration without touching entity code.
- **No DB FK constraint on `household_task.external_actor_id`** — the 409 guard lives in `ExternalActorService.delete()` within a single `@Transactional` boundary, preventing orphan UUIDs without needing a DB-level FK.
- **H2 drop-and-create in tests, not Flyway** — `casehub-engine-persistence-hibernate` puts `V1.0.0__Create_Quartz_Tables.sql` at `classpath:db/migration`, which Flyway interprets as the same version as casehub-work's `V1__initial_schema.sql`. Running Flyway in tests causes "found more than one migration with version 1" failure. Solution: `database.generation=drop-and-create` for both PUs in test config, matching clinical's validated pattern.

### Pattern to replicate

1. Create `api/` — pure Java, zero framework, zero JPA. Domain enums and constants only.
2. Define a `LifeDomain`-equivalent enum — each domain scopes a permission boundary and routing context.
3. Create `app/` — Quarkus Panache Active Record entities for each core entity type. Cross-entity references as raw UUID columns (no `@ManyToOne`).
4. Flyway migrations in `app/src/main/resources/db/migration/` at V100+ (casehub-work owns V1–V21+; ledger owns V1000–V1007). Migrations run in production; tests use `database.generation=drop-and-create`.
5. REST resources must be `@Blocking @ApplicationScoped` — quarkus-rest (RESTEasy Reactive) runs on the I/O thread; JDBC Panache blocks without `@Blocking`. `@Transactional` belongs on service methods only, not on resource methods.
6. Test `application.properties`: two H2 datasources (default + qhorus), both with `drop-and-create`. Suppress reactive with `quarkus.datasource.reactive=false` for both. Exclude CDI conflicts (JpaWorkloadProvider, connector beans). Index engine jars explicitly.
7. Write a `@QuarkusTest @TestMethodOrder` ShowcaseScenarioTest that narrates a full domain week showing the accountability gaps in sequence. Gap commentary goes in LAYER-LOG.md, not in test code.
8. Unit-test stateless domain logic (enum values, constant uniqueness) in pure Java without Quarkus.

---

## Layer 2 — + casehub-work (SLA enforcement)

**Status:** Complete  
**Completed:** 2026-05-27

### Summary

casehub-work WorkItems are created alongside `LifeTaskContext` supplements when life-domain tasks are created via `POST /life-tasks`. `LifeSlaBreachPolicy` implements two-tier stateless SLA escalation: first breach escalates to `household-admin`; second breach terminates terminally. Flyway migrations moved to `db/life/migration/` (PP-20260525-607b33). The `HouseholdTask`, `LifeGoal`, and `LifeEvent` wrapper entities were removed; their fields map to foundation primitives with no data loss.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| Contractor deadline passes silently | No escalation, no audit | WorkItem + LifeSlaBreachPolicy |
| Health follow-up silently overdue | SLA breach has no effect | ExpiryLifecycleService + policy |
| ExternalActor deleted while referenced | Dangling FK in supplement | LifeTaskContext referential guard |

### Key wiring

- `ExpiryLifecycleService` must NOT be in `quarkus.arc.exclude-types` in test config — inject and call `checkExpired()` directly to test SLA enforcement.
- `LifeTaskService.create()` is `@Transactional` — WorkItem and LifeTaskContext created atomically because `WorkItemService.create()` uses `REQUIRED` semantics.
- Template seeding: `V102__life_workitem_template_seeds.sql` runs in production; tests seed via `@BeforeEach @Transactional` using direct `WorkItemTemplate.persist()`.
- `candidateGroups` is NOT exposed on `CreateLifeTaskRequest` — groups come from the template exclusively to prevent tier detection bugs in `LifeSlaBreachPolicy`.
- `WorkItemPriority` enum has no `NORMAL` value — use `MEDIUM` for default priority.
- Layer 5 engine dependencies removed from pom.xml — `casehub-engine` 0.2-SNAPSHOT has a broken internal package refactor (casehubio/engine#379, casehubio/engine#380) that breaks Quarkus augmentation via VertxProcessor. Engine deps re-added in Layer 5 branch once fixed.

### Architectural decisions

- `LifeTaskContext` is a domain context entity (not a WorkItem wrapper): carries only fields with no foundation equivalent (`domain`, `externalActorId`, `recurrence`). Does not duplicate `title`, `deadline`, `status`, `assignedTo` — those live in WorkItem.
- Tier detection in `LifeSlaBreachPolicy` uses `candidateGroups.contains("household-admin")` — safe because callers cannot set groups.
- `HouseholdTask`, `LifeGoal`, `LifeEvent` entities removed — they duplicated foundation primitives (WorkItem, CaseInstance, LedgerEntry). Rationale in `docs/specs/2026-05-27-layer2-casehub-work-sla.md`.

### Pattern introduced

**WorkItem-backed life task with domain supplement:** `LifeTaskService` creates a `WorkItem` from a named `WorkItemTemplate` and a `LifeTaskContext` supplement in a single `@Transactional` boundary. The template provides candidateGroups and SLA; the supplement carries life-domain context.

### Pattern anchor

- `LifeTaskService#create()` — WorkItem + supplement creation
- `LifeSlaBreachPolicy#onBreach()` — stateless tier detection

### Gotchas

- `ExpiryLifecycleService` excluded from CDI in tests (Layer 1) to prevent scheduler interference — re-enabled in Layer 2 for direct injection in SLA tests.
- `WorkItemTemplate.defaultExpiryHours` (from V5 `default_expiry_hours`) is used in `LifeTaskService` — do NOT use `defaultExpiryBusinessHours` (separate column, not seeded in V102).
- `@Transactional @BeforeEach` for template seeding works in Quarkus @QuarkusTest.
- `Response.Status.UNPROCESSABLE_ENTITY` does not exist in the JAX-RS version in use — use raw integer 422 in `WebApplicationException(message, 422)`.
- `casehub-engine` 0.2-SNAPSHOT has broken internal package references — remove engine deps until SNAPSHOT is fixed (engine#379, engine#380).

### Pattern to replicate

1. Define `WorkItemTemplate` seeds in `V1NN__<app>_workitem_template_seeds.sql` with domain-specific `category`, `candidate_groups`, and `default_expiry_hours`.
2. Create `<Domain>TaskService` with `@Transactional create()` that: resolves template by name (422 if absent), validates optional actor FK (422 if not found), validates non-null `candidateGroups` (GE-20260522-4e806e), builds `WorkItemCreateRequest` with `callerRef` and `scope`, calls `WorkItemService.create()`, persists domain supplement.
3. Implement `SlaBreachPolicy` using stateless tier detection via `candidateGroups` (GE-20260522-f7db12). Do NOT allow callers to set `candidateGroups` — the policy's tier detection depends on template groups being the only initial groups.
4. In tests: re-enable `ExpiryLifecycleService` in CDI, inject it, call `checkExpired()` directly with past-deadline WorkItems to verify breach fires without scheduler dependency.

### Navigation

```bash
git log --grep="#3" --oneline
```

---

## Layer 3 — + casehub-qhorus (commitment lifecycle)

**Status:** Complete
**Completed:** 2026-05-29
**Issue:** casehubio/life#4

### Summary

casehub-qhorus is adopted for formal COMMAND/RESPONSE commitment tracking across three household accountability patterns: family delegation, contractor follow-up, and oversight gates. A `LifeCommitmentRecord` supplement (parallel to `LifeTaskContext` from Layer 2) links life-domain commitment context to the native qhorus `Commitment` via `correlationId`. Three life-domain Qhorus channels are provisioned at startup: `life/delegation`, `life/oversight` (COMMAND+RESPONSE enforced), and `life/actor/{externalActorId}` (per-actor, contractor commitments). One APPROVAL_PENDING Watchdog per channel monitors for expired Commitments. Two new REST actions are added: `POST /life-tasks/{id}/commit` (delegation or contractor commitment) and `POST /life-oversight-gates` (pre-approval gate — WorkItem created only after household-admin RESPONSE).

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| "Plumber committed Thursday" is a mental note | No machine-tracked obligation; no follow-up if no-show | COMMAND on `life/actor/{id}` + APPROVAL_PENDING Watchdog |
| Family delegation has no enforcement | "Pick up kids at 3:30" delegated verbally — no RESPONSE required, no Watchdog | COMMAND to `life/delegation` with deadline |
| Major financial decision proceeds without gate | No oversight gate; spend can proceed before household-admin approves | COMMAND to `life/oversight`; WorkItem not created until RESPONSE |

### Key wiring

- `MessageService.dispatch()` is the sole enforcement gate — all three strategies call it; no bypass allowed (PP-20260523-a08b97). `actorType(ActorType.SYSTEM)` is required on every builder call or it throws `IllegalArgumentException` at `build()`.
- `ChannelService.create()` does NOT register in `ChannelGateway` — both must be called, in that order (GE-20260526-5247f2).
- `LifeChannelInitializer` is `@Transactional` on `onStart()` — channels and Watchdogs registered atomically at startup. `ensureActorChannel()` is also `@Transactional` for on-demand contractor channels.
- `MessageDispatch.Builder` takes `channelId: UUID`, not channel name. Strategies call `channelInitializer.channelIdFor(DELEGATION_CHANNEL)` or `channelInitializer.ensureActorChannel(id)` to resolve UUID.
- `LifeOversightResponseObserver.onMessage()` receives `MessageReceivedEvent` (not `Message`). Implements `MessageObserver` which is application-wide — channel name guard (`event.channelName().equals(OVERSIGHT_CHANNEL)`) is required.
- `LifeWatchdogAlertObserver` uses `@ObservesAsync` — qhorus fires `WatchdogAlertEvent` via `fireAsync()`. Synchronous `@Observes` observers do NOT receive async events.
- `WatchdogAlertEvent.notificationChannel()` returns the channel NAME string (from `Watchdog.notificationChannel`), not a UUID. Observer queries `LifeCommitmentRecord` by that name.
- `LifeCommitmentService.applyCommitment()` validates XOR before strategy dispatch: exactly one of `delegateTo` or `externalActorId` must be non-null, and `deadline` is required. Returns 422 if malformed.
- `CommitmentConflictException` (unchecked) maps to 409 at the resource layer. Duplicate delegation/contractor: `findByWorkItemId` guard. Duplicate oversight: partial unique index on (`delegate_to`) WHERE mode='OVERSIGHT' AND status='PENDING_RESPONSE'.
- `LifeCommitmentRecord` is on the default datasource (life domain) — NOT the qhorus named datasource.
- Template `life-escalation` (V104): seeded with `candidate_groups = 'household-admin'`, category = `household`. Required by `LifeWatchdogAlertObserver.createEscalationTask()` — seed in `@BeforeEach @Transactional` for tests that exercise the alert path.

### Architectural decisions

- `LifeCommitmentStrategy` SPI lives in `app/` not `api/` — the sealed context types reference JPA entities (`WorkItem`, `LifeTaskContext`, `ExternalActor`). Placing them in `api/` creates a circular Maven dependency. This SPI has no external consumers; CDI `Instance<LifeCommitmentStrategy>` collects all three implementations.
- `OversightContext` carries no `WorkItem` — the oversight gate is pre-approval; no WorkItem exists until `LifeOversightResponseObserver` creates it on RESPONSE. This is the correct domain semantics: work that hasn't been approved yet is not a WorkItem.
- `delegateTo` column repurposed as dedup key for OVERSIGHT mode (value is `title:templateRef` hash). A partial unique index enforces it at the DB level. Acknowledged semantic overload — a dedicated `oversight_key` column would be cleaner but was not added to avoid schema complexity.
- Life channels are NOT normative qhorus mesh channels. The normative layout (`/work`, `/observe`, `/oversight` suffix convention) governs agent orchestration channels managed by Claudony's `NormativeChannelLayout` SPI. Life household channels are domain coordination channels with their own naming.
- `LifeOversightResponseObserver` uses `@Transactional(REQUIRES_NEW)` — `MessageService.dispatch()` calls observers synchronously inside the qhorus dispatch transaction; the observer needs its own transaction to persist the new WorkItem and update the commitment record independently.

### Gotchas

- `MessageDispatch.Builder()` requires `.actorType()` — omitting it throws `IllegalArgumentException: actorType is required` at `build()`. Use `ActorType.SYSTEM` for life-system dispatches.
- `WatchdogAlertEvent` has no `correlationId` field (checked bytecode) — it has `notificationChannel`, `watchdogId`, `firedAt`, and aggregate `context` (ApprovalPendingContext with `pendingCount` and `oldestExpiryAt`). The observer must query by channel name, not correlationId.
- `ChannelGateway` is in `io.casehub.qhorus.runtime.gateway` — not `io.casehub.qhorus.runtime.channel`.
- `DispatchResult.inReplyTo()` and `MessageDispatch.Builder.inReplyTo()` take `Long` (message sequence ID), not `UUID` (ledger entry ID). Use `commandResult.messageId()` for the RESPONSE builder.
- casehub-work-api `SelectionContext` constructor signature changes between SNAPSHOTs. If tests fail with `NoSuchMethodError: SelectionContext.<init>(String...)`, reinstall casehub-work from source: `mvn install -DskipTests -pl api,core,deployment -am -f /path/to/casehub-work/pom.xml`.
- `@ObservesAsync` in tests: `evaluateAll()` fires `WatchdogAlertEvent` asynchronously. Asserting the observer's result immediately after `evaluateAll()` races the async handler. Use Awaitility with a `@Transactional` helper method on the test class (required for the Panache query inside the Awaitility lambda).

### Pattern introduced

**Commitment strategy dispatch:** `LifeCommitmentService` collects `@ApplicationScoped` strategy implementations via `Instance<LifeCommitmentStrategy>`, asserts exactly one `applies()`, and executes it. The sealed context hierarchy (`DelegationContext`, `ContractorContext`, `OversightContext`) eliminates null-field documentation and NPE risk.

**Oversight gate with deferred WorkItem creation:** `OversightGateStrategy` persists `LifeCommitmentRecord{workItemId=null, pendingTaskJson=...}` and dispatches COMMAND. `LifeOversightResponseObserver` creates the WorkItem on RESPONSE, sets `workItemId`, and marks FULFILLED.

### Pattern anchor

- `LifeChannelInitializer#ensureChannelWithWatchdog()` — idempotent channel + Watchdog registration
- `LifeCommitmentService#applyCommitment()` — XOR validation + strategy dispatch
- `OversightGateStrategy#execute()` — deferred WorkItem creation pattern
- `LifeOversightResponseObserver#onMessage()` — RESPONSE-to-WorkItem bridge

### Navigation

```bash
git log --grep="#4" --oneline
```

### Open follow-up

- life#17 — `LifeWatchdogAlertObserver` escalation path needs integration test + Watchdog state isolation between @QuarkusTest classes
- life#18 — REST resource consistency (@Produces/@Consumes on all resources, 201 for commitment creation)
- life#16 — `docs/specs/life-automation.md` layer table is stale (wrong layer ordering)

---

## Layer 4 — + casehub-ledger (tamper-evident audit)

**Status:** Pending
**Issue:** casehubio/life#5
**Navigation:** `git log --grep="#5" --oneline` (fill in at layer close)

### What it shows

Integrates `casehub-ledger` for tamper-evident Merkle audit of health decisions, financial
decisions, and legal actions. GDPR Art.17 erasure for personal data stored in the ledger. Every
major decision has a cryptographically verifiable record — not just a database entry.

---

## Layer 5 — + casehub-engine (multi-step workflows)

**Status:** Pending
**Issue:** casehubio/life#6
**Navigation:** `git log --grep="#6" --oneline` (fill in at layer close)

### What it shows

Integrates `casehub-engine` for complex multi-step CasePlanModel workflows. Travel planning
(destination research → budget gate → flight search → human approval → booking → reminders).
Care coordination (assessment → care plan → site assignments → SLA monitoring).
The CasePlanModel replaces linear REST calls with adaptive workflow orchestration.

---

## Layer 6 — Trust routing

**Status:** Pending
**Issue:** casehubio/life#7
**Navigation:** `git log --grep="#7" --oneline` (fill in at layer close)

### What it shows

Trust-weighted agent routing. Which health-agent has the highest deadline-reliability score?
Which finance-agent has the best cost-accuracy record? Over time, the platform learns which
agents handle which household domains reliably and routes accordingly.

---

## Layer 7 — + casehub-openclaw (OpenClaw integration)

**Status:** Pending
**Issue:** casehubio/life#8
**Navigation:** `git log --grep="#8" --oneline` (fill in at layer close)

### What it shows

Integrates casehub-openclaw as the WorkerProvisioner. OpenClaw instances execute household
skills: banking API aggregation (Open Banking skills), Google Calendar integration (calendar
skill), Home Assistant smart home control (IoT skill), WhatsApp/SMS follow-up (messaging skills).

The ChannelContextWindow ensures each OpenClaw agent wakes with fresh context from Qhorus
channels — grocery-agent sees finance-agent's budget warning before placing an order;
health-agent sees smart home movement data before sending a medication reminder.

This layer demonstrates the bidirectional integration: CaseHub orchestrates via direct call
to /hooks/agent; OpenClaw heartbeat monitors ambient conditions and creates CaseHub WorkItems
when conditions warrant. Neither replaces the other.
