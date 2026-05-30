# Layer 4 Design — casehub-ledger Tamper-Evident Audit

**Date:** 2026-05-30  
**Issue:** casehubio/life#5  
**Status:** Approved — proceeding to implementation

---

## Context

Layer 4 integrates `casehub-ledger` to produce tamper-evident Merkle audit records for
health decisions, financial decisions, and legal actions. It also delivers GDPR Art.17
erasure for personal data held in `ExternalActor`. `casehub-ledger` runtime is already
on the classpath; the qhorus datasource and Flyway config are in place from Layer 3.

**What's already present:**
- `casehub-ledger` runtime dependency in `app/pom.xml`
- Qhorus datasource with `classpath:db/qhorus/migration,classpath:db/ledger/migration`
- `quarkus.hibernate-orm."qhorus".packages` with ledger model packages
- `JpaLedgerEntryRepository` selected via `quarkus.arc.selected-alternatives`

---

## Architecture Decision — Per-Domain LedgerEntry Subclasses

**Four entities:** `HealthDecisionLedgerEntry`, `FinancialDecisionLedgerEntry`,
`LegalActionLedgerEntry`, `ExternalActorErasureLedgerEntry`.

**Rationale:** Each domain has a distinct required field set. A single table with nullable
domain-specific columns forces NOT NULL constraints away from the schema and into application
logic — a production anti-pattern. Separate entities enforce schema invariants at the
database level. The composition concern (downstream subclassing) does not apply:
casehub-life is the terminal application tier; these entities have no consumers.

**GDPR** does not use `ActorIdentityProvider` SPI or `LedgerErasureService`. The PII
in this system lives exclusively in `ExternalActor` (`name`, `contactValue`). Ledger
entries store only the ExternalActor UUID — not PII. Erasure nullifies the PII fields
on the entity and writes `ExternalActorErasureLedgerEntry` as the tamper-evident proof.

**Note: `LedgerEntry.@PrePersist`** auto-assigns `id` and `occurredAt` when null
(verified: `runtime/src/main/java/io/casehub/ledger/runtime/model/LedgerEntry.java:261`).
Writers must NOT set these fields. Only set: `subjectId`, `sequenceNumber`, `entryType`,
`actorId`, `actorType`, `actorRole`, and domain-specific fields.

---

## Domain Entities

All entities require `@DiscriminatorValue` matching the value stated below. Missing
this annotation causes Hibernate to fall back to the class simple name — silently
breaking discriminator-based joins and queries.

---

### `HealthDecisionLedgerEntry`

**Discriminator value:** `HEALTH_DECISION`  
**Join table:** `health_decision_ledger_entry` (V2100, qhorus datasource)

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID FK → `ledger_entry` | NOT NULL | JOINED inheritance PK |
| `work_item_id` | UUID | NOT NULL | WorkItem being audited |
| `provider_id` | UUID | nullable | ExternalActor UUID; null if no specific provider |
| `appointment_date` | TIMESTAMP WITH TIME ZONE | nullable | Scheduled date; null for non-appointment health tasks |
| `task_category` | VARCHAR(100) | NOT NULL | WorkItem category (health, medication, referral, etc.) |
| `sla_deadline` | TIMESTAMP WITH TIME ZONE | NOT NULL | WorkItem.expiresAt at time of audit write |
| `event_type` | VARCHAR(30) | NOT NULL | `CREATED` / `SLA_BREACH` / `COMPLETED` |
| `outcome` | VARCHAR(255) | nullable | Null except at COMPLETED; mirrors WorkItem.outcome |

**Base LedgerEntry fields:** `subjectId = workItemId`, `actorId = "life-system"`,
`actorType = SYSTEM`, `actorRole = "HealthDecisionAudit"`, `entryType = EVENT`.

---

### `FinancialDecisionLedgerEntry`

**Discriminator value:** `FINANCIAL_DECISION`  
**Join table:** `financial_decision_ledger_entry` (V2101, qhorus datasource)

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID FK → `ledger_entry` | NOT NULL | JOINED inheritance PK |
| `work_item_id` | UUID | nullable | Null until oversight RESPONSE received; set at COMPLETED |
| `oversight_ref` | UUID | NOT NULL | `LifeCommitmentRecord.id` — stable across all three entries |
| `amount_threshold` | NUMERIC(15,2) | NOT NULL | Amount threshold from oversight gate request |
| `purchase_category` | VARCHAR(100) | NOT NULL | Category of financial decision |
| `approved_by` | VARCHAR(255) | nullable | `senderId` from RESPONSE message; null until COMPLETED |
| `event_type` | VARCHAR(30) | NOT NULL | `CREATED` / `SLA_BREACH` / `COMPLETED` |

