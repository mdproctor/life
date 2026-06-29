# GDPR CaseMemoryStore Erasure Integration

**Issue:** life#34
**Date:** 2026-06-29
**Status:** Approved

## Context

casehub-life's GDPR Art.17 erasure endpoint (`DELETE /external-actors/{id}/personal-data`) nullifies PII on `ExternalActor` and writes an `ExternalActorErasureLedgerEntry` — but does not clean `CaseMemoryStore` records referencing the erased actor.

Peer harnesses (devtown, clinical) integrate `CaseMemoryStore.eraseEntity()` alongside their erasure flows. casehub-life should do the same.

casehub-life currently has no `CaseMemoryStore` adapter on the classpath — `NoOpCaseMemoryStore` is active, returning 0 for all operations. The integration call is still correct: it's a guarded no-op today, and when a memory adapter is added (for OpenClaw agent context), the erasure path is already wired.

## Approach

Inline `eraseEntity()` in `ExternalActorService.erase()` — no separate service class. casehub-life has a single erasable entity type (`ExternalActor`) with a single call site, unlike devtown which needs a dedicated `GdprErasureService` for multiple entity types.

## Changes

### `ExternalActorResource`

- Inject `CurrentPrincipal`
- Pass `currentPrincipal.actorId()` as `erasedBy` to `ExternalActorService.erase()` — identity extraction stays at the resource layer per auth-retrofit-readiness protocol constraints (no auth/principal logic in service layers)

### `ExternalActorService`

- Inject `CaseMemoryStore`
- Accept `String erasedBy` parameter on `erase()`
- After PII nullification, before ledger write: call `memoryStore.eraseEntity(LifeActorIds.of(id), TenancyConstants.DEFAULT_TENANT_ID)`
- Catch `MemoryCapabilityException` → count = 0 (see §Error Handling Strategy)
- Pass `erasedBy` and count to `writeErasureEntry()`

### `ExternalActorErasureLedgerEntry`

- Add `@Column(name = "memory_records_erased") public int memoryRecordsErased`
- `domainContentBytes()` is **not** updated — `memoryRecordsErased` is operational metadata, not compliance-critical content. Including it would break Merkle hash verification of existing entries whose digests were computed without this field. The authoritative erasure proof is the entry's existence and its existing fields (`erasedActorId`, `contactMethod`, `erasedBy`).

### `LifeLedgerWriter.writeErasureEntry()`

- Accept `int memoryRecordsErased` parameter
- Set on entry before save

### Migration

`V2105__add_memory_records_erased_to_erasure_entry.sql`:
```sql
ALTER TABLE external_actor_erasure_ledger_entry
    ADD COLUMN memory_records_erased INTEGER NOT NULL DEFAULT 0;
```

### Entity ID Convention

`LifeActorIds.of(actorId)` produces `life-actor:{uuid}` — the same convention used throughout Layer 6 for ledger actorIds. Memory records keyed by this ID will be erased.

## Error Handling Strategy

**Narrow catch — fail-fast for I/O errors.** Only `MemoryCapabilityException` is caught (→ count = 0). Other runtime exceptions (network failures, JDBC errors) propagate and roll back the transaction.

Rationale: `MemoryCapabilityException` signals a store that doesn't support `eraseEntity()` — a configuration fact, not a runtime error. Real I/O failures should fail the transaction so the caller retries the entire operation. This is the safer GDPR-compliant choice: don't confirm erasure if memory cleanup actually failed.

The `NoOpCaseMemoryStore` overrides `eraseEntity()` to return 0 without throwing, so the catch path only activates for partial-capability adapters that inherit the default `CaseMemoryStore.eraseEntity()` implementation.

This matches devtown's `GdprErasureService.eraseEntitySafely()` pattern.

## Known Limitations

**Transaction boundary with external memory stores.** `ExternalActorService.erase()` is `@Transactional`. PII nullification and ledger write participate in JTA. `CaseMemoryStore.eraseEntity()` for external backends (Mem0 REST, Graphiti REST, SQLite with separate connection pool) does NOT participate in JTA.

If memory erasure succeeds but the JTA transaction subsequently rolls back, memory records are erased while PII remains — no ledger proof is written. This is a known limitation shared with devtown and clinical, inherent in the distributed nature of external memory stores. When a real memory adapter is added (life#8), this should be mitigated by ordering the memory erasure call after the commit point or introducing a compensating transaction — the same pattern the peer harnesses will need to adopt.

## Tests

- **Unit:** `LifeLedgerWriterTest` — verify `memoryRecordsErased` is set on the entry
- **Unit:** `ExternalActorServiceTest` — verify `MemoryCapabilityException` catch path sets count = 0; verify `eraseEntity()` called with `LifeActorIds.of(id)` and `TenancyConstants.DEFAULT_TENANT_ID`; verify `erasedBy` from caller is passed through to `writeErasureEntry()`
- **Integration:** `ExternalActorGdprResourceTest` — verify erasure flow works with NoOp store (count = 0), verify ledger entry records `memoryRecordsErased = 0`. Update `@TestSecurity` to use a distinct user name (e.g. `user = "jane.admin"`) to verify `erasedBy` records the principal identity, not a hardcoded role name.

## Not in Scope

- Adding a `CaseMemoryStore` adapter (memory-inmem, memory-jpa, etc.) — tracked as part of life#8 (Layer 7 OpenClaw integration)
- `LedgerErasureService.erase()` integration — tokenisation-based ledger record erasure is a distinct operation from memory cleanup (life#49)
- Compliance report enhancement — the `memoryRecordsErased` field on the ledger entry provides the audit trail; surfacing in a report is a separate concern (life#50)
