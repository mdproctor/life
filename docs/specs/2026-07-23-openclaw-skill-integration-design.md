# OpenClaw Skill Integration — Design Spec

**Issue:** casehubio/life#60
**Date:** 2026-07-23
**Status:** Approved

---

## 1. Purpose

Transform life's 32 LLM-backed workers and 7 sentinel heartbeats from reasoning-only
agents into tool-using agents that interact with external services — calendar, IoT,
banking, and messaging — during their OpenClaw `/hooks/agent` turns.

Currently, agents receive a system prompt, reason about the input, and produce structured
output. They cannot schedule a real calendar event, read an actual sensor, check a bank
balance, or send a WhatsApp message. This issue wires real tool access.

---

## 2. Architecture — Two-Tier Skill Model

### 2.1 Tiers

```
Tier 1 (NATIVE)    — CaseHub MCP tools served from Quarkus app or sibling repos
                     Full platform properties: RBAC, audit, trust, GDPR, risk classification
                     Per-call authorization via CurrentPrincipal

Tier 2 (OPENCLAW)  — OpenClaw community skills from ClawHub
                     Turn-level accountability via plugin hooks (coarser)
                     No CaseHub RBAC enforcement per tool call
```

Promotion from OPENCLAW → NATIVE is transparent to agents — same tool interface,
CaseHub MCP endpoint takes priority. Decision driven by RBAC need, audit need,
trust scoring need, risk classification need, GDPR scope, or reliability.

### 2.2 Authorization Model

**Tier 1 (NATIVE) — per-call authorization at the MCP endpoint:**

```
Agent calls MCP tool
  → Quarkus MCP endpoint (quarkus-mcp-server-http)
    → @RolesAllowed check (SecurityIdentity from OIDC token)
    → SPI call (calendar, IoT, messaging)
    → Audit + trust (LedgerEntry, attestation)
    → Response to agent
```

Principal propagation prerequisite: when OpenClaw invokes a CaseHub MCP tool on
behalf of a household user, the inbound HTTP request must carry the user's identity.
Mechanism TBD — options include OAuth2 token exchange (on-behalf-of), a signed
identity header from OpenClaw, or service-account + user-context claim forwarding.
Without this, `@RolesAllowed` enforcement is meaningless. Filed as a cross-repo
prerequisite (§8).

**Tier 2 (OPENCLAW) — turn-level accountability only:**

OpenClaw-tier tools have no CaseHub RBAC enforcement per tool call. The plugin
hooks (`before_tool_call`, `agent_end`) provide turn-level accountability — the
turn is attributed to a household user, but individual tool calls within the turn
are not gated.

**Risk classification is workflow-level, not tool-call-level:**

`LifeActionRiskClassifier` operates on `PlannedAction` objects from case
definitions — it gates planned work items before execution, not individual tool
calls during an agent turn. This is unchanged by this spec (§10). For NATIVE
tools, `@RolesAllowed` at the MCP endpoint is the per-call gate. For OPENCLAW
tools, there is no per-call gate — this is the accepted trade-off of the
OPENCLAW tier, and a promotion driver (§9).

### 2.3 SPI Bank — Distributed Across Repos

The capability SPIs are not centralized. Each repo owns its domain:

| Repo | Domain | SPI | Native Providers | Testing |
|------|--------|-----|-----------------|---------|
| connectors | Messaging | `ChatPlatform` ✅ | Slack, Discord, WhatsApp, SMS, Teams, Email ✅ | `chat-ref` ✅ |
| connectors | Calendar | `CalendarPlatform` (to build) | Google Calendar via jgccli (to build) | `calendar-ref` (to build) |
| iot | IoT | `DeviceProvider` ✅ | Home Assistant, OpenHAB ✅ | `MockDeviceProvider` ✅ |
| connectors | Banking | (to build) | — | (to build) |

Each SPI follows the same shape:
1. SPI interfaces (the contract)
2. Reference/mock implementation (for testing)
3. Native providers (production)
4. MCP tools (agent access)
5. OpenClaw fallback provider (long tail — other systems not natively supported)

### 2.4 SPI Hybrid Pattern

Calendar demonstrates the pattern. Native Google Calendar for the primary provider,
OpenClaw fallback for everything else (Outlook, CalDAV, etc.):

