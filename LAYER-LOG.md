# casehub-life Agentic Harness — Layer Log

Architecture record of what was built at each integration layer. Entries are ordered for
reading comprehension (learning progression), not chronology. Each entry is complete when
the layer closes.

**Migration note (2026-06-02):** Migrated to `ARC42STORIES.MD §9.4` Layer Entries.
This file retained as source-of-truth draft — `ARC42STORIES.MD` is the primary
architecture record. Format: `../parent/docs/arc42stories-spec.md` and
`../parent/docs/arc42stories-casehub-profile.md`.

Cross-references:
- Platform compliance gap analysis: `docs/specs/life-automation.md`
- Actor model: `docs/specs/life-actor-model.md`
- AML reference implementation: `../aml/LAYER-LOG.md`
- Clinical reference implementation: `../clinical/LAYER-LOG.md`
- Research spec: `../parent/docs/specs/2026-05-25-openclaw-casehub-integration.md`

**Architectural note — hexagonal pattern:** casehub-life uses the AML api/app split:
- `api/` — pure Java, zero framework imports, zero JPA. Domain records and constants.
- `app/` — Quarkus application: Panache entities, REST resources, Flyway, foundation wiring.

---

## Vertical Slices

| Slice | Capability delivered | Layers | Arch patterns | Status |
|---|---|---|---|---|
| S1 | Life-domain tasks created against named WorkItemTemplates with SLA deadlines; household-admin escalated automatically when deadlines breach | L1, L2 | Hexagonal, Strategy | ✅ complete |
| S2 | Contractor commitments tracked via COMMAND/RESPONSE with Watchdog follow-up; family delegation requires acknowledged RESPONSE; financial-oversight gates defer WorkItem creation — no task exists until household-admin approves | + L3 | + Observer | ✅ complete |
| S3 | Health, financial, and legal decisions produce tamper-evident Merkle audit records; GDPR Art.17 erasure for contractor personal data | + L4 | 🔲 | 🔲 pending |
| S4 | Multi-step household workflows (travel, care, home-maintenance) orchestrated via CasePlanModel with adaptive approval gates | + L5 | + Event-Driven | 🔲 pending |
| S5 | Life-domain tasks routed to the highest-trust agent per household domain based on Bayesian track record of past outcomes | + L6 | + Registry | 🔲 pending |
| S6 | OpenClaw agent skills (banking, calendar, smart home, messaging) execute household automation with full CaseHub accountability | + L7 | + Factory | 🔲 pending |

**Ordering rationale:**
- S1 before S2: WorkItem infrastructure (L2) required for commitment targets — qhorus COMMAND needs a task to commit on
- S2 before S3: Qhorus channels (L3) produce `MessageLedgerEntry` records that enrich ledger audit — commitment decisions are the high-value audit events (soft ordering)
- S3 before S4: Ledger (L4) provides tamper-evident capture of CasePlanModel decision points — CasePlanModel without audit is untracked (soft ordering)
- S4 before S5: Workflow outcomes (L5) are the primary source of trust score signals — trust routing needs outcome data to route by (hard ordering)
- S5 before S6: Trust routing (L6) must be wired before OpenClaw dispatches agents — otherwise agents launch without trust weighting (soft ordering)

**Architectural references:**
- `../parent/docs/ARCHITECTURE.md` — pattern definitions (Hexagonal, Strategy, Observer, Event-Driven, Registry, Factory)
- `../parent/docs/PLATFORM.md` — capability ownership; boundary rules
- `../parent/docs/protocols/universal/` and `../parent/docs/protocols/casehub/` — conventions
- `docs/specs/life-automation.md` — gap analysis and use case mapping
- `../parent/docs/repos/casehub-life.md` — what this app owns

---

## Layer 1 — Domain baseline (no CaseHub foundation)

**Participates in:** S1, S2, S3, S4, S5, S6 *(foundation — all slices build on it)*
**Architectural pattern:** Hexagonal (api/app split), Active Record (Panache)
**Key protocols:** `flyway-migration-rules.md`, `module-tier-structure.md`, PP-20260526-d0b921 (REST @Blocking @ApplicationScoped)
**Design refs:** `docs/specs/2026-05-26-layer1-domain-baseline.md`

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

