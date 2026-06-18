# casehub-life Workspace

**Name:** casehub-life

**Physical path:** /Users/mdproctor/claude/casehub/life/CLAUDE.md
**Symlinked at:** /Users/mdproctor/claude/public/casehub/life/CLAUDE.md
**Project repo:** /Users/mdproctor/claude/casehub/life
**Workspace:** /Users/mdproctor/claude/public/casehub/life
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/life` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/life`) — methodology artifacts: handover, blog, specs, plans, ADRs
- **Project repo** (`/Users/mdproctor/claude/casehub/life`) — source code

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace


## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Peer Repos — Hard Boundary

**This session owns exactly two repos: the workspace and the project repo.**
Every other casehubio repo is a peer repo with its own Claude session.

Peer repos (never commit or push to these from this session):
- `/Users/mdproctor/claude/casehub/parent` and all paths under it
- `/Users/mdproctor/claude/casehub/engine`
- `/Users/mdproctor/claude/casehub/ledger`
- `/Users/mdproctor/claude/casehub/work`
- `/Users/mdproctor/claude/casehub/qhorus`
- `/Users/mdproctor/claude/casehub/connectors`
- `/Users/mdproctor/claude/casehub/devtown`
- `/Users/mdproctor/claude/casehub/aml`
- `/Users/mdproctor/claude/casehub/clinical`
- `/Users/mdproctor/claude/casehub/openclaw` (casehub-openclaw)
- Any other sibling directory under `/Users/mdproctor/claude/casehub/`

**When a cross-repo doc change is needed** (e.g. `docs/PLATFORM.md`,
`docs/repos/casehub-life.md` in the parent): file a GitHub issue on
`casehubio/parent` describing the change — never edit or commit directly.

Skills that check this (implementation-doc-sync, work-end, handover) must
read this section before deciding where to commit doc changes.

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | epic journal stays in workspace |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# casehub-life — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
../parent/docs/PLATFORM.md
```

**Foundation repo deep-dives:**
- casehub-engine: `../parent/docs/repos/casehub-engine.md`
- casehub-ledger: `../parent/docs/repos/casehub-ledger.md`
- casehub-work: `../parent/docs/repos/casehub-work.md`
- casehub-qhorus: `../parent/docs/repos/casehub-qhorus.md`
- casehub-connectors: `../parent/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2

---

## Agentic Harness Goals

**Read first:** `../parent/docs/AGENTIC-HARNESS-GUIDE.md`

**Goal:** Production-grade personal life automation harness demonstrating that household coordination, health management, family obligations, and legal compliance are structurally better served by a formal accountability layer than by best-effort automation tools.

**Architecture record:** `ARC42STORIES.MD` is the primary architecture document (Arc42Stories v0.1, CaseHub Profile). `LAYER-LOG.md` retained as source-of-truth draft. A layer is not complete until its §9.4 entry in `ARC42STORIES.MD` is written. See `../parent/docs/arc42stories-spec.md` and `../parent/docs/arc42stories-casehub-profile.md`.

---

## What This Project Is

`casehub-life` is a **personal life automation harness** built on the CaseHub platform foundation. It coordinates household management agents, health coordination agents, financial governance agents, elder/family care agents — producing a formally tracked, SLA-enforced, tamper-evident record of life obligations and decisions.

This is an **application layer**, not a framework. The foundation (casehub-engine, casehub-qhorus, casehub-ledger, casehub-work, casehub-connectors) provides coordination, accountability, audit, and compliance primitives. casehub-life provides the personal life domain logic: what a household task is, how a care coordination cycle proceeds, which family members have decision authority, and how a major financial decision requires human sign-off before action.

### Why Personal Life Automation

Personal life has domains where CaseHub's accountability properties are structurally required: contractor commitments routinely go unfulfilled without a Watchdog, health follow-ups are forgotten without SLA enforcement, family obligations evaporate without commitment tracking, legal deadlines arrive without escalation.

### Accountability Properties Delivered

| Domain | Without CaseHub | With casehub-life |
|---|---|---|
| Contractor follow-up | Silent if commitment not met | Commitment + Watchdog; automated SMS via messaging skill if no ETA |
| Health appointment | Reminder sent, then forgotten | WorkItem with SLA deadline; escalation to named GP |
| Monthly grocery SLA | May fail silently | WorkItem with deadline and escalation |
| Major financial decision | No approval gate | Oversight channel; human RESPONSE required before any action |
| Legal deadline | Calendar reminder only | WorkItem with hard deadline + tamper-evident ledger record |

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of household domains, health protocols, personal finance rules, or family obligation tracking, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

---

## Reference Documents

| Document | What it covers |
|----------|---------------|
| `../parent/docs/AGENTIC-HARNESS-GUIDE.md` | Goals, what to produce, retroactive work instructions, layer maintenance |
| `docs/specs/life-automation.md` | Life automation domain, use case analysis, key domains |
| `docs/specs/life-actor-model.md` | Actor model — ExternalActor types, trust dimensions, agent routing |
| `../parent/docs/specs/2026-05-25-openclaw-casehub-integration.md` | Research spec — OpenClaw as WorkerProvisioner in casehub-life context |
| `../parent/docs/PLATFORM.md` | Platform architecture, boundary rules, capability ownership |
| `../garden/docs/protocols/casehub/HARNESS-INDEX.md` | CaseHub app protocols |
| `../garden/docs/protocols/universal/INDEX.md` | Universal Java/Quarkus protocols |
| `docs/protocols/casehub-life/INDEX.md` | Life-specific standing rules (e.g. platform-config YAML registration — PP-20260607-4c59f4) |