**Base LedgerEntry fields:** `subjectId = oversightRef`, `actorId = "life-system"`,
`actorType = SYSTEM`, `actorRole = "FinancialDecisionAudit"`, `entryType = EVENT`.

**Note — subjectId asymmetry:** For HEALTH and LEGAL, `subjectId = workItemId`. For
FINANCE, `subjectId = oversightRef` (LifeCommitmentRecord.id) because `workItemId` is
null at CREATED and SLA_BREACH. A Layer 5/6 query for "all ledger entries for life task X"
requires different logic per domain: `findBySubjectId(workItemId)` for HEALTH/LEGAL, but
`findBySubjectId(oversightRef)` for FINANCE. This asymmetry is the correct tradeoff given
the oversight gate's deferred WorkItem creation model.

**Note — approved_by data source:** `LifeOversightResponseObserver.onMessage()` has
access to `event.senderId()` at RESPONSE time. The observer must set
`record.approvedBy = event.senderId()` on the `LifeCommitmentRecord` before persisting.
At COMPLETED time, `LifeLedgerWriter` reads `record.approvedBy` to populate this field.
This requires a new `approved_by VARCHAR(255)` column on `life_commitment_record` (V106
migration on default datasource) and a corresponding `approvedBy` field on
`LifeCommitmentRecord`.

---

### `LegalActionLedgerEntry`

**Discriminator value:** `LEGAL_ACTION`  
**Join table:** `legal_action_ledger_entry` (V2102, qhorus datasource)

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID FK → `ledger_entry` | NOT NULL | JOINED inheritance PK |
| `work_item_id` | UUID | NOT NULL | WorkItem being audited |
| `legal_obligation` | VARCHAR(255) | NOT NULL | Description (e.g. "Annual Tax Return 2026") |
| `filing_deadline` | TIMESTAMP WITH TIME ZONE | NOT NULL | The legal deadline (WorkItem.expiresAt) |
| `jurisdiction` | VARCHAR(100) | nullable | e.g. "UK", "EU-GDPR"; null if not specified |
| `event_type` | VARCHAR(30) | NOT NULL | `CREATED` / `SLA_BREACH` / `COMPLETED` |
| `action_taken` | VARCHAR(255) | nullable | Null until COMPLETED |

**Base LedgerEntry fields:** `subjectId = workItemId`, `actorId = "life-system"`,
`actorType = SYSTEM`, `actorRole = "LegalActionAudit"`, `entryType = EVENT`.

---

### `ExternalActorErasureLedgerEntry`

**Discriminator value:** `EXTERNAL_ACTOR_ERASURE`  
**Join table:** `external_actor_erasure_ledger_entry` (V2103, qhorus datasource)

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | UUID FK → `ledger_entry` | NOT NULL | JOINED inheritance PK |
| `erased_actor_id` | UUID | NOT NULL | ExternalActor UUID (row retained; PII nulled) |
| `contact_method` | VARCHAR(50) | NOT NULL | Type of contact data erased (EMAIL / PHONE / etc.) |
| `erased_by` | VARCHAR(255) | NOT NULL | `senderId` of requester ("household-admin" until auth wired) |

**Base LedgerEntry fields:** `subjectId = erasedActorId`, `actorId = erasedBy`,
`actorType = HUMAN` (or SYSTEM until auth wired), `actorRole = "GdprDataController"`,
`entryType = EVENT`.

---

## actorId Convention — life#10

| Actor | actorId value | actorType |
|---|---|---|
| System-initiated (no principal yet) | `"life-system"` | `SYSTEM` |
| Household-admin (until auth wired) | `"household-admin"` | `HUMAN` |
| Post-auth principal | `currentPrincipal.actorId()` | `HUMAN` |

ExternalActor UUIDs are stored in domain-specific columns (`provider_id`, `erased_actor_id`),
NOT in the base `actorId` field. UUIDs are not PII and require no ledger chain severing.

---

## Trigger Model

### CREATE — direct call from service layer

`WorkItemLifecycleEvent` fires before `LifeTaskContext.persist()` — CDI observers cannot
be used for CREATE. The relevant service calls `LifeLedgerWriter` after persisting context.

| Domain | Trigger point | Writer call |
|---|---|---|
| HEALTH | `LifeTaskService.create()` after `ctx.persist()` | `lifeLedgerWriter.writeHealthEntry(CREATED, ctx, workItem)` |
| LEGAL | `LifeTaskService.create()` after `ctx.persist()` | `lifeLedgerWriter.writeLegalEntry(CREATED, ctx, workItem)` |
| FINANCE | `OversightGateStrategy.execute()` after `record.persist()` | `lifeLedgerWriter.writeFinancialEntry(CREATED, record, null)` |