### What it adds

Household domain vocabulary with no CaseHub foundation modules. Core entities in `app/`
(Quarkus Panache) and domain constants in `api/` (pure Java). A REST API for external actors
and life-domain tasks — without SLA enforcement, commitment tracking, or audit.

The accountability gaps are structural: no record of who committed to what, no SLA enforcement,
no formal obligation when a contractor commits to a date, no escalation when deadlines pass.

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
- `app/src/test/java/io/casehub/life/app/ShowcaseScenarioTest.java` — @QuarkusTest integration scenario (6 ordered methods, shared state; candidate for rename/refactor)
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
7. Unit-test stateless domain logic (enum values, constant uniqueness) in pure Java without Quarkus.

---

## Layer 2 — + casehub-work (SLA enforcement)

**Participates in:** S1, S2, S3, S4, S5, S6 *(foundation — all slices build on it)*
**Architectural pattern:** Hexagonal, Strategy (SlaBreachPolicy), Domain Supplement (LifeTaskContext)
**Key protocols:** PP-20260527-da1f66 (domain supplement pattern), PP-20260526-75d9c9 (@Transactional on service only), PP-20260525-607b33 (Flyway repo-scoped path)
**Design refs:** `docs/specs/2026-05-27-layer2-casehub-work-sla.md`
**Status:** Complete  
**Completed:** 2026-05-27
**Issue:** casehubio/life#3
**Navigation:** `git log --grep="#3" --oneline`

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

---

## Layer 3 — + casehub-qhorus (commitment lifecycle)

**Participates in:** S2, S3, S4, S5, S6
**Architectural pattern:** Observer (CDI @Observes/@ObservesAsync), Strategy (LifeCommitmentStrategy sealed hierarchy)
**Key protocols:** `dual-trail-audit-pattern.md`, `message-dispatch-builder-validation.md`, `alternative-extension-patterns.md`
**Design refs:** `docs/specs/2026-05-29-layer3-qhorus-commitment.md`
**Status:** Complete
**Completed:** 2026-05-29
**Issue:** casehubio/life#4
**Navigation:** `git log --grep="#4" --oneline`

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

---

## Layer 4 — + casehub-ledger (tamper-evident audit)

**Participates in:** S3, S4, S5, S6
**Architectural pattern:** JOINED Inheritance (LedgerEntry subclasses), Observer (CDI @Observes for SLA_BREACH/COMPLETED), Unified Writer (LifeLedgerWriter)
**Key protocols:** `dual-trail-audit-pattern.md`, PP-20260524-10efef (Flyway ledger locations), `auth-retrofit-readiness.md`
**Design refs:** `docs/specs/2026-05-30-layer4-casehub-ledger-design.md`
**Status:** Complete
**Completed:** 2026-05-30
**Issue:** casehubio/life#5
**Navigation:** `git log --grep="#5" --oneline`

### What it adds

Tamper-evident Merkle audit for health decisions, financial decisions, and legal actions via
`casehub-ledger`. GDPR Art.17 erasure for contractor personal data. Every major life decision
has a cryptographically verifiable record independent of the operational database.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| Health decision has no independently verifiable record | GP appointment could be silently modified or deleted | HealthDecisionLedgerEntry — Merkle chain per workItemId |
| Financial approval has no tamper-evident audit | Household-admin approval can be disputed after the fact | FinancialDecisionLedgerEntry — oversightRef chains the full lifecycle |
| Legal action has no compliance record | Tax filing deadline met but no proof | LegalActionLedgerEntry — filingDeadline + actionTaken recorded at COMPLETED |
| Contractor personal data cannot be erased | GDPR Art.17 right to erasure not implemented | DELETE /external-actors/{id}/personal-data + ExternalActorErasureLedgerEntry |

### Key files