```
CalendarPlatform (SPI interface)
  ├── GoogleCalendarPlatform (native, jgccli)
  └── OpenClawCalendarPlatform (fallback → delegates to OpenClaw skill)

CalendarMcpTool → CalendarPlatformService → routes to configured provider
```

The agent calls `calendar_create_event`. The MCP tool routes through the SPI.
The agent never knows which provider handled it. Platform properties (audit, trust,
GDPR) wrap the SPI call regardless of provider.

Messaging already follows this pattern — `ChatPlatformMcpTool` routes to any
configured `ChatPlatform` implementation.

### 2.5 Tool Discovery

All agents get full MCP access to all available tools. No per-agent tool scoping.
System prompts guide which tools are most relevant for the task, but any agent can
reach any tool. A finance-agent that spots an urgent anomaly can send a WhatsApp
message. A home-agent checking sensors can also check the calendar.

RBAC and ACL layer on top — full discovery, authorized execution.

### 2.6 UI Composition

IoT and other repos are developing web components (Lit, Quinoa). Life-ui already
composes from `@casehubio/agency-ui-*` and `@casehubio/pages-*`. IoT device panels,
sensor dashboards, and control UIs are another composition source.

Response data structures must be compatible with what IoT's UI components expect.
When a home-agent reads sensor state via the IoT SPI, the data flows to both the
worker response schema and the UI — not two different representations.

---

## 3. Domain Mapping

### 3.1 Initial Tier Assignment

| Domain | Tier | Rationale |
|--------|------|-----------|
| Messaging | NATIVE | connectors already has ChatPlatform SPI + 6 providers + MCP tools |
| IoT | NATIVE | iot repo has DeviceProvider SPI + HA/OpenHAB. Needs MCP tool exposure |
| Calendar | HYBRID | Native Google Calendar (jgccli) + OpenClaw fallback for others |
| Banking | OPENCLAW | No native SPI yet. OpenClaw community skills for now |

### 3.2 Agent-to-Tool Usage (Guidance, Not Restriction)

| Agent | Primary tools | Sentinel tools |
|-------|--------------|----------------|
| home-agent | `iot_get_devices`, `iot_get_state`, `calendar_create_event`, `send_chat` | `iot_get_state`, `send_chat` |
| health-agent | `calendar_create_event`, `calendar_list_events`, `send_chat`, `iot_get_state` | `calendar_list_events`, `send_chat`, `iot_get_state` |
| finance-agent | `bank_get_transactions`, `bank_get_balances`, `send_chat` | `bank_get_transactions`, `send_chat` |
| travel-agent | `calendar_create_event`, `calendar_list_events`, `send_chat` | `calendar_list_events`, `send_chat` |

All agents have full MCP access. This table documents primary usage patterns —
the system prompts reference these tools as the primary workflow, but agents can
use any available tool.

---

## 4. System Prompt Changes

### 4.1 Prompt Design Principles

1. **Name the tools** — agents discover MCP tools automatically, but naming them
   directs usage and reduces hallucinated tool calls
2. **Describe the workflow** — "read sensors FIRST, then schedule, then report"
3. **Require tool-derived data in output** — "include the event ID from the calendar
   tool" forces actual tool usage rather than fabrication
4. **CBR suffix unchanged** — the existing `CBR_SYSTEM_PROMPT_SUFFIX` appends cleanly

### 4.2 Before/After Example

**Before (reasoning-only):**
```
You are a home maintenance agent. Schedule a property inspection,
assess the condition, and report findings.
```

**After (tool-aware):**
```
You are a home maintenance agent. Use iot_get_devices and iot_get_state
to read current sensor state for the property. Use calendar_create_event
to schedule the inspection with the appropriate service provider.
Report findings including actual sensor readings and the calendar event ID.
If sensors show anomalies (temperature, humidity, movement), flag them
in your assessment. Use send_chat to notify the household if urgent
issues are detected.
```

### 4.3 Scope

All 32 worker prompts and 7 sentinel prompts are updated. Each prompt is reviewed
for which tools are most relevant to its specific task and rewritten to describe
the tool-assisted workflow.

---