---

## Design Phase References

Read these **before designing**, not after. The concern column tells you when each applies.

### Domain model and API design

| Concern | Read first |
|---------|-----------|
| Designing a new entity, record, or SPI | `docs/specs/life-automation.md` — does life already own this? `PLATFORM.md` capability ownership table — does the foundation own it? |
| Deciding api vs app module placement | `PLATFORM.md` persistence module split rule — JPA-free api, JPA in app. |
| Naming capability tags or trust dimensions | This CLAUDE.md §What This Project Owns — existing tag and dimension names |
| Choosing which life domain a feature belongs to | `LifeDomain` enum values in `api/` — routing logic ties capability tags to domains |

### Layer design

| Concern | Read first |
|---------|-----------|
| Deciding which layer a feature belongs in | Foundation Layers section below — layer ownership boundaries |
| Documenting a completed layer | ARC42STORIES.MD §9.4 — write the layer entry before closing the issue (LAYER-LOG.md is the draft) |

### Foundation integration

| Concern | Read first |
|---------|-----------|
| Using casehub-work (WorkItem, SLA, escalation) | `../parent/docs/repos/casehub-work.md` |
| Using casehub-qhorus (COMMAND/RESPONSE/DONE/DECLINE, Commitment) | `../parent/docs/repos/casehub-qhorus.md` |
| Using casehub-ledger (Merkle audit, GDPR erasure, LedgerEntry subclasses) | `../parent/docs/repos/casehub-ledger.md` |
| Using casehub-engine (CasePlanModel, bindings, sub-cases) | `../parent/docs/repos/casehub-engine.md` |
| Using casehub-openclaw (WorkerProvisioner, skill ecosystem) | Research spec `../parent/docs/specs/2026-05-25-openclaw-casehub-integration.md` |
| Boundary check — foundation or life? | `PLATFORM.md` boundary rules; Layering Rule section in this file |

### Persistence and migrations

| Concern | Read first |
|---------|-----------|
| Writing a new Flyway migration | `../garden/docs/protocols/universal/flyway-migration-rules.md` — naming, H2 MODE=PostgreSQL |
| Assigning a migration version number | Domain migrations start at V100 (casehub-work occupies V1–V21+) |
| Adding casehub-ledger to the classpath | PP-20260524-10efef — add `classpath:db/ledger/migration` to Flyway locations |
| Adding casehub-qhorus to the classpath | Add `classpath:db/qhorus/migration` to Flyway locations |
| Extending LedgerEntry (adding a tamper-evident subclass) | `casehub-ledger.md` Consumer Pattern section — JOINED inheritance, V2001+ migration. **Override `domainContentBytes()`** — join all persistent fields with `\|` separator, return as UTF-8 bytes. Build-time validator enforces this; test in same package (protected access). See `LedgerEntryDomainContentBytesTest`. |

### Testing