- `app/src/main/java/io/casehub/life/app/ledger/HealthDecisionLedgerEntry.java` — JOINED subclass for health domain audit
- `app/src/main/java/io/casehub/life/app/ledger/FinancialDecisionLedgerEntry.java` — JOINED subclass for financial domain audit
- `app/src/main/java/io/casehub/life/app/ledger/LegalActionLedgerEntry.java` — JOINED subclass for legal domain audit
- `app/src/main/java/io/casehub/life/app/ledger/ExternalActorErasureLedgerEntry.java` — JOINED subclass for GDPR erasure proof
- `app/src/main/java/io/casehub/life/app/service/ledger/LifeLedgerWriter.java` — unified writer for all 4 domain entry types
- `app/src/main/java/io/casehub/life/app/observer/LifeDecisionLedgerObserver.java` — CDI observer for SLA_BREACH and COMPLETED

### Key wiring

- Qhorus PU packages must use `io.casehub.ledger.runtime` (broad) to include `LedgerSupplement` sub-package. Using `io.casehub.ledger.runtime.model` misses supplements and causes Hibernate `AnnotationException`.
- Life ledger entities in `io.casehub.life.app.ledger` — NOT in `io.casehub.life.app.entity.ledger`. Quarkus uses prefix matching for PU assignment; sub-packages of a default-PU package get assigned to the default PU.
- `LedgerEntry.@PrePersist` auto-assigns `id` and `occurredAt` — writers must NOT set these fields.
- CREATE trigger is a direct call from `LifeTaskService`/`OversightGateStrategy` (not CDI observer) because `WorkItemLifecycleEvent` fires before `LifeTaskContext.persist()`.
- Finance SLA_BREACH pre-RESPONSE goes through `LifeWatchdogAlertObserver` (WatchdogAlertEvent), not `LifeDecisionLedgerObserver` (SlaBreachEvent) — no WorkItem exists before RESPONSE.

### Architectural decisions

- **Per-domain LedgerEntry subclasses (not single table):** Each domain has distinct required fields. Nullable columns for required domain fields are a production anti-pattern. The composition concern doesn't apply — casehub-life is a terminal application tier; these entities have no downstream consumers.
- **No ActorIdentityProvider SPI:** PII lives exclusively in the ExternalActor JPA entity. Ledger entries store only UUIDs. Direct field nullification is correct and minimal.
- **Separate GDPR endpoint:** `DELETE /external-actors/{id}/personal-data` is distinct from hard delete `DELETE /external-actors/{id}`. Different semantics: erasure retains the row for FK integrity.
- **Unified LifeLedgerWriter (not per-domain writers):** sequenceNumber computation and base field assembly are shared logic. One class, one injection point, one place to fix.

### Pattern introduced

**CDI observer + direct-call hybrid trigger model:** SLA_BREACH and COMPLETED events flow through CDI observers; CREATE events use direct service calls due to event-ordering constraints. The observer delegates to the same `LifeLedgerWriter` the services call directly.

### Pattern anchor

- `LifeLedgerWriter#writeHealthEntry()` — unified writer pattern
- `LifeDecisionLedgerObserver#onSlaBreachEvent()` — CDI observer domain dispatch
- `ExternalActorService#erase()` — GDPR erasure with active-task guard

### Gotchas

