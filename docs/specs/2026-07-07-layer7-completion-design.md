# Layer 7 Completion — Heartbeat Channel Context Enrichment

**Issue:** casehubio/life#8 (partial — see §Scope below)
**Date:** 2026-07-07
**Status:** Design approved

## Scope

This spec delivers one new feature toward #8: channel context enrichment for heartbeat
sentinels. It does NOT close #8 — the epic's core deliverable (skill integration: banking,
calendar, Home Assistant, messaging) is blocked on casehub-openclaw Epic 4 and remains on #8.

A separate issue will be filed for the ChannelContextProvider work described here.

## Problem

Heartbeat sentinels (delivered by life#37) run, produce reports, and trigger WorkItem
creation via the existing `sentinel-escalation` bindings in all 7 case YAMLs. One gap
remains in the sentinel pipeline:

**Agents are context-blind.** Heartbeat sentinels receive case context via
`CaseHubRuntime.query(caseId, ".")`, but never see qhorus channel messages. A
grocery-agent doesn't know finance-agent posted a budget warning. A health-agent
doesn't see smart home movement data. Cross-agent coordination requires channel awareness.

### What's already delivered (life#37)

All 7 case definitions already have sentinel capabilities, sentinel bindings, and
`sentinel-escalation` humanTask bindings that create WorkItems when the sentinel
reports `escalationRequired: true`. These were delivered by the Layer 7
WorkerProvisioner Heartbeat spec (life#37). No YAML changes are needed — the
sentinel-to-WorkItem pipeline is already functional.

### Skill integration split

The following scope from #8 is blocked on casehub-openclaw Epic 4 and remains on #8:

- Banking API skill (Open Banking aggregation)
- Calendar skill (Google Calendar integration)
- IoT skill (Home Assistant smart home)
- Messaging skill (WhatsApp/SMS follow-up)

These require the OpenClaw skill registry, discovery, and execution infrastructure that
Epic 4 provides. They do not affect the architectural wiring delivered by this branch.

## Design

### Feature 1: ChannelContextProvider

A `@ApplicationScoped` CDI bean `LifeChannelContextProvider` in `io.casehub.life.app.engine`.

#### Relationship to ChannelContextWindowService

`ChannelContextWindowService` exists in `casehub-openclaw-core`
(`io.casehub.openclaw.context.ChannelContextWindowService`). It maintains per-channel
in-memory ring buffers populated by `MessageReceivedEvent`, with TTL-based eviction
(default 30 minutes). Its `query(agentId, since)` API is agent-centric.

`LifeChannelContextProvider` is a different component for a different use case:

| | ChannelContextWindowService | LifeChannelContextProvider |
|---|---|---|
| **Storage** | In-memory ring buffer (volatile) | Persistent `MessageStore` query |
| **Population** | Event-driven (`MessageReceivedEvent`) | On-demand query per heartbeat tick |
| **Survives restart** | No — buffer lost on restart | Yes — queries persistent store |
| **Query model** | Agent-centric: `query(agentId, since)` | Case-centric: `gatherContext(caseId)` |
| **Module** | `casehub-openclaw-core` | `casehub-life` |

Heartbeat sentinels run every 4–24 hours. Between ticks, the application may restart,
and the in-memory buffer would be empty. A persistent query against `MessageStore`
guarantees context availability regardless of process lifecycle. Additionally, life
avoids depending on openclaw-core internals (documented in life#37 §6).

#### Responsibilities

- Resolve which channels are relevant for a given case
- Query recent messages from each channel via `MessageStore`
- Serialise into a structured map for agent consumption

#### Channel resolution rules

| Channel | Included when | Rationale |
|---------|--------------|-----------|
| `life/delegation` | Always | Coordination bus — budget warnings, delegation, cross-agent signals |
| `life/oversight` | Always | Pending approval gates constrain what any agent can do |
| `life/actor/ext-{externalActorId}` | Case has associated WorkItems with non-null `externalActorId` in `LifeTaskContext` | Per-actor comms relevant only to cases involving that actor |

#### Actor channel resolution path

The `caseId` (engine case ID) links to WorkItems via `callerRef`. Engine-created
WorkItems have `callerRef` in the format `case:{caseId}/pi:{planItemId}` (generated
by `PlanItemCallerRef.encode()`). The resolution path:

1. Query `WorkItem` entities where `callerRef LIKE 'case:{caseId}/%'`
2. For each matching WorkItem, look up `LifeTaskContext.findByIdOptional(workItem.id)`
3. Collect non-null `externalActorId` values
4. Resolve channel IDs via `LifeChannelInitializer.channelIdFor("life/actor/ext-" + externalActorId)`

If no WorkItems exist for the case, or none have associated `LifeTaskContext` entries
with external actors, the actor channel is simply omitted. Delegation and oversight
channels are always included.

#### Message query

Per channel, `MessageStore.scan(MessageQuery.builder().channelId(id).limit(N).descending(true).build())`,
then reverse to chronological order. Default limit: 10 messages per channel. Configurable via
`casehub.life.channel-context.message-limit`.

#### Serialisation format

Each message becomes `{sender, type, content, createdAt}`.
Returned as:
```json
{
  "channelContext": {
    "delegation": [{sender: "finance-agent", type: "STATUS", content: "Budget warning: ...", createdAt: "..."}],
    "oversight": [...],
    "actor/ext-{id}": [...]
  }
}
```

#### Integration point

`LifeHeartbeatJob` calls `channelContextProvider.gatherContext(caseId)` after
`caseHubRuntime.query()` and merges the result into the case context map before
`sentinelAgent.execute(enrichedContext)`.

#### Fault isolation

`gatherContext()` is supplementary enrichment — the sentinel can produce a useful
report from case context alone. A failure in channel context gathering (channel not
found during startup, transient DB error in `MessageStore.scan()`) must not prevent
the heartbeat tick from executing.

`LifeHeartbeatJob.execute()` wraps the `gatherContext()` call in a try-catch. On
failure: log a warning with the exception, and proceed with case context only (empty
channel context map). The sentinel executes with degraded input rather than not at all.

#### Dependencies

`ChannelService` (resolve channel name → UUID), `MessageStore` (query messages).
Both are existing qhorus CDI beans.

### Prerequisite: Ledger Import Migration

`LedgerAttestation` moved from `io.casehub.ledger.runtime.model` to `io.casehub.ledger.api.model`
in a recent upstream SNAPSHOT. Two files need the import path updated:

- `LifeOutcomeAttestationWriter.java` (production)
- `LifeOutcomeAttestationWriterTest.java` (test)

Mechanical fix, folded into the first commit.

## Testing Strategy

### ChannelContextProvider — Unit

Mock `ChannelService` and `MessageStore`. Verify:
- Delegation + oversight channels always queried
- Actor channel queried only when case has WorkItems with non-null `externalActorId`
  in `LifeTaskContext`
- Message limit respected
- Messages returned in chronological order
- Serialisation format matches expected structure

### ChannelContextProvider — Integration (`@QuarkusTest`)

**Delegation channel path:**
- Channels seeded by `LifeChannelInitializer` at startup
- Dispatch messages to `life/delegation` via `MessageService.dispatch()`
- Call `gatherContext(caseId)` and assert messages appear in returned map

**Actor channel resolution path:**
- Create a `WorkItem` with `callerRef` in `case:{caseId}/pi:...` format
- Persist a `LifeTaskContext` for that WorkItem with non-null `externalActorId`
- Ensure the actor channel exists via `LifeChannelInitializer.ensureActorChannel(externalActorId)`
- Dispatch a message to the actor channel
- Call `gatherContext(caseId)` and assert actor channel messages appear in returned map

**Fault isolation:**
- Call `gatherContext()` with a caseId that triggers a resolution failure
- Assert the heartbeat tick completes with case context only (no channel context)
- Assert a warning was logged

### LifeHeartbeatJob — Context Enrichment

- Existing heartbeat integration tests exercise the full flow
- Add assertion that agent receives channel context in its input
- Mock agent, inspect context map passed to `execute()`

## Files Changed

**New files:**
- `app/src/main/java/io/casehub/life/app/engine/LifeChannelContextProvider.java`
- `app/src/test/java/io/casehub/life/app/engine/LifeChannelContextProviderTest.java` (unit)
- `app/src/test/java/io/casehub/life/app/engine/LifeChannelContextProviderIntegrationTest.java`

**Modified files:**
- `app/src/main/java/io/casehub/life/app/engine/LifeHeartbeatJob.java` — inject provider, merge context
- `app/src/main/java/io/casehub/life/app/service/ledger/LifeOutcomeAttestationWriter.java` — ledger import fix
- `app/src/test/java/io/casehub/life/app/service/LifeOutcomeAttestationWriterTest.java` — ledger import fix
- `app/src/main/resources/application.properties` — add channel-context.message-limit config

**No YAML changes.** All sentinel capabilities, bindings, and escalation humanTask
bindings already exist in the 7 case YAMLs (delivered by life#37).

**No Flyway migrations required.**