| Concern | Read first |
|---------|-----------|
| Writing a `@QuarkusTest` | `../garden/docs/protocols/universal/quarkus-test-database.md` — H2 MODE=PostgreSQL, datasource config |
| Testing SPI wiring | `spi-testing-alternative-inner-classes` protocol |
| Testing a WorkItem SLA | WorkItem test patterns in `casehub-work.md` |
| Seeding WorkItemTemplates in tests | Flyway is disabled in tests (`migrate-at-start=false`). Seed templates via `LifeTestFixtures.seedStandardTemplates()` and/or `LifeTestFixtures.seedEscalationTemplate()` from `@BeforeEach @Transactional`. Canonical UUIDs 001–004; idempotency guard by template name. **Always set `tenancyId` to `"278776f9-e1b0-46fb-9032-8bddebdcf9ce"` — V35 adds NOT NULL with no H2 default.** See `app/src/test/java/io/casehub/life/app/LifeTestFixtures.java`. |
| Non-JPA plain SQL tables in H2 tests | Hibernate `drop-and-create` only creates JPA entity tables. Plain SQL tables (e.g. `ledger_subject_sequence`) must be created via `quarkus.hibernate-orm."<pu>".sql-load-script` pointing to a SQL file with `CREATE TABLE IF NOT EXISTS`. See `app/src/test/resources/import-qhorus.sql` and PP-20260609-e2c3a1. **When the ledger SNAPSHOT changes `ledger_subject_sequence` schema** (e.g. adding `tenancy_id` column in composite PK), update `import-qhorus.sql` to match — mismatch causes "Column not found" HTTP 500 on any ledger write. |
| Testing async CDI observers | Call the observer method directly through the injected CDI proxy — bypasses event dispatch and debounce. Method-level `@Transactional(REQUIRES_NEW)` is honoured via CDI proxy. Do NOT use `@TestTransaction` on the test method — it blocks the REQUIRES_NEW from seeing committed setup records. See GE-20260529-9f3557 and `LifeWatchdogAlertObserverTest`. |
| Testing ledger writers (unit) | Mock `LedgerEntryRepository` with Mockito. Do NOT assert on `entry.id` or `entry.occurredAt` — these are set by `LedgerEntry.@PrePersist` which is bypassed in mocked tests. See `LifeLedgerWriterTest`. |
| Multi-PU entity package placement | Ledger subclass entities must NOT be sub-packages of `io.casehub.life.app.entity` (default PU). Use `io.casehub.life.app.ledger` — Quarkus uses prefix matching for PU assignment; sub-packages of a default-PU package get assigned to the default PU, causing cross-PU association errors with `LedgerEntry.supplements`. |
| Testing engine case definitions | Definition tests (verify YAML loads, binding count, goal count, capabilities) are pure unit tests — no Quarkus startup needed. Integration tests (start case → workers execute → goals met) require @QuarkusTest. Use `CaseIntegrationTestSupport` helpers: `startCase`, `awaitWorker`, `completeHumanTask`, `awaitCaseCompleted`. Pattern: `QuarkusTransaction.requiringNew()` for Panache queries inside Awaitility lambdas; filter WorkItems by `callerRef` prefix to avoid cross-test interference. Pure humanTask cases (e.g. family-vote) skip `startCase` — no workers to await. |
| Testing attestation pipeline (unit) | Mock `LedgerEntryRepository` with Mockito. Verify verdicts (SOUND/FLAGGED), capability tags, dimension scores. Guard: CREATED events produce no attestation. See `LifeOutcomeAttestationWriterTest`. |
| Trust routing policy provider | `@QuarkusTest` — inject `TrustRoutingPolicyProvider`, verify capability→domain resolution, YAML blend factors and quality floors. See `LifeTrustRoutingPolicyProviderTest`. |
| Testing `ActionRiskClassifier` | Unit tests: mock `PreferenceProvider`, use `lenient().when(...)` in `@BeforeEach` for shared stubs — NEVER/unknown types skip `resolve()` entirely, triggering `UnnecessaryStubbingException` in strict mode. `@QuarkusTest`: inject `@RiskClassifier Instance<ActionRiskClassifier>` to verify CDI qualifier wiring. See `LifeActionRiskClassifierTest`, `LifeActionRiskClassifierQuarkusTest`. |
| Testing `HumanTaskTarget.candidateGroups()` | Returns sealed `ListEvaluator`. Pattern: `ht.candidateGroups() instanceof ListEvaluator.StaticList sl && sl.values().contains("group-name")`. Import `io.casehub.api.model.evaluator.ListEvaluator`. |
| Engine CDI wiring | `quarkus.arc.selected-alternatives` must include `MemorySubCaseGroupRepository`, `MemoryPlanItemStore`, `MemoryReactivePlanItemStore` from casehub-engine-persistence-memory (GE-20260531-1e51d4). |
| Testing LLM-backed workers (`AppointmentCycleCaseHub`) | Use `@Alternative @Priority(10) @ApplicationScoped` test CDI bean (e.g. `TestLifeOpenClawChatModelProvider`) registered in `quarkus.arc.selected-alternatives` in test config. **Never use `@InjectMock`** for beans used in `augment()` — Mockito's CDI proxy reset between test classes triggers a Quarkus restart, re-registering Vert.x codecs and failing ALL subsequent `@QuarkusTest` classes (engine#536). The test bean must be request-aware: detect the rendered user message text to serve correct responses for both success and decline paths. `augmentedDefinition` is baked once per JVM lifetime — whichever test class runs first bakes the ChatModel. See `TestLifeOpenClawChatModelProvider`. |
| @QuarkusTest status (2026-06-18) | All `@QuarkusTest` classes currently skip due to pre-existing Quarkus augmentation phase bug: `BlackboardEventCodecRegistrar.onStart()` registers Vert.x codecs during augmentation, then fails when the application tries to start again for tests. Filed as engine#536. Unit tests and non-Quarkus tests pass. |
| Engine-ledger PU packages | `io.casehub.ledger.model` must be in the qhorus PU packages — this is `casehub-engine-ledger`'s entity package (e.g. `WorkerDecisionEntry`, `CaseLedgerEntry`), distinct from `io.casehub.ledger.runtime` (casehub-ledger base). Without it: `Unknown entity type 'WorkerDecisionEntry' does not belong to this persistence unit`. |
| SubCase M-of-N in YAML | M-of-N fields (groupId, totalInGroup, requiredCount) are DSL-only — not YAML-supported. Add via Java augmentation in YamlCaseHub.getDefinition() (GE-20260531-d896bf). |

---

## What This Project Owns

### Domain Model

**Life domain entities:**
- `LifeDomain` enum — `HOUSEHOLD`, `HEALTH`, `FINANCE`, `FAMILY_SCHEDULING`, `TRAVEL`, `LEGAL`, `CONTRACTOR_COORDINATION`, `ELDER_CARE`
- `ExternalActor` — contractor, doctor, service provider, or institution: `{name, actorType, contactMethod, contactValue, gdprErasedAt}` — the one genuine JPA domain entity
- `LifeTaskContext` — domain supplement for foundation WorkItems: `{workItemId, domain, externalActorId, recurrence}` — carries life-specific context that has no foundation home