**Transaction semantics for direct calls:** the writer call inherits the outer
`@Transactional(REQUIRED)` boundary. If task creation rolls back, the ledger entry
rolls back with it — correct behavior, the audit record should not outlive the event
it documents.

**Finance CREATE note:** `OversightGateStrategy` has no WorkItem at gate-creation time.
`LifeCommitmentRecord` carries `amountThreshold` and `purchaseCategory` from
`OversightGateRequest` — these fields must be persisted on the record and read by the
writer. Add `amountThreshold NUMERIC(15,2)` and `purchaseCategory VARCHAR(100)` to
`life_commitment_record` (V106 migration) and corresponding fields to
`LifeCommitmentRecord`.

### SLA_BREACH — CDI observer

`LifeDecisionLedgerObserver` observes:

- `@Observes SlaBreachEvent` → `breach.context().task().taskId()` → look up
  `LifeTaskContext` → dispatch to `LifeLedgerWriter` for HEALTH/LEGAL SLA_BREACH.

**Finance SLA_BREACH — two distinct scenarios:**

1. **Pre-RESPONSE (no WorkItem):** `WatchdogAlertEvent` fires on the oversight channel.
   `LifeWatchdogAlertObserver` handles this. It must be extended to also call
   `lifeLedgerWriter.writeFinancialEntry(SLA_BREACH, record, null)` for OVERSIGHT-mode
   records. The `LifeCommitmentRecord` is looked up by channel name (existing pattern).

2. **Post-RESPONSE (WorkItem exists):** `SlaBreachEvent` fires. The observer looks up
   `LifeTaskContext`, domain = FINANCE, dispatches to `LifeLedgerWriter`.

Both observer methods are `@Transactional(REQUIRES_NEW)` — ledger writes commit
independently of the outer casehub-work transaction.

### COMPLETED — CDI observer

`LifeDecisionLedgerObserver` observes:

- `@Observes WorkItemLifecycleEvent` — filter `status() == COMPLETED` → look up
  `LifeTaskContext` → dispatch to `LifeLedgerWriter` for HEALTH/FINANCE/LEGAL COMPLETED.

**COMPLETED filter:** `WorkItemStatus.COMPLETED` only. `REJECTED`, `ESCALATED`,
`CANCELLED`, `EXPIRED` are terminal but not "completed" in the compliance sense —
HEALTH/LEGAL COMPLETED entries represent a successful outcome, not any terminal state.
Write a COMPLETED entry only for `WorkItemStatus.COMPLETED`.

### GDPR erasure — direct call from `ExternalActorService`

`DELETE /external-actors/{id}/personal-data` → `ExternalActorService.erase(uuid)`:

1. Load ExternalActor — 404 if not found
2. If `gdprErasedAt` already set → 409 "Already erased at [date]"
3. Check for WorkItems via LifeTaskContext where `workItem.status.isActive()` — 409 if
   any exist (PENDING, ASSIGNED, IN_PROGRESS, SUSPENDED block erasure; EXPIRED, DELEGATED,
   COMPLETED, REJECTED, ESCALATED, CANCELLED do not)
4. Null `name` → `"[ERASED]"`, `contactValue` → `"[ERASED]"`
5. Set `gdprErasedAt = Instant.now()`
6. Write `ExternalActorErasureLedgerEntry`

**Endpoint:** `DELETE /external-actors/{id}/personal-data` — distinct from the existing
`DELETE /external-actors/{id}` (hard delete, row removed). Hard delete remains for actors
with no task references. GDPR erasure retains the row for FK integrity, nulls PII.

**GET /external-actors/{id} after erasure:** include `gdprErasedAt` in
`ExternalActorResponse`. Return 200 with erased fields showing `"[ERASED]"` — the
timestamp makes the erasure state explicit to callers without requiring a 410.

---

## Components

### New classes

| Class | Package | Role |
|---|---|---|
| `HealthDecisionLedgerEntry` | `app/entity/ledger/` | JPA JOINED subclass |
| `FinancialDecisionLedgerEntry` | `app/entity/ledger/` | JPA JOINED subclass |
| `LegalActionLedgerEntry` | `app/entity/ledger/` | JPA JOINED subclass |
| `ExternalActorErasureLedgerEntry` | `app/entity/ledger/` | JPA JOINED subclass |
| `LifeDecisionEventType` | `app/` | Enum: `CREATED`, `SLA_BREACH`, `COMPLETED` — internal, not API |
| `LifeLedgerWriter` | `app/service/ledger/` | Single writer — all domain entries + sequenceNumber |
| `LifeDecisionLedgerObserver` | `app/observer/` | CDI observer for SLA_BREACH and COMPLETED |