## 5. Response Schema Changes

### 5.1 Schema Evolution Principles

1. **Tool-derived fields are typed** — `calendarEventId`, not a blob. The UI and
   downstream processing rely on them.
2. **`toolsUsed` on every schema** — `List<String>` recording which tools the agent
   called. Populated by the LLM in its structured response (the response schema
   instructs the agent to list tools it invoked). Convenience field for UI display
   and debugging. **Not authoritative for trust scoring or audit** — LLM
   self-reporting is unreliable (hallucinated or omitted tool calls). Trust scoring
   and ledger attestation require infrastructure-reported tool usage from the
   OpenClaw turn execution log (§8, cross-repo prerequisite).
3. **LLM assessment fields remain** — agents still reason. Tools provide data; the
   agent interprets it.
4. **Nullable tool fields** — if a tool call fails or the agent skips it, null.
   Downstream processing handles both paths.

### 5.2 Example Evolution

**Before (actual current schema):**
```java
public record ScheduleInspectionResult(
    boolean inspected,
    String condition,
    String inspectionDate) {}
```

**After:**
```java
public record ScheduleInspectionResult(
    boolean inspected,
    String calendarEventId,
    LocalDate scheduledDate,
    List<DeviceEntity> sensorReadings,
    String condition,
    String findings,
    String recommendedActions,
    List<String> toolsUsed) {}
```

**Type sourcing for tool-derived fields:**

Response schema types come from the module that owns the tool, not from life.
No mapping layer — the response schema uses the same types the MCP tool returns.

| Field type | Defined in | Status | Structure |
|-----------|-----------|--------|-----------|
| `DeviceEntity` | `casehub-iot-api` | Exists — abstract class with `@JsonTypeInfo(use = CUSTOM, property = "@deviceType")` polymorphism. 11 subclasses: `ThermostatDevice`, `SensorDevice`, `LightDevice`, `SwitchDevice`, `CoverDevice`, `LockDevice`, `FanDevice`, `CameraDevice`, `MediaPlayerDevice`, `PowerSensor`, `PresenceSensor` | Typed fields directly on each subclass (e.g., `ThermostatDevice` has `Temperature currentTemperature`, `Temperature targetTemperature`, `ThermostatMode mode`) |
| Calendar event type | `casehub-connectors-api` | To be defined with `CalendarPlatform` SPI (§8) | Expected: `eventId`, `title`, `start`, `end`, `location`, `attendees` |
| Banking transaction type | OpenClaw skill pack | To be defined by skill pack — not a CaseHub type | Expected: `transactionId`, `amount`, `currency`, `date`, `description`, `category` |

Life takes a compile dependency on `casehub-iot-api` for `DeviceEntity`. Calendar
types arrive with the connectors CalendarPlatform SPI work. Banking types are
OpenClaw-native — life uses `Map<String, Object>` for OPENCLAW-tier tool data
where no CaseHub type exists (§2.1 trade-off: OPENCLAW has coarser type safety).

### 5.3 Scope

All 32 worker response schemas and 7 sentinel report schemas gain:
- Domain-specific tool-derived fields (event IDs, sensor readings, balances, message IDs)
- `List<String> toolsUsed` uniformly

**Sentinel field examples** (actual class names from codebase):

| Sentinel | Agent | Tool-derived fields gained |
|----------|-------|--------------------------|
| `MaintenanceSentinelReport` | HOME | `List<DeviceEntity> deviceStates` (from `iot_get_state`), `String notificationMessageId` (from `send_chat`) |
| `ContractorSentinelReport` | HOME | `String notificationMessageId` (from `send_chat`) |
| `BookingSentinelReport` | HEALTH, TRAVEL | `String calendarEventId` (from `calendar_list_events`), `String reminderMessageId` (from `send_chat`) |
| `AnomalySentinelReport` | FINANCE | `Map<String, Object> transactionSummary` (from `bank_get_transactions` — OPENCLAW tier, no CaseHub type), `String alertMessageId` (from `send_chat`) |
| `CareQualitySentinelReport` | HEALTH | `String calendarEventId` (from `calendar_list_events`), `String notificationMessageId` (from `send_chat`) |
| `PatientStatusSentinelReport` | HEALTH | `String reminderMessageId` (from `send_chat`) |
| `FollowUpSentinelReport` | any | `String notificationMessageId` (from `send_chat`) |