- **Quarkus multi-PU prefix matching** — `io.casehub.life.app.entity.ledger` (sub-package of default-PU's `io.casehub.life.app.entity`) gets assigned to the default PU. Symptom: `AnnotationException: Association 'LedgerEntry.supplements' targets LedgerSupplement which does not belong to the same persistence unit`. Fix: use `io.casehub.life.app.ledger` (sibling, not child, of `io.casehub.life.app.entity`).
- **WorkItemLifecycleEvent fires before LifeTaskContext.persist()** — CDI observer for CREATE would find no LifeTaskContext. Symptom: silent miss (no ledger entry written). Fix: direct service call for CREATE, CDI observer only for SLA_BREACH/COMPLETED.
- **WorkItem.outcome already set post-mutation** — observer loading WorkItem in REQUIRES_NEW transaction gets the correct outcome; do NOT reassign `workItem.outcome = event.outcome()` — this marks the entity dirty and causes a spurious UPDATE.

### Pattern to replicate

1. Create JOINED-inheritance `LedgerEntry` subclasses in a package that is NOT a sub-package of your default PU entity package. Add the package to the qhorus PU `packages` config.
2. Add `classpath:db/<app>/ledger/migration` to the qhorus Flyway locations. Use V2100+ to avoid conflicts with ledger base (V1000-V1007) and qhorus (V2000).
3. Create a unified writer service that injects `LedgerEntryRepository`, owns `sequenceNumber` computation via `findLatestBySubjectId`, and assembles base fields. Do NOT set `id` or `occurredAt` — `@PrePersist` handles them.
4. For CREATE events: call the writer directly from the service layer after persisting domain context.
5. For SLA_BREACH and COMPLETED: write a CDI `@Observes` observer on `SlaBreachEvent` and `WorkItemLifecycleEvent`. Use `@Transactional(REQUIRES_NEW)` so ledger writes commit independently.
6. For GDPR Art.17: null PII fields on the domain entity, write an erasure ledger entry as proof. Guard with 404 (not found), 409 (already erased), 409 (active tasks exist via `workItem.status.isActive()`).

---

## Layer 5 — + casehub-engine (multi-step workflows)

**Participates in:** S4, S5, S6
**Architectural pattern:** Event-Driven (CasePlanModel + CDI @ObservesAsync), Hexagonal (api/app split preserved), Strategy (per-case YamlCaseHub augmentation)
**Key protocols:** PP-20260518-case-definition-layers (YAML + DSL pairing), PP-20260531-worker-func-exec (FuncDSL worker execution), PP-20260529-3ffe28 (three-phase case start)
**Design refs:** `docs/specs/2026-05-31-layer5-casehub-engine-design.md`
**Status:** Complete
**Completed:** 2026-06-01 — `358c6eb`, fix: `338aa16`
**Issue:** casehubio/life#6
**Navigation:** `git log --grep="#6" --oneline`
→ 358c6eb feat(#6): Layer 5 — casehub-engine CasePlanModel workflows
→ 338aa16 fix(#6): add missing engine memory @Alternative beans to selected-alternatives
**Blog:** `blog/2026-06-01-mdp01-layer5-eight-workflows.md`

### What it adds

**Before:** Layers 1–4 provide standalone operations — a single REST call creates a task, commitment, or ledger entry. No multi-step orchestration.
**After:** `casehub-engine` CasePlanModel workflows coordinate sequences of workers, adaptive gates, and cross-step signals into formally tracked household cases.

Eight case definitions cover the full breadth of engine capabilities:
- **travel-plan** — parallel execution (flight + hotel search), adaptive budget gate, M-of-N SubCase family vote (2-of-3), DECLINE + rebooking recovery
- **home-maintenance-cycle** — qhorus COMMAND via QhorusMessageSignalBridge (case enters WAITING), contractor RESPONSE resumes case, ledger write on completion
- **care-coordination** — SubCase lifecycle (care-episode child case), milestones with SLA tracking, cross-case signal to appointment-cycle on health concern
- **appointment-cycle** — DECLINE recovery (alternative provider), health decision ledger write
- **contractor-coordination** — full qhorus lifecycle (COMMAND → Watchdog → RESPONSE/DECLINE), cross-case signal to financial-review on payment
- **financial-review** — cross-case signal reception, spending aggregation, qhorus oversight gate for anomalies
- **family-vote** — lightweight M-of-N child case (single humanTask per voter)
- **care-episode** — child case spawned by care-coordination (assess → provide care → record notes)

Integration tests re-enabled (engine#410 resolved — commit 66a6e34, life#23). Engine-level compliance ledger (`casehub-engine-ledger`) deferred to Layer 6.

### Accountability gaps closed

| Gap | What breaks without it | Closed by |
|-----|----------------------|-----------|
| Household coordination is a series of disconnected tasks | Travel booking requires manual sequencing of research → budget → vote → book → confirm | CasePlanModel workflow with binding conditions |
| No adaptive routing based on context | Every purchase triggers the same approval path regardless of cost | Adaptive gate bindings: `isHighValue == true` fires M-of-N vote; `requiresApproval == true and isHighValue == false` fires single approval |
| No formal quorum for shared household decisions | Major purchase approved by one person without family input | M-of-N SubCase: travel-plan spawns 3 family-vote child cases, requires 2 approvals |
| No cross-case coordination | Contractor payment completes with no effect on monthly financial review | Cross-case signal via LifeCaseTracker: contractor-coordination signals active financial-review |

### Key files

**api/ — enums and request/response records:**
- `api/src/main/java/io/casehub/life/api/LifeCaseType.java` — TRAVEL_PLAN, HOME_MAINTENANCE, CARE_COORDINATION, APPOINTMENT_CYCLE, CONTRACTOR_COORDINATION, FINANCIAL_REVIEW (FAMILY_VOTE is sub-case only, not a LifeCaseType)
- `api/src/main/java/io/casehub/life/api/LifeCaseStatus.java` — ACTIVE, COMPLETED, FAILED

**app/engine/ — 8 YamlCaseHub subclasses + 8 fluent DSL companions:**
- `app/src/main/java/io/casehub/life/app/engine/TravelPlanCaseHub.java` — augments YAML with M-of-N SubCase bindings (DSL-only feature)
- `app/src/main/java/io/casehub/life/app/engine/HomeMaintenanceCaseHub.java` — qhorus COMMAND worker via QhorusMessageSignalBridge
- `app/src/main/java/io/casehub/life/app/engine/CareCoordinationCaseHub.java` — SubCase binding for care-episode, milestone definitions
- `app/src/main/java/io/casehub/life/app/engine/AppointmentCycleCaseHub.java` — DECLINE recovery binding, health ledger write
- `app/src/main/java/io/casehub/life/app/engine/ContractorCoordinationCaseHub.java` — full qhorus lifecycle, cross-case signal to financial-review
- `app/src/main/java/io/casehub/life/app/engine/FinancialReviewCaseHub.java` — cross-case signal reception, oversight gate
- `app/src/main/java/io/casehub/life/app/engine/FamilyVoteCaseHub.java` — single humanTask child case for M-of-N quorum
- `app/src/main/java/io/casehub/life/app/engine/CareEpisodeCaseHub.java` — child case for care-coordination SubCase
- DSL companions: `TravelPlanCaseDefinitions.java`, `HomeMaintenanceCaseDefinitions.java`, `CareCoordinationCaseDefinitions.java`, `AppointmentCycleCaseDefinitions.java`, `ContractorCoordinationCaseDefinitions.java`, `FinancialReviewCaseDefinitions.java`, `FamilyVoteCaseDefinitions.java`, `CareEpisodeCaseDefinitions.java`

**app/engine/ — services and observers:**
- `app/src/main/java/io/casehub/life/app/engine/LifeCaseService.java` — three-phase case start (PP-20260529-3ffe28): validate → join() outside TX → persist engineCaseId
- `app/src/main/java/io/casehub/life/app/engine/LifeCaseTrackerObserver.java` — `@ObservesAsync CaseLifecycleEvent` updates tracker status on case completion

**app/entity/ and app/resource/:**
- `app/src/main/java/io/casehub/life/app/entity/LifeCaseTracker.java` — JPA entity tracking active engine cases by type for cross-case signal lookup
- `app/src/main/java/io/casehub/life/app/resource/LifeCaseResource.java` — `POST /life-cases`, @Blocking @ApplicationScoped

**YAML case definitions (8 files):**
- `app/src/main/resources/life/travel-plan.yaml`, `home-maintenance.yaml`, `care-coordination.yaml`, `appointment-cycle.yaml`, `contractor-coordination.yaml`, `financial-review.yaml`, `family-vote.yaml`, `care-episode.yaml`

**Migration:**
- `app/src/main/resources/db/life/migration/V107__create_life_case_tracker.sql`

### Key wiring

- **Engine memory @Alternative beans** — `quarkus.arc.selected-alternatives` must include `MemorySubCaseGroupRepository`, `MemoryPlanItemStore`, `MemoryReactivePlanItemStore` from `casehub-engine-persistence-memory`. Without them the engine silently falls back to no-op implementations and cases start but never progress.
- **Jandex index entries** — test `application.properties` must index `casehub-engine-common`, `casehub-engine-blackboard`, `casehub-engine-work-adapter`, `casehub-engine-scheduler-quartz`, `casehub-engine-persistence-memory`, `casehub-engine-testing`. Missing entries produce silent CDI resolution failures.
- **Scope retrofit** — `LifeTaskService` changed WorkItem scope from `"life"` to `"casehubio/life/" + domain.name().toLowerCase()`. `LifeDecisionLedgerObserver` resolves domain from scope Path (primary), LifeTaskContext (fallback). Engine-created WorkItems produce correct ledger entries without supplements.
- **M-of-N SubCase is DSL-only** — YAML schema does not support `groupId`, `totalInGroup`, `requiredCount`. Travel-plan's family-vote bindings added via Java augmentation in `TravelPlanCaseHub.getDefinition()`.
- **Cross-case signals live in workers** — the completing worker queries `LifeCaseTracker` for active target cases and calls `CaseHubRuntime.signal()`. `LifeCaseTrackerObserver` is pure infrastructure (status update only).

### Architectural decisions

- **Why 8 case definitions in one layer rather than incremental:** casehub-life demonstrates the full breadth of engine capabilities. Splitting across layers produces seven more issues and branch ceremonies for work sharing the same infrastructure. Tradeoff: larger single commit, harder to review incrementally.
- **Why `casehub-engine-persistence-memory` at compile scope rather than test:** in-memory persistence avoids Docker dependency for development. Production would use `casehub-engine-persistence-hibernate`. Tradeoff: no persistence durability across restarts.
- **Why direct YamlCaseHub injection rather than string-based lookup:** type-safe, compile-time verified. `LifeCaseService` injects each YamlCaseHub bean directly and switches on `LifeCaseType`. Matches clinical pattern.
- **Why cross-case signals in workers rather than lifecycle observers:** the completing worker already has the domain context (payment amount, health concern). The lifecycle observer is generic infrastructure — it would need to re-derive domain context. Clinical reference: `TrialSafetySignalService` called from AE escalation observer.

### Pattern introduced

**YAML + DSL paired case definition with FuncDSL workers:** each case has a YAML definition (structure) and a DSL companion (programmatic, used for tests and augmentation). Workers use `FuncWorkflowBuilder` per PP-20260531.

### Pattern anchor

- `TravelPlanCaseHub#getDefinition()` — YAML + DSL augmentation with M-of-N SubCase bindings
- `LifeCaseService#startCase()` — three-phase case start pattern

### Gotchas

- **Engine memory @Alternative beans silently missing**
  - **Symptom:** Cases start via `CaseHubRuntime.startCase()` but no bindings fire. Case status stays RUNNING indefinitely. No error in logs.
  - **Cause:** `MemorySubCaseGroupRepository`, `MemoryPlanItemStore`, `MemoryReactivePlanItemStore` not listed in `quarkus.arc.selected-alternatives`. Engine falls back to no-op implementations.
  - **Fix:** Add all three to `quarkus.arc.selected-alternatives` in both `application.properties` and test `application.properties`. Commit `338aa16`.

- **Surefire retry masks real test failures (GE-20260601-8ff52b)**
  - **Symptom:** Test passes on retry after initial failure. CI green. The underlying issue — a timing race in async case completion — remains.
  - **Cause:** Maven Surefire `rerunFailingTestsCount` retries flaky tests silently. An `assumeTrue()` guard intended to skip tests when the engine bug (#410) is present interacts with Surefire retry: the assumption failure counts as a test failure, Surefire retries, the second run passes because the assumption guard succeeds.
  - **Fix:** Use `@Disabled("engine#410")` instead of `assumeTrue()` for known engine bugs. Reserve `assumeTrue()` for environment-conditional tests (Docker availability, OS-specific).

- **CaseDefinition forward lookup failure (engine#410)** — RESOLVED (commit 66a6e34, life#23)
  - **Symptom:** `SchedulerService.getCaseDefinition(caseKey)` returns null after the definition was successfully registered at startup. Integration tests fail with NPE in the scheduling path.
  - **Cause:** Mutable-hashCode map key in `DefaultCaseDefinitionRegistry`. Fixed with immutable `CaseKey` record + `RegistryEntry` map.
  - **Resolution:** engine#410 fixed. Integration tests re-enabled. Additional fixes needed: remove WAITING state assertion (engine doesn't transition to WAITING for humanTasks), add `QuarkusTransaction.requiringNew()` for Panache in Awaitility lambdas, filter WorkItems by `callerRef` to prevent cross-test interference, add `io.casehub.ledger.model` to qhorus PU packages for `WorkerDecisionEntry`.

### Pattern to replicate

1. Add `casehub-engine`, `casehub-engine-scheduler-quartz`, `casehub-engine-work-adapter`, `casehub-engine-blackboard`, and `casehub-engine-persistence-memory` to `app/pom.xml`. Add `casehub-engine-testing` at test scope.
2. Add `MemorySubCaseGroupRepository`, `MemoryPlanItemStore`, `MemoryReactivePlanItemStore` to `quarkus.arc.selected-alternatives` in both production and test `application.properties`.
3. Add Jandex index entries for all engine modules in test `application.properties`.
4. Create a `LifeCaseType`-equivalent enum in `api/` listing the case types exposed via REST. Sub-case-only types (e.g. family-vote, care-episode) are not LifeCaseTypes.
5. Create a `LifeCaseTracker` JPA entity on the default datasource tracking `{caseType, engineCaseId, status, createdAt, completedAt}`. Migration at V107+.
6. For each case definition: create a YAML file at `app/src/main/resources/{app}/` defining bindings, sentries, goals. Create a `@ApplicationScoped` YamlCaseHub subclass that loads the YAML and augments with domain workers via `getDefinition()` override. Create a companion fluent DSL class with a static `build()` method.
7. Workers use `FuncWorkflowBuilder.workflow().tasks(FuncDSL.function(...)).build()` — not raw lambdas. Workers that call CDI services wrap the proxy call inside `FuncDSL.function()`.
8. Create `LifeCaseService` following the three-phase pattern (PP-20260529-3ffe28): Phase 1 (`@Transactional`) validates and creates tracker; Phase 2 (no TX) calls `caseHub.startCase().join()`; Phase 3 (`@Transactional`) persists engineCaseId. Error recovery marks tracker FAILED.
9. Create `LifeCaseTrackerObserver` — `@ObservesAsync CaseLifecycleEvent("CaseCompleted")` updates tracker status. Pure infrastructure, no domain logic.
10. For cross-case signals: the completing worker queries `LifeCaseTracker` for active target cases by type and calls `CaseHubRuntime.signal(targetCaseId, key, value)`. No signal stored for later delivery — acceptable when the target is a periodic process.

---

## Layer 6 — Trust routing

**Participates in:** S5, S6
**Architectural pattern:** 🔲 at Layer 6 close
**Key protocols:** 🔲 at Layer 6 close
**Design refs:** 🔲 at Layer 6 close
**Status:** Pending
**Issue:** casehubio/life#7
**Navigation:** `git log --grep="#7" --oneline` (fill in at layer close)

### What it adds

Trust-weighted agent routing by household domain. `ActorTrustScore` updated from WorkItem
outcomes and commitment attestations (Bayesian Beta). Routing selects the agent with the
highest domain-specific trust score — deadline-reliability for contractor tasks, cost-accuracy
for finance, factual-accuracy for health.

---

## Layer 7 — + casehub-openclaw (OpenClaw integration)

**Participates in:** S6
**Architectural pattern:** 🔲 at Layer 7 close
**Key protocols:** 🔲 at Layer 7 close
**Design refs:** 🔲 at Layer 7 close
**Status:** Pending
**Issue:** casehubio/life#8
**Navigation:** `git log --grep="#8" --oneline` (fill in at layer close)

### What it adds

casehub-openclaw as the WorkerProvisioner. OpenClaw instances execute household skills:
banking API aggregation, Google Calendar integration, Home Assistant smart home control,
WhatsApp/SMS follow-up. The ChannelContextWindow delivers fresh Qhorus channel context to
each agent before execution. Bidirectional: CaseHub orchestrates via `/hooks/agent`; OpenClaw
heartbeat creates WorkItems when ambient conditions warrant action.