`LifeDecisionEventType` lives in `app/`, not `api/`. It is a join-table column value
used exclusively by app-layer entities and writers. No external consumer imports it.

### Modified classes

| Class | Change |
|---|---|
| `LifeTaskService` | Call `lifeLedgerWriter.writeHealthEntry/writeLegalEntry(CREATED, ...)` after `ctx.persist()` |
| `OversightGateStrategy` | Call `lifeLedgerWriter.writeFinancialEntry(CREATED, record, null)` after `record.persist()` |
| `LifeWatchdogAlertObserver` | Write `FinancialDecisionLedgerEntry(SLA_BREACH)` for OVERSIGHT-mode alerts |
| `LifeOversightResponseObserver` | Set `record.approvedBy = event.senderId()` before persisting; `LifeTaskService.create()` already creates `LifeTaskContext{domain=FINANCE}` — no additional fix needed here |
| `ExternalActorService` | Add `erase(UUID)` method |
| `ExternalActorResource` | Add `DELETE /{id}/personal-data` handler |
| `ExternalActorResponse` | Add `gdprErasedAt` field |
| `ExternalActor` (entity) | Add `@Column gdprErasedAt Instant` field |
| `LifeCommitmentRecord` (entity) | Add `approvedBy VARCHAR(255)`, `amountThreshold NUMERIC(15,2)`, `purchaseCategory VARCHAR(100)` |

**Finance LifeTaskContext note:** `LifeOversightResponseObserver.onMessage()` already
calls `lifeTaskService.create(pending)` which creates `LifeTaskContext{domain=FINANCE}`
(derived from `domainFromCategory(template.category)`). No code change needed, but
`OversightGateStrategy` must validate that the provided `templateRef` maps to a life
domain category (not household or other) — otherwise domain will be wrong and the
ledger observer will miss the COMPLETED entry.

---

### `LifeLedgerWriter` — unified writer design

```
@ApplicationScoped LifeLedgerWriter:

  void writeHealthEntry(LifeDecisionEventType, LifeTaskContext, WorkItem)
  void writeFinancialEntry(LifeDecisionEventType, LifeCommitmentRecord, UUID workItemId)
  void writeLegalEntry(LifeDecisionEventType, LifeTaskContext, WorkItem)
  void writeErasureEntry(ExternalActor, String erasedBy)

  private int nextSequenceNumber(UUID subjectId)
  private void populateBase(LedgerEntry entry, UUID subjectId,
                            String actorId, ActorType actorType, String actorRole)
```

`@PrePersist` handles `id` and `occurredAt`. Writers set: `subjectId`, `sequenceNumber`,
`entryType`, `actorId`, `actorType`, `actorRole`, domain-specific fields.

**Concurrency note:** `nextSequenceNumber` uses `findLatestBySubjectId(subjectId)` —
a read-then-increment pattern that is not safe under concurrent writes for the same
subject. For a personal life harness with a single household, concurrent writes to the
same subject are not expected. If concurrent writes are needed in future, a
`SELECT ... FOR UPDATE` or a DB sequence per subject is the correct fix.

---

## Infrastructure Changes

### Flyway — new path for ledger join tables

Life ledger join tables run on the qhorus datasource. A dedicated path is added:

**`application.properties`:**
```properties
quarkus.flyway."qhorus".locations=\
  classpath:db/qhorus/migration,\
  classpath:db/ledger/migration,\
  classpath:db/life/ledger/migration
```
Same addition in test `application.properties`.

**Version ranges:** casehub-ledger base V1000–V1007 (`db/ledger/migration`); qhorus V2000
(`db/qhorus/migration`); life ledger joins V2100–V2103 (`db/life/ledger/migration`).
No overlap.

### JPA package registration

Add `io.casehub.life.app.entity.ledger` to the qhorus PU packages in both main and test
`application.properties`.

---

## Migration Scripts

| Version | Path | Datasource | Contents |
|---|---|---|---|
| V105 | `db/life/migration/` | default | Add `gdpr_erased_at TIMESTAMP WITH TIME ZONE` to `external_actor` |
| V106 | `db/life/migration/` | default | Add `approved_by VARCHAR(255)`, `amount_threshold NUMERIC(15,2)`, `purchase_category VARCHAR(100)` to `life_commitment_record` |
| V2100 | `db/life/ledger/migration/` | qhorus | `health_decision_ledger_entry` join table |
| V2101 | `db/life/ledger/migration/` | qhorus | `financial_decision_ledger_entry` join table |
| V2102 | `db/life/ledger/migration/` | qhorus | `legal_action_ledger_entry` join table |
| V2103 | `db/life/ledger/migration/` | qhorus | `external_actor_erasure_ledger_entry` join table |