Types follow the same sourcing as §5.2: `DeviceEntity` from `casehub-iot-api`,
calendar types from connectors when CalendarPlatform SPI lands, banking data as
`Map<String, Object>` (OPENCLAW tier — no CaseHub type).

---

## 6. OpenClaw Configuration

### 6.1 MCP Server Registration

MCP servers are configured **per-persona on the OpenClaw side**, not passed
per-invocation from Life. `OpenClawAgentProvider.invoke()` does not forward
`AgentSessionConfig.mcpServers` — the transport sends only the prompt and
delivery URL. MCP server configuration is part of the OpenClaw persona
definition maintained in OpenClaw's persona registry.

All 4 agent personas (HEALTH, HOME, FINANCE, TRAVEL) share the same MCP server
set. The 32 workers across 8 CaseHub classes map to these 4 personas — e.g.,
`HomeMaintenanceCaseHub` and `ContractorCoordinationCaseHub` both use
`LifeAgent.HOME` ("home-agent"). All workers sharing a persona get the same
tool set; system prompts differentiate tool usage within a shared persona.

Target persona MCP configuration:

```json
{
  "mcp": {
    "servers": {
      "casehub": {
        "transport": "streamable-http",
        "url": "${CASEHUB_BASE_URL}/mcp"
      },
      "casehub-iot": {
        "transport": "streamable-http",
        "url": "${IOT_BRIDGE_URL}/mcp"
      },
      "calendar": {
        "transport": "stdio",
        "command": "jgccli mcp-server"
      }
    }
  }
}
```

**`casehub` MCP endpoint:** Requires `quarkus-mcp-server-http` on life's
classpath (§8, prerequisite). This auto-configures the `POST /mcp` endpoint
and serves all `@Tool`-annotated beans — including `ChatPlatformMcpTool` from
connectors/mcp already on the classpath. No application code needed for the
endpoint itself.

**`jgccli`:** A planned CLI wrapper for Google Calendar API access, following
the `mcp-server` subcommand pattern (like `npx @anthropic/mcp-server-*`).
Wraps Google Calendar API operations as MCP tools via stdio transport. To be
built as part of the calendar SPI work in connectors (§8).

**Banking:** Uses OpenClaw community skill packs from ClawHub — not CaseHub MCP
tools. Tool names like `bank_get_transactions` in §3.2 are tools provided by
the skill pack's own MCP server configuration, not by CaseHub. The agent
discovers and calls these tools through OpenClaw's skill ecosystem. No CaseHub
MCP server entry needed.

### 6.2 Life-Side Config

```properties
# Skill tier declarations
casehub.life.skills.messaging.tier=native
casehub.life.skills.iot.tier=native
casehub.life.skills.calendar.tier=native
casehub.life.skills.banking.tier=openclaw
```

Declarative — documents intent for future promotion tooling.

---

## 7. Testing Strategy

### 7.1 Test Factory

`TestLifeOpenClawChatModelFactory` returns tool-enriched canned responses. The factory
does not mock MCP tool calls — it mocks complete agent responses (which include data
the agent obtained from tools).

**Before (matches actual `ScheduleInspectionResult`):**
```json
{"inspected": true, "condition": "good", "inspectionDate": "2026-08-15"}
```

**After (matches §5.2 evolved schema):**
```json
{"inspected": true,
 "calendarEventId": "evt_abc123", "scheduledDate": "2026-08-15",
 "sensorReadings": [
   {"@deviceType": "thermostat", "deviceId": "sensor-01",
    "currentTemperature": 21.3, "targetTemperature": 22.0, "mode": "HEAT"},
   {"@deviceType": "sensor", "deviceId": "sensor-02",
    "humidity": 45, "battery": 87}
 ],
 "condition": "good", "findings": "Sensors nominal",
 "recommendedActions": "Annual check",
 "toolsUsed": ["iot_get_state", "calendar_create_event"]}
```

The `sensorReadings` array uses IoT's `DeviceEntity` Jackson serialization:
`@deviceType` is the polymorphic type discriminator (from `@JsonTypeInfo`),
typed fields are flat on each device object (e.g., `ThermostatDevice` fields,
`SensorDevice` fields).

