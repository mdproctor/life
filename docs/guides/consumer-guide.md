# casehub-life -- Consumer Guide

> Personal life automation application on the CaseHub harness -- household management, health, finance, family obligations, elder care with formal SLA enforcement and tamper-evident audit.

**GitHub:** [casehubio/life](https://github.com/casehubio/life)
**Tier:** Application

---

## What It Is

casehub-life is a personal life automation application built on the CaseHub agentic harness. It coordinates household management, health, finance, family obligations, travel, legal compliance, contractor coordination, and elder care -- producing a formally tracked, SLA-enforced, tamper-evident record of life obligations. Serves as a field showcase and tutorial for developers evaluating CaseHub for personal automation.

## Module Structure

Two Maven modules under `casehub-life-parent`:

| Module | Artifact | Purpose |
|--------|----------|---------|
| `api/` | `casehub-life-api` | Domain model enums, DTOs, SPIs, descriptors. No framework dependencies. |
| `app/` | `casehub-life-app` | Quarkus application: JPA entities, REST resources, Flyway migrations, engine wiring, CBR, sentinels, frontend hosting. |

## Tutorial Layers

The tutorial structure follows the natural adoption sequence. Each layer adds one foundation module and makes its value tangible relative to the previous layer.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Domain baseline -- household domain model | Baseline: direct service calls, no SLA, no audit | **complete** (life#2) |
| 2 | casehub-work | No formal SLA on household tasks | **complete** (life#3) |
| 3 | casehub-qhorus | No commitment tracking; no oversight gates | **complete** (life#4) |
| 4 | casehub-ledger | No tamper-evident audit for health/financial decisions | **complete** (life#5) |
| 5 | casehub-engine | No multi-step workflow orchestration | **complete** (life#6 + subsequent) |
| 6 | Trust routing | No trust model for agent routing | **complete** (life#11) |
| 7 | casehub-openclaw | OpenClaw as agent provider; direct-call integration | **complete** (life#25, life#38, life#60) |
| 8 | Auth (casehub-platform-oidc) | No RBAC enforcement; risk thresholds role-agnostic | **complete** (life#40) |

## What It Owns

### Domain Model

- **`LifeDomain` enum** (8 values): `HEALTH`, `FINANCE`, `FAMILY_SCHEDULING`, `TRAVEL`, `LEGAL`, `CONTRACTOR_COORDINATION`, `ELDER_CARE`, `HOUSEHOLD`. Each carries a `LifeDomainDescriptor` that defines the domain's capability tag, template category, routing policy, SLA policy, and worker capabilities.
- **`LifeActorType` enum**: `AI_AGENT`, `HOUSEHOLD_PRINCIPAL`, `EXTERNAL_HUMAN`
- **`ExternalActor` entity**: contractors, carers, GPs -- contact details, actor type, GDPR-erasable
- **`LifeTaskContext` entity**: domain supplement persisted alongside each `WorkItemEntity` -- `domain`, `priority`, `externalActorId`, `jurisdiction` (ISO 3166-1/2), deadline context
- **`LifeCaseTracker` entity**: tracks engine case lifecycle -- `caseType`, `domain`, `status`, `engineCaseId`, `completedAt`
- **`LifeCommitmentRecord` entity**: persists commitment context -- task id, actor, channel, message correlation, domain, oversight key

### Capability Tags

8 capability tags declared in `LifeCapabilities`:

`household-management`, `health-coordination`, `financial-planning`, `family-scheduling`, `travel-planning`, `legal-deadline`, `contractor-coordination`, `elder-care`

### Trust Dimensions

4 trust dimensions declared in `LifeTrustDimensions`:

`deadline-reliability`, `cost-accuracy`, `factual-accuracy`, `proactive-alerting`

### LifeDomainDescriptor System

Each `LifeDomain` enum value carries a `LifeDomainDescriptor` implementation. The descriptor encodes per-domain configuration:

| Domain | Descriptor | Capability | Template Category | SLA Escalation | SLA Deadline |
|--------|------------|------------|-------------------|----------------|--------------|
| HEALTH | `HealthDomainDescriptor` | `health-coordination` | `health` | household-admin | 24h |
| FINANCE | `FinanceDomainDescriptor` | `financial-planning` | `finance` | household-admin | 48h |
| FAMILY_SCHEDULING | `FamilySchedulingDomainDescriptor` | `family-scheduling` | `family` | household-admin | 48h |
| TRAVEL | `TravelDomainDescriptor` | `travel-planning` | `travel` | household-admin | 48h |
| LEGAL | `LegalDomainDescriptor` | `legal-deadline` | `legal` | household-admin | 12h |
| CONTRACTOR_COORDINATION | `ContractorCoordinationDomainDescriptor` | `contractor-coordination` | `contractor` | household-admin | 48h |
| ELDER_CARE | `ElderCareDomainDescriptor` | `elder-care` | `elder-care` | household-admin | 12h |
| HOUSEHOLD | `HouseholdDomainDescriptor` | `household-management` | `household` | household-admin | 48h |

Each descriptor also declares `workerCapabilities()` and a `LifeRoutingPolicy` with trust threshold, minimum observations, borderline margin, fallback strategy, and rationale.

### CasePlanModel Definitions

8 YAML case definitions at `app/src/main/resources/life/`:

| Definition | Domain | Description |
|------------|--------|-------------|
| `appointment-cycle` | HEALTH | Book appointment, confirm, send pre-visit prep, attend (human task), record to ledger |
| `home-maintenance` | HOUSEHOLD | Schedule inspection, get quotes, issue commitment, monitor job, verify completion |
| `financial-review` | FINANCE | Gather data, analyse anomalies, escalate, oversight response, produce report |
| `travel-plan` | TRAVEL | Destination research, flight/hotel search, budget assessment, booking approval gate, confirm |
| `contractor-coordination` | CONTRACTOR_COORDINATION | Request quote via qhorus COMMAND, watchdog escalation, quote processing, admin approval gate, job monitoring, payment gate, record payment |
| `care-coordination` | ELDER_CARE | Needs assessment, care plan, health check, recurring care episodes (spawns sub-cases) |
| `care-episode` | ELDER_CARE | Child case of care-coordination: assess patient, provide care, record notes (human task), patient-status sentinel |
| `family-vote` | FAMILY_SCHEDULING | Single humanTask child case for M-of-N quorum decisions |

6 of these map to `LifeCaseType` enum values (the engine-startable case types): `APPOINTMENT_CYCLE`, `HOME_MAINTENANCE`, `FINANCIAL_REVIEW`, `TRAVEL_PLAN`, `CONTRACTOR_COORDINATION`, `CARE_COORDINATION`. The remaining 2 (`care-episode`, `family-vote`) are child-case definitions spawned by parent cases.

Each case definition includes:
- **CBR spec** (on the 6 primary types): features, weights, topK, minSimilarity, domain scope, timing
- **Capabilities**: agent-executed worker bindings
- **Sentinel capability**: persistent heartbeat monitor for long-running oversight
- **Human task bindings**: approval gates and recording steps with candidateGroups and expiry
- **Goals and completion conditions**: JQ-expression-based success criteria

### Household Permission Topology

3 groups declared in `HouseholdGroups`:

- `household-admin` -- full authority: approve major financial decisions, delegate tasks, configure SLAs
- `household-member` -- standard member: view all, action assigned tasks, request new tasks
- `household-junior` -- restricted: view own tasks and cases only, cannot approve financial decisions or create cases

All REST resources enforce `@RolesAllowed` based on these groups. OIDC authentication via `casehub-platform-oidc` provides `CurrentPrincipal.groups()` in production; dev/demo profiles disable OIDC.

### HouseholdActionType Risk Taxonomy

12 action types in `HouseholdActionType` classify consequential agent actions for oversight gating:

| Action Type | Gate Policy | Reversible | Candidate Groups |
|-------------|-------------|------------|------------------|
| `SPEND_PURCHASE` | AMOUNT_THRESHOLD | yes | admin |
| `SPEND_SUBSCRIPTION_CANCEL` | ALWAYS | no | admin |
| `SPEND_SUBSCRIPTION_MODIFY` | AMOUNT_THRESHOLD | yes | admin |
| `BOOKING_NONREFUNDABLE` | ALWAYS | no | admin |
| `BOOKING_REFUNDABLE` | AMOUNT_THRESHOLD | yes | admin |
| `HEALTH_APPOINTMENT_SPECIALIST` | ALWAYS | yes | admin |
| `HEALTH_APPOINTMENT_GP` | NEVER | yes | (none) |
| `HEALTH_MEDICATION_FLAG` | ALWAYS | no | admin, member |
| `CONTRACTOR_ENGAGE` | AMOUNT_THRESHOLD | yes | admin |
| `LEGAL_DOCUMENT_SUBMIT` | ALWAYS | no | admin |
| `ELDER_CARE_DECISION` | ALWAYS | yes | admin, member |

Gate policies: `ALWAYS` (unconditional gate), `AMOUNT_THRESHOLD` (gate when amount >= configured threshold), `NEVER` (always autonomous). RBAC-differentiated thresholds: admin gets elevated thresholds; junior always gates; unknown roles fail-secure to always-gate.

### Risk Policy Configuration

YAML-backed risk thresholds at `casehub/life/risk-policy.yaml`:

- Spend threshold: 100 (admin: 500)
- Contractor threshold: 200 (admin: 500)
- Booking threshold: 150 (admin: 300)
- Approval expiry: 24 hours

### Flyway Migrations

Two migration streams using separate datasources:

- **Default datasource** (`db/life/migration/`): V100-V111. `ExternalActor`, `LifeTaskContext`, `LifeCommitmentRecord`, `LifeCaseTracker`, WorkItem template seeds, escalation template seed. Shares the datasource with casehub-work migrations (V1-V31).
- **Qhorus datasource** (`db/life/ledger/migration/`): V2100-V2106. Ledger entry tables for health, financial, legal decisions and actor erasure. Shares the datasource with qhorus and ledger migrations.
- **Demo seeds** (`db/life/demo/`): V9001-V9002. Demo external actors and cases. Active only in `demo` profile.

## Layer 2 -- casehub-work Integration

- `POST /life-tasks` -- creates `WorkItemEntity` + `LifeTaskContext` atomically via `WorkItemTemplate` lookup
- `GET /life-tasks/{id}` -- returns task with visibility policy enforcement
- `LifeSlaBreachPolicy` -- implements `casehub-work` `SlaBreachPolicy` SPI; domain-aware escalation: resolves `LifeDomain` from `LifeTaskContext`, delegates to `LifeDomainDescriptor.slaPolicy()` for escalation group and deadline. If the escalation group is already in candidateGroups (tier 2), fails with `life-sla-exhausted`.
- Activated via `casehub.work.sla.breach-policy=life-sla-breach` in application.properties.

## Layer 3 -- casehub-qhorus Integration

- `LifeCommitmentRecord` entity -- persists commitment context (task id, actor, channel, message correlation, domain, oversight key)
- `LifeCommitmentStrategy` SPI implementations: `ContractorCommitmentStrategy`, `DelegationCommitmentStrategy`, `OversightGateStrategy`
- Channel topology: `life/delegation` (task assignment), `life/oversight` (human gates, COMMAND/RESPONSE only), `life/actor/ext-{id}` (per-actor channel)
- `LifeChannelInitializer` -- creates channels at startup with Watchdog conditions for `APPROVAL_PENDING`
- `LifeOversightResponseObserver` -- `MessageObserver` SPI; bridges oversight RESPONSE to task creation (fulfils pending commitment)
- `LifeWatchdogAlertObserver` -- creates escalation tasks when `APPROVAL_PENDING` watchdog fires on expired commitments; writes ledger entries for SLA breaches
- REST: `POST /life-tasks/{id}/commit` (`LifeCommitmentResource`), `POST /life-oversight-gates` (`LifeOversightGateResource`)

## Layer 4 -- casehub-ledger Integration

4 `LedgerEntry` subclasses (JOINED inheritance, qhorus PU):

- `HealthDecisionLedgerEntry` -- health decision audit trail
- `FinancialDecisionLedgerEntry` -- financial decision audit trail
- `LegalActionLedgerEntry` -- with `jurisdiction` field (ISO 3166-1/2); prefers task-level jurisdiction over tenant-wide config
- `ExternalActorErasureLedgerEntry` -- with `ledgerEntriesAffected` and `memoryRecordsErased` for self-contained Merkle-chained erasure proof

`DomainLedgerHandler` interface dispatches ledger writes by domain. 3 implementations: `HealthDomainLedgerHandler`, `FinanceDomainLedgerHandler`, `LegalDomainLedgerHandler`.

`LifeLedgerWriter` -- unified writer; single injection point for all ledger writes. Owns `sequenceNumber` computation and base field assembly.

`LifeOutcomeAttestationWriter` -- converts WorkItem outcomes and SLA breaches into `LedgerAttestation` records. Produces SOUND/FLAGGED verdicts and `deadline-reliability` dimension scores for tasks with deadlines.

`LifeGdprErasureService` -- dedicated GDPR erasure pipeline: PII nullification, memory erasure, `LedgerErasureService.erase()` tokenisation, `ExternalActorErasureLedgerEntry` write. Returns `ErasureResponse` with counts.

## Layer 5 -- casehub-engine Integration

8 `CaseHub` implementations backed by YAML CasePlanModels:

| CaseHub Class | Extends | YAML Path | Case Type |
|---------------|---------|-----------|-----------|
| `AppointmentCycleCaseHub` | `LifeTypedCaseHub` | `life/appointment-cycle.yaml` | APPOINTMENT_CYCLE |
| `HomeMaintenanceCaseHub` | `LifeTypedCaseHub` | `life/home-maintenance.yaml` | HOME_MAINTENANCE |
| `FinancialReviewCaseHub` | `LifeTypedCaseHub` | `life/financial-review.yaml` | FINANCIAL_REVIEW |
| `TravelPlanCaseHub` | `LifeTypedCaseHub` | `life/travel-plan.yaml` | TRAVEL_PLAN |
| `ContractorCoordinationCaseHub` | `LifeTypedCaseHub` | `life/contractor-coordination.yaml` | CONTRACTOR_COORDINATION |
| `CareCoordinationCaseHub` | `LifeTypedCaseHub` | `life/care-coordination.yaml` | CARE_COORDINATION |
| `CareEpisodeCaseHub` | `YamlCaseHub` | `life/care-episode.yaml` | (child case) |
| `FamilyVoteCaseHub` | `YamlCaseHub` | `life/family-vote.yaml` | (child case) |

`LifeTypedCaseHub` is the template method base class for primary case types. Subclasses override `configureCase(CaseDefinition)` to add workers and bindings. Agent descriptor registration is automatic. All workers use `agentWorker()` which builds an `Agent` with CBR context suffix injected into the system prompt.

`LifeCaseService` -- orchestrates case start using a three-phase pattern to avoid Agroal connection pool deadlock: (1) create tracker + build context, (2) start engine case, (3) persist engine case ID. Integrates CBR retrieval, plan adaptation, and trust feature enrichment before case start. Resolves the correct `LifeTypedCaseHub` via CDI `Instance<LifeTypedCaseHub>`.

`LifeCaseTrackerObserver` -- observes engine case lifecycle events to update `LifeCaseTracker` status.

### LifeAgent Enum

4 OpenClaw agent personas in `LifeAgent`:

| Agent | Persona | Slot | Domain |
|-------|---------|------|--------|
| HEALTH | `health-agent` | `casehubio/life/health` | Health domain coordination |
| HOME | `home-agent` | `casehubio/life/household` | Household maintenance |
| FINANCE | `finance-agent` | `casehubio/life/finance` | Financial review and governance |
| TRAVEL | `travel-agent` | `casehubio/life/travel` | Travel planning and booking |

Agent IDs follow the pattern `openclaw:{persona}@1`. All agents use OpenClaw's `/hooks/agent` direct-call integration (not heartbeat mode -- heartbeat-mode beans are CDI-excluded).

### Sentinel Heartbeat System

7 sentinel types configured via `LifeSentinelConfig` (`casehub.life.sentinel.capabilities.*`):

| Sentinel | Agent | Interval | Purpose |
|----------|-------|----------|---------|
| `contractor-sentinel` | HOME | 4h | Monitor contractor progress, contact for status updates |
| `maintenance-sentinel` | HOME | 4h | Read IoT sensors, alert on anomalies |
| `follow-up-sentinel` | HEALTH | 12h | Check prescriptions, referrals, test results |
| `care-quality-sentinel` | HEALTH | 12h | Verify scheduled vs completed care sessions |
| `patient-status-sentinel` | HEALTH | 24h | Read patient monitoring sensors, alert on changes |
| `anomaly-sentinel` | FINANCE | 24h | Scan transactions for anomalies, budget overruns |
| `booking-sentinel` | TRAVEL | 6h | Check booking status, price changes |

`LifeHeartbeatJob` (Quartz `Job`) executes sentinel agents: queries case context, gathers channel context via `LifeChannelContextProvider`, builds sentinel agent, executes, signals `sentinelReport` back into the case. Each sentinel has a typed response schema (e.g. `ContractorSentinelReport`, `AnomalySentinelReport`).

`LifeChannelContextProvider` merges recent qhorus channel messages (delegation, oversight, per-actor) into heartbeat context for cross-agent coordination. Config: `casehub.life.channel-context.message-limit` (default 10).

## Layer 6 -- Trust Routing

Trust routing configured per capability via YAML (`casehub/life/trust-routing.yaml`). Each capability scope has:

- `blend-factor` -- weight given to trust score vs random selection (0.0 = fully random, 1.0 = fully trust-based)
- `floor.*` -- minimum acceptable trust dimension scores

| Capability Scope | Blend Factor | Trust Floors |
|-----------------|--------------|--------------|
| health-coordination | 0.70 | factual-accuracy >= 0.60 |
| legal-deadline | 0.80 | deadline-reliability >= 0.70 |
| contractor-coordination | 0.60 | deadline-reliability >= 0.50, cost-accuracy >= 0.50 |
| financial-planning | 0.65 | cost-accuracy >= 0.60 |
| elder-care | 0.70 | (none) |
| travel-planning | 0.50 | (none) |
| household-management | 0.40 | (none) |
| family-scheduling | 0.40 | (none) |

`LifeActionRiskClassifier` (implements `ActionRiskClassifier`, `@RiskClassifier` qualifier) classifies consequential actions using `HouseholdActionType` policy plus YAML thresholds. RBAC-aware: admin gets elevated thresholds, junior always gates, unknown roles fail-secure.

`LifeTrustRoutingPolicyProvider` maps domain routing policies from `LifeDomainDescriptor` to engine's trust routing configuration.

## CBR Integration

6 domain feature schemas registered at startup by `LifeCbrFeatureSchemaRegistrar` (`@Observes StartupEvent`): `contractor-coordination`, `home-maintenance`, `appointment-cycle`, `care-coordination`, `financial-review`, `travel-plan`.

Dual-path outcome recording: `LifeRoutingOutcomeRecorder` (implements `RoutingOutcomeRecorder`) records agent-routing outcomes per worker execution; `LifeCaseOutcomeCbrWriter` (implements `CaseOutcomeObserver`) records case-level outcomes on terminal state. Both write to `CbrCaseMemoryStore`.

Dual-path architecture in `LifeCaseService.startCase()`: calls `cbrSuggestionService.retrieveForAdaptation()`, injects `cbrCalibration` and `adaptedPlan` into initial context, fires `CbrAdaptationRecorded` event. Trust feature enrichment via `LifeTrustFeatureEnricher`.

## REST API

### Cases (`LifeCaseResource`, `/life-cases`)

- `POST /life-cases` -- start a new case. Requires `household-admin` or `household-member`. Returns `LifeCaseResponse`.
- `GET /life-cases` -- list cases. Filterable by `domain`, `status` (ACTIVE/COMPLETED/FAILED), `caseType`. Paged. All groups can read (junior sees filtered results via `LifeCaseVisibilityPolicy`).
- `GET /life-cases/{id}` -- case detail.

### Tasks (`LifeTaskResource`, `/life-tasks`)

- `POST /life-tasks` -- creates `WorkItemEntity` + `LifeTaskContext` atomically. Admin or member only.
- `GET /life-tasks/{id}` -- returns task with `LifeTaskVisibilityPolicy` enforcement.

### Commitment (`LifeCommitmentResource`, `/life-tasks/{id}/commit`)

- `POST /life-tasks/{id}/commit` -- apply commitment to task. Admin or member only.

### Oversight (`LifeOversightGateResource`, `/life-oversight-gates`)

- `POST /life-oversight-gates` -- request approval via oversight channel. Admin or member only.

### Analytics (`LifeAnalyticsResource`, `/analytics`)

- `GET /analytics/cases` -- `CaseStatisticsResponse` (per-type stats, resolution time percentiles, completion rate). Filterable by caseType.
- `GET /analytics/sla` -- `SlaComplianceResponse` (breach count, compliance rate, avg breach latency). Filterable by domain.
- `GET /analytics/trust` -- `TrustAnalyticsResponse` (actor trust score summaries, dimension averages, lowest-scoring actors).

### Pending Actions (`PendingActionsResource`, `/pending-actions`)

- `GET /pending-actions` -- paged, filterable by `domain`, `candidateGroup`, `dueSoonHours` (default 24). Returns `PendingActionResponse` with urgency classification (OVERDUE, DUE_SOON, NORMAL, NO_DEADLINE).

### External Actors (`ExternalActorResource`, `/external-actors`)

- `POST /external-actors` -- create actor. Admin or member.
- `GET /external-actors` -- search by name, actorType, contactMethod, erasedOnly. Paged.
- `GET /external-actors/{id}` -- actor detail.
- `PUT /external-actors/{id}` -- update actor. Admin only.
- `DELETE /external-actors/{id}` -- delete actor. Admin only.
- `DELETE /external-actors/{id}/personal-data` -- GDPR erasure. Admin only. Returns `ErasureResponse`.
- `GET /external-actors/{id}/tasks` -- list tasks for actor. Admin or member.
- `GET /external-actors/{id}/trust-history` -- actor trust score history. Paged.
- `GET /external-actors/{id}/activity` -- actor activity timeline. Paged.

### SSE Events (`LifeEventStreamApi`, `/events`)

- `GET /events/inbox` -- SSE stream of work item events: `WORK_ITEM_CREATED`, `WORK_ITEM_UPDATED`, `WORK_ITEM_COMPLETED`, `SLA_BREACH`. All groups.
- `GET /events/cases` -- SSE stream of case events: `CASE_STARTED`, `CASE_COMPLETED`, `CASE_FAULTED`. Admin or member.
- `GET /events/stream` -- SSE stream of all events. All groups.

## Visibility Policy SPIs

### LifeTaskVisibilityPolicy

`LifeTaskVisibilityPolicy` interface (`api/spi/`): `boolean isVisible(LifeTaskResponse task, String actorId, Set<String> groups)`.

- **`DefaultLifeTaskVisibilityPolicy`** (`@DefaultBean`): always returns `true` (permissive).
- **`JuniorLifeTaskVisibilityPolicy`** (`@Alternative @Priority(1)`): non-junior principals pass unconditionally; junior principals (`HouseholdGroups.JUNIOR`) visible only if assigned or in candidate pool.

### LifeCaseVisibilityPolicy

`LifeCaseVisibilityPolicy` interface (`api/spi/`): `boolean isVisible(LifeCaseResponse caseResponse, String actorId, Set<String> groups)`.

- **`DefaultLifeCaseVisibilityPolicy`** (`@DefaultBean`): always returns `true`.
- **`JuniorLifeCaseVisibilityPolicy`** (`@Alternative`, selected via `quarkus.arc.selected-alternatives`): junior principals see only cases relevant to them.

## Frontend -- life-ui

Lit 3.x single-page application served via Quarkus Quinoa (2.8.3). Hash-routed app shell with two views.

| View | What it shows |
|------|--------------|
| Dashboard (`home-view`) | KPI metrics (active cases by domain, SLA compliance, pending actions), case statistics |
| Inbox (`inbox-view`) | Work-item-workbench composition -- filterable inbox with detail pane |

**Build:** Vite with aliases resolving `@casehubio/agency-ui-*` and `@casehubio/pages-*` from Maven SNAPSHOT artifacts (extracted to `.casehub-packages/` via `mvn initialize`). No npm cross-repo `file:` references.

**Profiles:** Quinoa disabled by default (`quarkus.quinoa.enabled=false`) and in tests. Enabled in `dev` and `demo` profiles only.

**Demo mode:** `quarkus.profile=demo` -- H2 in-memory, Flyway demo seeds at `db/life/demo/` (V9001-V9002), OIDC disabled.

## Skill Tier Declarations

Application.properties declares which external capability tier each skill uses:

| Skill | Tier | Note |
|-------|------|------|
| messaging | native | CaseHub MCP tool (send_chat) |
| iot | native | CaseHub MCP tool (iot_get_state) |
| calendar | native | CaseHub MCP tool (calendar_list_events) |
| banking | openclaw | Community skill (bank_get_transactions) |

## Dependencies

```
casehub-life
  -> casehub-platform-oidc         (Layer 8: OidcCurrentPrincipal, @RolesAllowed enforcement)
  -> casehub-platform-config       (YAML-backed PreferenceProvider for trust routing + risk policy)
  -> casehub-platform-expression   (JQ evaluator for engine CDI beans)
  -> casehub-platform              (runtime: MockPreferenceProvider @DefaultBean)
  -> casehub-openclaw-core         (OpenClaw LLM client)
  -> casehub-openclaw-casehub      (CaseHub-OpenClaw integration -- heartbeat beans CDI-excluded)
  -> casehub-engine                (Layer 5: CasePlanModel orchestration)
  -> casehub-engine-scheduler-quartz (Quartz worker execution)
  -> casehub-work-engine-adapter   (HumanTaskScheduleHandler + WorkItemLifecycleAdapter)
  -> casehub-engine-planning       (Planning capabilities -- was casehub-engine-blackboard before life#80)
  -> casehub-engine-persistence-memory (In-memory case instance storage)
  -> casehub-engine-ledger         (Engine-ledger integration)
  -> casehub-ledger                (Layer 4: Merkle audit, GDPR erasure, trust scoring)
  -> casehub-work                  (Layer 2: WorkItems with SLA and escalation)
  -> casehub-qhorus                (Layer 3: commitment lifecycle, oversight channel)
  -> casehub-connectors-core       (household notifications)
  -> casehub-neocortex-memory-api  (CBR: memory type API)
  -> casehub-neocortex-memory      (runtime: CbrCaseMemoryStore)
  -> casehub-iot-api               (IoT sensor integration for sentinels)
  -> casehub-life-api              (domain model)
```

## What It Does NOT Own

Foundation capabilities that casehub-life consumes but does not implement:

- Trust scoring -- casehub-ledger (Bayesian Beta computation from attestations)
- Commitment lifecycle -- casehub-qhorus
- Case engine and `CasePlanModel` execution -- casehub-engine
- WorkItem inbox with SLA -- casehub-work
- Notification delivery -- casehub-connectors
- LLM agent execution -- casehub-openclaw
- IoT sensor state -- casehub-iot-api
- CBR memory storage -- casehub-neocortex-memory