Note: `HouseholdTask`, `LifeGoal`, `LifeEvent` were removed in Layer 2 — they duplicated foundation primitives (WorkItem, CaseInstance, LedgerEntry). See `docs/specs/2026-05-27-layer2-casehub-work-sla.md` and parent#79.

**Layer 3 additions:**
- `LifeCommitmentRecord` — supplement to qhorus native `Commitment`, keyed by `correlationId`; tracks DELEGATION/CONTRACTOR/OVERSIGHT commitment mode and status. `workItemId` is null for OVERSIGHT until household-admin RESPONSE fulfills the gate.
- `LifeCommitmentStrategy` — internal app/ SPI (not api/ — context types reference JPA entities); three implementations: `DelegationCommitmentStrategy`, `ContractorCommitmentStrategy`, `OversightGateStrategy`.
- Channel topology: `life/delegation` (shared, family delegation), `life/oversight` (shared, COMMAND+RESPONSE only), `life/actor/{externalActorId}` (per-actor, contractor commitments). One APPROVAL_PENDING Watchdog per channel.

**Layer 4 additions:**
- `HealthDecisionLedgerEntry`, `FinancialDecisionLedgerEntry`, `LegalActionLedgerEntry`, `ExternalActorErasureLedgerEntry` — four JOINED-inheritance `LedgerEntry` subclasses in `io.casehub.life.app.ledger` (qhorus PU). Per-domain required fields; `@DiscriminatorValue` discriminates.
- `LifeDecisionEventType` — enum: `CREATED`, `SLA_BREACH`, `COMPLETED`. Internal to `app/`.
- `LifeLedgerWriter` — unified writer service; owns `sequenceNumber` computation and base field assembly. `@PrePersist` on `LedgerEntry` handles `id` and `occurredAt`.
- `LifeDecisionLedgerObserver` — CDI observer for `SlaBreachEvent` (HEALTH/LEGAL/FINANCE SLA_BREACH) and `WorkItemLifecycleEvent` (COMPLETED only). `@Transactional(REQUIRES_NEW)`.
- GDPR Art.17 erasure: `DELETE /external-actors/{id}/personal-data` — nullifies PII, writes `ExternalActorErasureLedgerEntry`. Guards: 404/409 already-erased/409 active-tasks.
- actorId convention: `"life-system"` for system events, `"household-admin"` for GDPR erasure (until auth wired).

**Layer 5 additions:**
- `LifeCaseTracker` — JPA entity tracking active engine cases by type for cross-case signal lookup: `{id, caseType, engineCaseId, status, createdAt, completedAt}`. Default datasource.
- `LifeCaseType` enum in `api/`: TRAVEL_PLAN, HOME_MAINTENANCE, CARE_COORDINATION, APPOINTMENT_CYCLE, CONTRACTOR_COORDINATION, FINANCIAL_REVIEW. FAMILY_VOTE is not a LifeCaseType — spawned only as sub-case.
- `LifeCaseStatus` enum in `api/`: ACTIVE, COMPLETED, FAILED.
- `LifeCaseService` — three-phase case start (PP-20260529-3ffe28). Direct injection of each YamlCaseHub, switch on LifeCaseType.
- `LifeCaseTrackerObserver` — `@ObservesAsync CaseLifecycleEvent` updates tracker status.
- `LifeDecisionLedgerObserver` — refactored: domain resolution now uses `WorkItem.scope` Path (primary), LifeTaskContext (fallback).
- 8 `YamlCaseHub` subclasses + 8 fluent DSL companions in `io.casehub.life.app.engine`. Workers use quarkus-flow FuncDSL per PP-20260531-worker-func-exec.
- 8 YAML case definitions at `app/src/main/resources/life/`.
- `POST /life-cases` endpoint — `LifeCaseResource`.
- Scope retrofit: WorkItem scope changed from `"life"` to `"casehubio/life/{domain}"` (hierarchical Path format).

**Layer 6 additions:**
- `LifeActorIds` — `api/` utility for `life-actor:{uuid}` actorId convention mapping ExternalActor UUIDs to ledger actorIds.
- `LifeOutcomeAttestationWriter` — attestation pipeline: converts WorkItem outcomes and SLA breaches into `LedgerAttestation` records with verdict (SOUND/FLAGGED), capability tag (domain-derived), and `deadline-reliability` dimension score.
- `LifeTrustRoutingPolicyProvider` — `TrustRoutingPolicyProvider` SPI implementation with 32-entry fine-grained capability→domain mapping and 8 domain routing policies (threshold, minObservations, borderlineMargin, fallbackType, rationale).
- `LifeTrustRoutingPolicyKeys` — `PreferenceKey` constants for YAML trust routing config (blend-factor + 4 dimension floor keys).
- `DoublePreference` — `SingleValuePreference` record for YAML double values.
- `LifeRoutingPolicy` — domain routing policy record (code-level design decisions).
- `trust-routing.yaml` — YAML config at `casehub/life/trust-routing.yaml` for blend factors and quality floors per domain.
- `TrustProfile` — nested record on `ExternalActorResponse` enriched from `TrustGateService` (globalScore, dimensionScores, capabilityScores).
- ActorId convention: `life-actor:{uuid}` for ExternalActor behaviour in ledger entries; `"life-system"` for system actions.