### 7.2 Testing Layers

| Layer | What it tests | How |
|-------|--------------|-----|
| Unit (schemas) | New fields serialize/deserialize | Plain JUnit |
| Unit (prompts) | System prompts reference correct tool names | Assert on prompt strings |
| Integration (`@QuarkusTest`) | Full worker execution with tool-enriched responses | `TestLifeOpenClawChatModelFactory` |
| Integration (IoT) | IoT SPI mock provides device state | `casehub-iot-testing` `MockDeviceProvider` |
| Integration (messaging) | Chat SPI mock handles send_chat | connectors `chat-ref` |
| Contract | Response schemas match UI component expectations | Schema validation tests |

No end-to-end OpenClaw tests in life's repo. Life tests verify prompts, schemas,
and config are correct. OpenClaw behavior is tested in OpenClaw.

---

## 8. Cross-Repo Prerequisites

| Repo | Issue | Description | Life blocking? |
|------|-------|-------------|---------------|
| life | **To file** | Add `quarkus-mcp-server-http` dependency to serve CaseHub MCP endpoint at `/mcp` | Yes — prerequisite for NATIVE tools |
| openclaw | **To file** | Infrastructure-reported tool usage: return tool call log from `/hooks/agent` turn alongside text response, for trust scoring and audit | No — `toolsUsed` works as UI convenience until delivered |
| openclaw | **To file** | Principal propagation: mechanism for forwarding household user identity on inbound MCP tool calls from OpenClaw agents | Yes — prerequisite for NATIVE tool RBAC |
| connectors | **To file** | Calendar SPI + ref + Google provider: `CalendarPlatform` SPI, `calendar-ref` for testing, `calendar-google` wrapping jgccli. Follow `chat-spi` pattern. | No — life mocks until delivered |
| connectors | **To file** | Calendar MCP tools: `CalendarMcpTool` following `ChatPlatformMcpTool` pattern | No — life mocks until delivered |
| iot | **To file** | MCP tool exposure: serve `DeviceProvider` operations as MCP tools (`iot_get_devices`, `iot_get_state`, `iot_send_command`) | No — life uses `MockDeviceProvider` |
| iot | **To file** | UI component export: device panels/sensor dashboards composable in life-ui | No — parallel concern |

**Status:** Issues above are identified but not yet filed. They must be filed
before implementation begins. Life's work does NOT block on most of these —
tests use mocks and reference implementations. Production features light up
progressively as MCP tools and infrastructure come online.

---

## 9. Promotion Criteria

When to promote a tool from OPENCLAW to NATIVE:

| Criterion | Example |
|-----------|---------|
| RBAC enforcement needed | Junior user shouldn't send contractor payment messages |
| Per-call audit trail | Health appointment booking needs tamper-evident ledger entry |
| Trust scoring | Contractor calendar reliability feeds deadline-reliability dimension |
| GDPR scope | Tool handles personal data subject to erasure requests |
| Risk classification | Booking above threshold needs household-admin approval |
| Reliability | OpenClaw community skill is flaky or poorly maintained |

Promotion is transparent — same MCP tool name, CaseHub endpoint takes priority.

---

## 10. What Is NOT In Scope

- Building MCP servers in connectors or iot (prerequisites filed in §8)
- Dynamic skill discovery UI (future — browse/enable OpenClaw skills for household)
- Native banking SPI (banking stays OpenClaw-only until usage patterns clarify)
- UI composition of IoT components in life-ui (parallel concern)
- Changes to `LifeTypedCaseHub`, `agentWorker()`, `DirectCallBridge`, `OpenClawChatModel`,
  or `OpenClawAgentProvider` (transport layer unchanged — MCP server config is
  per-persona on OpenClaw side, not per-invocation)
- Changes to risk classification, trust routing, or CBR (risk classification operates
  on `PlannedAction` objects from case definitions at the workflow level; `@RolesAllowed`
  at the MCP endpoint is the per-tool-call gate for NATIVE tools)
- Principal propagation mechanism (prerequisite filed in §8 — blocked on
  openclaw design decision)