All qhorus migrations: `TIMESTAMP WITH TIME ZONE` (not `TIMESTAMP`), `UUID` (not
`VARCHAR`), `NUMERIC` (not `DOUBLE`) per GE-20260512-2c2eff portability rules.

---

## Testing Strategy

### Unit tests (Mockito, no Quarkus)

**`LifeLedgerWriterTest`** — mocks `LedgerEntryRepository`:

Health entry tests:
- `writeHealthEntry_created_setsRequiredFields` — eventType=CREATED, workItemId, taskCategory, slaDeadline, actorId=life-system, actorType=SYSTEM, sequenceNumber=1
- `writeHealthEntry_sequenceNumberIncrementsFromPrior`
- `writeHealthEntry_slaBreachEventType`
- `writeHealthEntry_completed_setsOutcome`
- `writeHealthEntry_nullProviderIdWhenNoActor`

Financial entry tests:
- `writeFinancialEntry_created_setsOversightRefAndAmountThreshold`
- `writeFinancialEntry_slaBreachNullWorkItemId` — pre-RESPONSE SLA_BREACH
- `writeFinancialEntry_completed_setsApprovedByAndWorkItemId`

Legal entry tests:
- `writeLegalEntry_created_setsLegalObligationAndFilingDeadline`
- `writeLegalEntry_slaBreachEventType`
- `writeLegalEntry_completed_setsActionTaken`

Erasure entry tests:
- `writeErasureEntry_setsContactMethodAndErasedBy`
- `writeErasureEntry_sequenceNumberStartsAt1ForNewSubject`

Note: do NOT assert on `entry.id` or `entry.occurredAt` — these are set by
`LedgerEntry.@PrePersist` at persist time, which is bypassed when `LedgerEntryRepository`
is mocked. Assert only on fields explicitly set by the writer.

**`ExternalActorServiceEraseTest`** — mocks `LedgerEntryRepository`:
- `erase_nullsPiiAndSetsErasedAt`
- `erase_writesErasureLedgerEntry`
- `erase_throws404WhenActorNotFound`
- `erase_throws409WhenAlreadyErased`
- `erase_throws409WhenActiveTasksExist` — seed WorkItem with `isActive()=true`
- `erase_allowsErasureWhenTasksAreExpiredOrCompleted` — seed WorkItem with EXPIRED/COMPLETED

### Integration tests (`@QuarkusTest`)

**`LifeDecisionLedgerObserverTest`** — call observer methods directly via CDI proxy:
- `onSlaBreachEvent_writesHealthSlaBreachEntry`
- `onSlaBreachEvent_writesLegalSlaBreachEntry`
- `onSlaBreachEvent_skipsHouseholdDomainTask`
- `onSlaBreachEvent_skipsWorkItemWithNoLifeTaskContext`
- `onLifecycleEvent_writesHealthCompletedEntry` — status=COMPLETED
- `onLifecycleEvent_writesFinancialCompletedEntry`
- `onLifecycleEvent_writesLegalCompletedEntry`
- `onLifecycleEvent_skipsRejectedStatus` — REJECTED is terminal but not COMPLETED
- `onLifecycleEvent_skipsNonLifeDomainWorkItem`

**`ExternalActorGdprResourceTest`** — via REST:
- `DELETE /external-actors/{id}/personal-data` → 204, name="[ERASED]", ledger entry written
- `DELETE /external-actors/{id}/personal-data` (not found) → 404
- `DELETE /external-actors/{id}/personal-data` (already erased) → 409
- `DELETE /external-actors/{id}/personal-data` (active tasks) → 409
- `GET /external-actors/{id}` after erasure → 200, gdprErasedAt non-null

**`LifeBootTest`** — existing; verifies CDI starts clean after infrastructure changes.

---

## Excluded from this Layer

- Trust score updates from ledger attestations — Layer 6
- `ComplianceSupplement` / `ProvenanceSupplement` — not required
- `LedgerErasureService` / `ActorIdentityProvider` SPI — PII not in ledger entries
- Reactive ledger paths — `casehub.ledger.reactive.enabled=false`
- Hard delete `DELETE /external-actors/{id}` — unchanged; GDPR erasure uses distinct path