**Layer 7 additions (partial — AgentExec wiring, life#25):**
- `LifeOpenClawChatModelProvider` — `app/engine/agent/` `@ApplicationScoped ChatModelProvider`; targets
  OpenClaw's `/v1/chat/completions` via reflection on `OpenAiChatModel.builder()`. Temporary pending
  casehubio/engine#527 (add `baseUrl` to `OpenAiChatModelProvider` in engine-api). Config:
  `casehub.life.openclaw.api-url` (required, no default), `casehub.life.openclaw.api-key` (default "no-key"),
  `casehub.life.openclaw.timeout-seconds` (default 120). chatModelProvider.get() called once per JVM lifetime.
- `BookingResult` — `app/engine/agent/` Java record; structured output schema for the booking agent.
  `AgentBuilder.responseSchema(BookingResult.class)` derives the JSON schema.
- `OpenClawHealthProbe` — `app/engine/agent/` `@IfBuildProfile("prod") @ApplicationScoped`; fires TCP
  connectivity probe on `casehub.life.openclaw.api-url` at startup. Suppressed in tests and dev mode.
- `TestLifeOpenClawChatModelProvider` (src/test only) — `@Alternative @Priority(10) @ApplicationScoped`;
  returns a request-aware `ChatModel` (detects "unavailable" in rendered user message → decline path).
  Registered via `quarkus.arc.selected-alternatives` in test config. Avoids `@InjectMock` which causes
  Quarkus CDI restart between test classes and re-registers blackboard event bus codecs (engine#536).
- `casehub.life.tenancy-id` — required config property (no default); must be set in deployment environment
  (absent from production `application.properties`; present in test config with canonical test UUID).
- `langchain4j-open-ai 1.14.1` — added as `runtime` dep in `app/pom.xml`.
- AgentDescriptor convention: `{model-family}:{persona}@{major}` per `docs/specs/life-actor-model.md`.
  Example: `"openclaw:health-agent@1"`. Must match casehub-openclaw-casehub config map key when
  WorkerProvisioner is wired (life#37).

**Layer 7 additions (partial — risk classification):**
- `HouseholdActionType` — `api/` enum: 11 action types, `GatePolicy` (ALWAYS/AMOUNT_THRESHOLD/NEVER),
  `ThresholdCategory` (SPEND/BOOKING/CONTRACTOR), `reversible`, `candidateGroups`.
  `actionType()` / `fromActionType()` provide type-safe `PlannedAction` construction.
- `HouseholdGroups` — `api/` string constants: `household-admin`, `household-member`, `household-junior`.
- `LifeRiskPolicyKeys` — `app/routing/` `PreferenceKey` constants. Namespace: `casehubio.life.risk-policy`.
  spend.threshold (100.0), contractor.threshold (200.0), booking.threshold (150.0), approval.expires-hours (24.0).
- `LifeActionRiskClassifier` — `app/routing/` `@ApplicationScoped @RiskClassifier`; implements
  `ActionRiskClassifier` from casehub-engine-api. Discovered by engine's `ChainedReactiveActionRiskClassifier`
  via `@Inject @RiskClassifier Instance<ActionRiskClassifier>`.
- `risk-policy.yaml` — YAML config at `casehub/life/risk-policy.yaml`; single scope `casehubio/life/risk-policy`.
- scope convention: `"casehubio/life/oversight"` — verify against engine#437 once engine docs clarify scope→channel mapping.

**Capability tags:**
- `household-management` — routine household coordination: grocery ordering, maintenance scheduling, contractor liaison
- `health-coordination` — appointment booking, medication reminders, follow-up tracking, GP escalation
- `financial-planning` — budget review, purchase research, decision gate for major expenditure
- `family-scheduling` — shared family calendar, obligation distribution, delegation tracking
- `travel-planning` — trip research, itinerary coordination, booking confirmations
- `legal-deadline` — tax filing deadlines, contract renewals, compliance obligations
- `contractor-coordination` — quote tracking, job completion follow-up, payment approval gates
- `elder-care` — care scheduling, health monitoring, family delegation for dependent care

**Trust dimensions:**
- `deadline-reliability` — track record of meeting committed deadlines (contractors, service providers)
- `cost-accuracy` — accuracy of quoted vs actual costs (financial agents, contractor quotes)
- `factual-accuracy` — reliability of information retrieval (health advice agents, legal deadline agents)
- `proactive-alerting` — track record of surfacing risks before they become crises

### CasePlanModels

- `appointment-cycle` — book appointment, confirm, send pre-visit prep, follow-up after, record outcome
- `home-maintenance-cycle` — schedule inspection, get quotes, approve contractor, monitor job, verify completion
- `financial-review` — monthly budget review, flag anomalies, escalate major decisions to oversight channel
- `travel-plan` — research options, approval gate for bookings above threshold, confirm itinerary
- `contractor-coordination` — issue COMMAND for quote by date, Watchdog follow-up if no ETA, payment gate on completion
- `care-coordination` — recurring care schedule, health status monitoring, family delegation for availability

### Permission Topology

- `household-admin` — full authority: approve major financial decisions, delegate tasks, configure SLAs
- `household-member` — standard member: view all, action assigned tasks, request new tasks
- `household-junior` — restricted: view own tasks only, cannot approve financial decisions

M-of-N quorum configuration for shared household decisions (e.g. 2-of-3 adults required for purchases above threshold).

---

## What This Project Does NOT Own

Everything in the foundation:
- WorkItem lifecycle and SLA enforcement — casehub-work
- Commitment tracking, COMMAND/RESPONSE/DONE/DECLINE — casehub-qhorus
- Tamper-evident audit trail and GDPR erasure — casehub-ledger
- CasePlanModel orchestration and adaptive bindings — casehub-engine
- Agent execution and skill ecosystem — casehub-openclaw

---

## Module Structure

```
api/    — pure Java: LifeDomain enum, ExternalActor request/response records,
          CreateLifeTaskRequest, LifeTaskResponse, CommitmentMode/CommitmentStatus/
          CommitmentOutcome/CommitmentRequest/OversightGateRequest (Layer 3),
          LifeActorIds (Layer 6), capability tag constants, trust dimension constants.
          Zero framework imports. No JPA.

app/    — Quarkus: JPA entities (ExternalActor, LifeTaskContext, LifeCommitmentRecord,
          LifeCaseTracker), REST resources, Flyway migrations (db/life/migration/, V100–V107),
          service layer, SPI implementations (LifeSlaBreachPolicy, LifeCommitmentStrategy + 3 impls),
          infrastructure (LifeChannelInitializer), observers (LifeOversightResponseObserver,
          LifeWatchdogAlertObserver, LifeDecisionLedgerObserver, LifeCaseTrackerObserver),
          engine (io.casehub.life.app.engine — 8 YamlCaseHub subclasses + 8 DSL companions +
          LifeCaseService + LifeCaseTrackerObserver),
          routing (io.casehub.life.app.routing — LifeTrustRoutingPolicyProvider +
          LifeTrustRoutingPolicyKeys + LifeRoutingPolicy + DoublePreference),
          CasePlanModel YAML definitions (app/src/main/resources/life/),
          trust routing YAML config (app/src/main/resources/casehub/life/trust-routing.yaml).
          Ledger subclasses in io.casehub.life.app.ledger (qhorus PU);
          ledger join table migrations at db/life/ledger/migration/ (V2100+).
```

---

## Foundation Layers

Each layer corresponds to a foundation module integration step. `ARC42STORIES.MD §9.4` tracks completion — a layer is not done until its entry is written. `LAYER-LOG.md` is the source-of-truth draft.

```
Layer 1: Domain baseline — ExternalActor entity, REST API, life domain vocabulary (LifeDomain,
         LifeCapabilities, LifeTrustDimensions). No foundation modules active.
         Note: HouseholdTask/LifeGoal/LifeEvent entities introduced here were removed in
         Layer 2 (duplicated foundation primitives — parent#79).
         ✅ COMPLETE

Layer 2: + casehub-work — SLA enforcement via WorkItemTemplate + LifeTaskContext supplement.
         POST /life-tasks creates WorkItem + LifeTaskContext atomically. LifeSlaBreachPolicy
         enforces stateless two-tier escalation. Flyway at db/life/migration/.
         Note: casehub-engine deps removed until SNAPSHOT is fixed (engine#379, engine#380).
         ✅ COMPLETE

Layer 3: + casehub-qhorus — commitment lifecycle: family delegation (COMMAND to household-member),
         contractor follow-up (COMMAND + Watchdog), oversight gates for major financial decisions
         (COMMAND to household-admin; no action until RESPONSE received).
         ✅ COMPLETE

Layer 4: + casehub-ledger — tamper-evident Merkle audit for health decisions, financial
         decisions, and legal actions. GDPR Art.17 erasure for contractor personal data.
         ✅ COMPLETE

Layer 5: + casehub-engine — 8 CasePlanModel workflows (travel-plan, home-maintenance,
         care-coordination, appointment-cycle, contractor-coordination, financial-review,
         family-vote, care-episode). Parallel execution, adaptive gates, M-of-N SubCase
         quorum, QhorusMessageSignalBridge, cross-case signals, milestones, FuncDSL workers.
         Integration tests re-enabled (engine#410 resolved — commit 66a6e34).
         ✅ COMPLETE

Layer 6: Trust routing — TrustRoutingPolicyProvider with 8 domain policies +
         32-entry capability→domain mapping. Attestation pipeline (LifeOutcomeAttestationWriter)
         converts outcomes to LedgerAttestation records. ExternalActor REST response enriched
         with ledger-backed TrustProfile. casehub-engine-ledger activates TrustWeightedAgentStrategy.
         casehub-platform-config provides YAML PreferenceProvider.
         Single-candidate limitation: FuncDSL workers = trivial routing decisions until Layer 7.
         ✅ COMPLETE

Layer 7 (partial): Action risk classification — LifeActionRiskClassifier intercepts
         consequential worker actions before execution. @RiskClassifier CDI qualifier
         activates via ChainedReactiveActionRiskClassifier. HouseholdActionType enum
         (api/) owns the full action taxonomy: 11 types across 3 gate policies
         (ALWAYS / AMOUNT_THRESHOLD / NEVER). YAML thresholds in risk-policy.yaml
         via casehub-platform-config. RBAC-differentiated thresholds deferred (life#26,
         blocked on auth retrofit). Full Layer 7 = + casehub-openclaw as WorkerProvisioner.
         ✅ COMPLETE (risk classification)  🔲 PENDING (OpenClaw integration)

Layer 7 (partial — AgentExec wiring): First real LLM-backed worker (life#25). Establishes
         WorkerFunction.AgentExec(Agent) pattern for OpenClaw /v1/chat/completions. Components:
         `LifeOpenClawChatModelProvider` (@ApplicationScoped, temporary pending engine#527),
         `BookingResult` (structured output schema), `OpenClawHealthProbe` (@IfBuildProfile("prod")
         TCP probe), `TestLifeOpenClawChatModelProvider` (@Alternative @Priority(10) CDI test bean).
         AgentDescriptor.agentId = "openclaw:health-agent@1" per {model-family}:{persona}@{major}
         convention. Protocol: docs/protocols/casehub-life/openclaw-agent-worker-pattern.md.
         engine#536 filed (BlackboardEventCodecRegistrar idempotency — all @QuarkusTest currently
         skip due to pre-existing codec double-registration in augmentation phase).
         ✅ COMPLETE (AgentExec wiring)  🔲 PENDING (WorkerProvisioner / /hooks/agent — life#37, life#38)

Layer 7 (full): + casehub-openclaw — OpenClaw as WorkerProvisioner; skill ecosystem (banking APIs,
         calendar integration, Home Assistant, messaging).
```

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Appointment booking SLA | casehub-work ✅ production |
| Contractor COMMAND + Watchdog | P0 complete (engine#186 ✅, qhorus ✅) |
| Oversight gate — major financial decision | P0 complete |
| GDPR personal data erasure | LedgerErasureService ✅ |
| Tamper-evident audit | CaseLedgerEntry ✅ (2026-04-26) |
| Adaptive CasePlanModel (travel, care) | engine P0 complete |
| Trust-weighted agent routing | casehub-engine-ledger wired ✅; TrustRoutingPolicyProvider implemented; attestation pipeline active; single-candidate routing until Layer 7 |
| OpenClaw as WorkerProvisioner | Pending — research spec 2026-05-25 |

---

## Key Protocols

- **PP-20260524-a8f597** — casehub-platform scope rule: when to add `casehub-platform` and `casehub-platform-expression` as dependencies
- **PP-20260524-10efef** — Flyway ledger locations: add `classpath:db/ledger/migration` when casehub-ledger is active
- **PP-20260525-607b33** — Flyway repo-scoped path: life domain migrations at `db/life/migration/`
- **PP-20260527-da1f66** — domain supplement pattern: attach domain context to foundation primitives via supplement table, not wrapper entity
- **PP-20260526-d0b921** — REST resources must be `@Blocking @ApplicationScoped`; class-level `@Produces(APPLICATION_JSON)` and `@Consumes(APPLICATION_JSON)` required; creation endpoints return 201 Created (no Location header for resources without independent URIs)
- **PP-20260526-75d9c9** — `@Transactional` on service methods only, never resource methods
- **dual-trail-audit-pattern.md** — operational trail (casehub-work/qhorus) vs compliance ledger (casehub-ledger)
- **auth-retrofit-readiness.md** — auth not yet wired to internal services; design for retrofit
- **alternative-extension-patterns.md** — `@Alternative` CDI patterns for SPI wiring
- **PP-20260518-case-definition-layers** — YAML and fluent Java DSL are paired, equal authoring paths; every YAML must have a DSL companion
- **PP-20260531-worker-func-exec** (superseded for LLM workers by PP-20260618-openclaw-agent) —
  engine#463 settled: single LLM call → `Worker.builder().function(Agent.builder()...build())` (AgentExec);
  multi-step durable → `FuncWorkflowBuilder` or YAML workflow (Flow); stub / in-process → Sync lambda.
  FuncWorkflowBuilder is still correct for genuine multi-step workers with retry/branching.
  Raw `WorkerResult.of(map)` stubs remain for non-health workers until Layer 7 full lands.
- **PP-20260618-openclaw-agent** — WorkerFunction.AgentExec(Agent) + AgentDescriptor required for LLM-backed
  workers; agentId = {model-family}:{persona}@{major}; responseSchema required; config changes require restart.
  See docs/protocols/casehub-life/openclaw-agent-worker-pattern.md.
- **PP-20260529-3ffe28** — three-phase case start: never join() inside @Transactional

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Java on this machine:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # native only
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**Multi-module test scoping:** Always scope Maven with `-pl <module> -am`. When combining `-am` with `-Dtest=ClassName`, add `-Dsurefire.failIfNoSpecifiedTests=false`.

**Flyway critical rules:**
- Life domain migrations live at `db/life/migration/` (PP-20260525-607b33) — V100+
- Life ledger join table migrations at `db/life/ledger/migration/` — V2100+ (qhorus datasource)
- Production locations: `classpath:db/life/migration,classpath:db/work/migration`
- casehub-work occupies V1–V31; life starts at V100; ledger join tables at V2100+. V-ranges don't overlap.
- Add `classpath:db/ledger/migration` when casehub-ledger is active (PP-20260524-10efef)
- Add `classpath:db/qhorus/migration` when casehub-qhorus is active
- Qhorus PU packages must use `io.casehub.ledger.runtime` (broad) — NOT `io.casehub.ledger.runtime.model` (misses `LedgerSupplement` sub-package)

**CDI wiring:** `JpaLedgerEntryRepository` and `JpaActorTrustScoreRepository` are both `@Alternative`. The corresponding `@Default` beans (`NoOpLedgerEntryRepository`, `NoOpActorTrustScoreRepository`) are silent no-ops — omitting either JPA bean from `selected-alternatives` causes ledger writes and trust score reads to silently do nothing. Add both to `application.properties`:
```properties
quarkus.arc.selected-alternatives=\
  io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository,\
  io.casehub.ledger.runtime.repository.jpa.JpaActorTrustScoreRepository
```

**CurrentPrincipal disambiguation:** when a foundation SNAPSHOT introduces a new `@Default CurrentPrincipal` bean (e.g. `QhorusInboundCurrentPrincipal`), add it to `quarkus.arc.exclude-types` in both `application.properties` (production) and `test/resources/application.properties` (tests). Production keeps `TenantScopedPrincipal` (@RequestScoped); tests exclude `TenantScopedPrincipal` so `DefaultTestPrincipal` wins (provides canonical tenancyId `278776f9-e1b0-46fb-9032-8bddebdcf9ce`). `DefaultTestPrincipal` is not `@Alternative` — never add it to `selected-alternatives`.

---

## Build Commands

```bash
# Install api first — Quarkus generate-code scans api/target/classes before javac runs
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install -pl api

# Then build app
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install -pl app

# Full build
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn --batch-mode install

# Test a single class (app)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl app -Dtest=ExternalActorResourceTest --batch-mode

# Compile only (no tests)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl api,app --batch-mode
```

**Important:** `mvn test -pl app` requires `api` to be installed in the local Maven repo first. Run `mvn install -pl api` if you get ClassNotFound errors for `io.casehub.life.api.*`.

---

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/life

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Every issue must be linked to its parent epic — no orphan issues.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`. No commit may be made without an issue reference.

---

## Development Workflow

### Platform Coherence
Before implementing any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol in `../parent/docs/PLATFORM.md`. Check capability ownership, boundary rules, and consistency with existing patterns.

### TDD
Every implementation plan must include tests at all levels:
- **Unit tests** — pure domain logic in `api/` — records, interfaces, value types — standard JUnit 5, no Quarkus
- **Integration tests** (`@QuarkusTest` with H2) — JPA entities, REST resources, CDI wiring
- **Robustness tests** — boundary conditions, invalid input, missing data
- **Correctness tests** — SLA deadline computation, state machine transitions, quorum logic

Tests are part of the implementation plan from the start — not deferred.

### IntelliJ MCP Tools
Two IntelliJ MCPs are available: `mcp__intellij` and `mcp__intellij-index`.

**Always check both are available before starting implementation work.** If either is unavailable, stop and report before proceeding.

**Prefer IntelliJ tools over Bash** for all operations they support:

| Operation | Use IntelliJ tool, not Bash |
|-----------|----------------------------|
| Find a class, symbol, or file | `ide_find_class`, `ide_find_file`, `ide_search_text` |
| Navigate to a definition | `ide_find_definition` |
| Find all references before renaming/deleting | `ide_find_references` |
| Rename a symbol across the project | `ide_refactor_rename` |
| Move a file | `ide_move_file` |
| Check for errors in a file | `ide_diagnostics` |
| Build the project | `build_project` |
| Read a file by project-relative path | `get_file_text_by_path` |
| Search for text across files | `search_in_files_by_text` |

Only use Bash when the operation is outside IntelliJ's scope: git commands, Maven, file creation, shell scripts.

### Code Review
Before marking any task complete, invoke `superpowers:requesting-code-review` to review the implementation for quality, correctness, and platform consistency.

### Documentation Maintenance
After any code change, systematically check and update:

1. **This CLAUDE.md** — does any section describe something that no longer exists or no longer matches the code?
2. **`casehub-life.md`** in the parent repo — reflects the current state of domain ownership, epics, dependencies
3. **Cross-references** — any path or URL referenced in docs: verify it resolves, rename if the target moved
4. **Drift and gaps** — code that exists without doc coverage; docs that describe code that was removed or renamed
5. **ARC42STORIES.MD** — update §9.4 layer entry before closing any layer-related issue (LAYER-LOG.md is the draft)

Run this check before every handover. If a doc update requires changes in the parent repo, create a GitHub issue on `casehubio/parent` — do not commit to that repo directly.
