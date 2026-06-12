# Home Automation — Research & Design Findings
**Date:** 2026-06-05  
**Context:** Pre-spec research for `casehub-iot` foundation module and home automation use case

---

## Design Decisions

### 1. Foundation module + casehub-life as the application layer

**Foundation:** Device abstraction lives in a new foundation repo (`casehub-iot`), peer to `casehub-connectors`. Any future application (property management, elder care monitoring, industrial IoT) consumes it without duplicating provider code.

**Application:** Home automation case types, `CasePlanModel` definitions, Drools rules, and device binding configuration live in **casehub-life**, not a new repo. Home automation IS household management — already a `LifeDomain`. Keeping it in casehub-life means the `HolidayTrip` → home mode coordination is case-to-case within the same application, not cross-app event bridging. Community automations deploy into casehub-life as Quarkus extensions.

**Rationale:** The same HA/OpenHAB device layer could power casehub-life, a property management app, elder care monitoring, and more. Application-scoped would require extraction later. Foundation-first is consistent with how connectors handles messaging. A separate `casehub-home` repo is explicitly rejected — it would split one coherent domain (household life automation) across two applications.

---

### 2. Common interface first, vendor supplement only for genuine unmappables

The typed device class hierarchy in `casehub-iot-api` carries as much as cross-vendor mappings support. Provider implementations (HA, OpenHAB) map their native models to common types. Vendor supplement subclasses exist only for fields that have no cross-vendor equivalent.

**Rationale:** Normalising down to the lowest common denominator (generic Map) discards information. Normalising up to the richest typed model — with providers doing the mapping work — gives workers a consistent, type-safe surface while preserving platform-specific capabilities via supplements.

**Pattern source:** Ledger supplement pattern (`LedgerEntry` → `AmlCaseOpenedLedgerEntry`). The supplement IS the entity, not a parallel object.

---

### 3. Device class vocabulary aligned with Matter

Common device class names in `casehub-iot-api` (`SwitchDevice`, `ThermostatDevice`, `LightDevice`, `SensorDevice`, `LockDevice`, `CoverDevice`, etc.) align with Matter's published Device Type Library where vocabulary overlaps.

**Rationale:** Matter is an industry-standard device type taxonomy that both HA and OpenHAB now support. Aligning with it gives a principled naming scheme, avoids reinventing vocabulary, and provides a natural path to a future Matter provider without renaming the common hierarchy.

---

### 4. StateChangeEvent carries changedCapabilities

```java
StateChangeEvent {
    DeviceEntity before;
    DeviceEntity after;
    Set<String> changedCapabilities; // e.g. ["targetTemperature"]
}
```

OpenHAB populates `changedCapabilities` directly from the item event — it already knows exactly what changed. HA populates it by diffing old/new entity attributes (HA events include previous state).

**Rationale:** OpenHAB's per-item SSE events are field-level deltas — more precise than HA's entity-level snapshots. Discarding this precision by normalising to assembled snapshots would force workers to diff before/after themselves. The correct direction is to make HA produce the same field-level precision via diffing, not to make OpenHAB coarser. Drools rules can then pattern-match on `"targetTemperature" in changedCapabilities` without caring which platform fired the event.

---

### 5. CDI async event bus for state routing (Approach A)

`StateChangeEvent` fired via CDI `Event.fireAsync()`. Applications observe events they care about and decide what to do (create cases, update worker state, dispatch commands). Consistent with how `InboundConnector` fires `InboundMessage`.

**Rejected alternatives:**
- **Trigger SPI** (foundation evaluates declarative trigger rules): deferred until real applications show which patterns are worth standardising.
- **Drools as the router**: couples routing to a specific worker type; too early to commit.

---

### 6. OpenHAB provider state cache

The OpenHAB provider maintains `Map<EquipmentId, DeviceEntity>` as authoritative assembled state. Item-level SSE events are internal implementation details that drive cache updates. Consumers receive `StateChangeEvent` for the assembled `DeviceEntity`, never per-item events.

HA provider maintains a cache too, for consistency — both providers surface state identically.

**Rationale:** OpenHAB's SSE stream fires `ItemStateChangedEvent` per item. Assembling a `ThermostatDevice` from a setpoint item event requires current state of sibling items (current temperature, mode). The cache provides this. Multiple item events within a short window produce one coherent device state event.

---

### 7. Community automations as Quarkus extensions

Case automations (CasePlanModel + Drools rules + Flow definitions + OptaPlanner configurations) packaged as Quarkus extensions. CDI bean discovery, config namespace isolation, classpath activation.

**Rationale:** Extensions get isolation and discoverability for free. A community "Morning Routine" automation declares `casehub.automation.morning-routine.thermostat-device=...` config and activates by adding the JAR dependency. No code changes to the host application.

**Implication:** `casehub-iot-api` common device types are a public API surface. Semver discipline from day one — community automations will depend on them.

---

### 8. Named qualifier pattern for device binding

Community automations declare which device roles they need (`@ThermostatDevice("zone-heating")`), not which specific device IDs. Deployment configuration maps role names to actual device IDs. Automations are portable across installations.

---

### 9. Three deployment modes supported

1. **Local** — CaseHub runs alongside HA/OpenHAB on same machine or LAN. No internet dependency. Privacy-first self-hosters.
2. **Bridge** — Lightweight local bridge connects to HA/OpenHAB, forwards `StateChangeEvent`s to cloud CaseHub, relays `DeviceCommand`s back. Compelling for property managers managing many sites.
3. **Hybrid** — Drools rules run at edge (inside bridge) for latency-sensitive reactions. Flow, OptaPlanner, HITL, ledger, memory run in cloud. `StateChangeEvent` is the natural wire protocol between bridge and cloud.

**Rationale:** See Market Findings section — this position is genuinely unoccupied.

---

## Technical Findings

### Home Assistant entity model

- Strongly typed by **domain** (`climate`, `light`, `cover`, `lock`, `binary_sensor`, `sensor`, etc.)
- Within-domain typing via `device_class` (27 binary_sensor classes, 50+ sensor classes, 10 cover classes, etc.)
- One entity per capability group — `climate.living_room` has current temp, setpoint, HVAC mode, fan mode, preset mode all in one entity and one event
- Events include previous state — diffing to produce `changedCapabilities` is straightforward
- Consistent across all integrations implementing the same domain (Hue light and Z-Wave light both implement `light` domain identically)

### OpenHAB item model

- **Structural layer**: Thing → Channel → Item. Things are physical devices; Items are abstract state holders linked to Channels via bindings. Item types are data-type-centric: Switch, Dimmer, Color, Number, Contact, String, Rollershutter, Player, Location, etc.
- **Semantic model** (since OH 3): Equipment groups Items into logical devices via tags (Lightbulb, HVAC, MotionDetector, etc.). Equipment = Group item. Points = member items.
- A "light" in OpenHAB is a convention: Switch item + Dimmer item + Color item, grouped under a `Lightbulb`-tagged Equipment Group.

### OpenHAB REST API

- `GET /rest/things/{thingUID}` — structural metadata only. `linkedItems` contains Item name strings, not states.
- `GET /rest/items/{equipmentGroupName}` — returns Group with all member Item states. **Single call, assembled state** — IF the semantic model is set up.
- `GET /rest/events` (SSE) — fires `ItemStateChangedEvent` per item (includes old and new value, type). Thing events are connectivity status only (ONLINE/OFFLINE). No device-level state events.
- No dedicated semantic REST endpoint for "give me all Equipment" — semantic queries are in-process only (rules engine).

**Key constraint:** OpenHAB provider has a hard dependency on users having the semantic model configured (Equipment as Groups with Points as members). Many OpenHAB users have not set this up. Document as a prerequisite; consider fallback via Thing-scoped item discovery.

### Structural impedance mismatch

| Dimension | Home Assistant | OpenHAB |
|---|---|---|
| Device representation | One entity per capability group | Multiple Items per Equipment Group |
| State retrieval | One entity API call | One Group API call (if semantic model set up) |
| State change events | Entity-level snapshots | Per-item field-level deltas (more precise) |
| What changed | Must diff old/new entity | Known directly from item event |

The CaseHub provider cache bridges this: OpenHAB item events → cache update → assembled `StateChangeEvent`. HA entity events → cache update → `StateChangeEvent` with derived `changedCapabilities`.

### Matter

- Abstracts **physical devices below HA and OpenHAB**, not the platform APIs above them. Both platforms sit on top of Matter as controllers/commissioners.
- Does not provide a unified API that normalises HA entities and OpenHAB items.
- Matter coverage gaps: legacy protocols (Zigbee, Z-Wave without bridges), advanced HVAC, proprietary device features, cloud-only remote access.
- HA has supported Matter since 2022, CSA-certified March 2025. OpenHAB added Matter binding in 5.0 (2025) via matter.js SDK.
- **Verdict:** Matter's device type taxonomy (Light, Thermostat, Lock, etc.) is a useful vocabulary reference for `casehub-iot-api` class naming. Matter itself does not solve the integration problem.

### W3C Web of Things (WoT) Thing Description

- Generic metadata schema: Properties, Actions, Events. Protocol-agnostic.
- No production HA or OpenHAB integration. No typed device class hierarchy — device semantics must be added via JSON-LD or external ontologies.
- Zero traction in residential home automation.
- **Verdict:** Not useful for the CaseHub abstraction problem.

### No existing Java library exists

No production Java or Quarkus library provides a typed `Light`, `Thermostat`, `Cover` abstraction over both HA REST/WebSocket and OpenHAB REST. The gap is confirmed. The closest is JHAAPI (unmaintained HA REST wrapper).

---

## Market & Business Findings

### Home automation community preference

Strongly local-first. HA's tagline: "local, private, open source." The community has been burned repeatedly:
- SmartThings outages taking homes offline
- Wink bankruptcy bricking hubs
- Insteon server shutdown
- Numerous cloud-dependent platforms going dark

Deep distrust of cloud dependency is structural, not incidental. HA's core audience is allergic to anything that requires internet for basic functionality.

### Current platform landscape

| Platform | Local execution | Cloud role |
|---|---|---|
| Home Assistant | 100% local | Nabu Casa = remote access tunnel only, no processing |
| Hubitat | 100% local | Optional backup/remote admin only |
| OpenHAB | 100% local | myopenHAB = cloud relay, no processing |
| SmartThings | Partial local | Mechanical fallout of Edge Driver type, not deliberate latency routing |
| Control4 / Crestron / Savant | 100% local | Integrator management only |
| Josh.ai | NLP in cloud | Executes via local control system drivers; perceptible latency noted by integrators |

### The unoccupied position

**"SaaS orchestration layer above HA/OpenHAB"** — completely absent from shipping products. No commercial or notable open-source project positions itself as a cloud-intelligent layer that:
- Sits above existing hubs rather than replacing them
- Augments without requiring migration
- Intelligently routes rule execution based on latency requirements
- Provides rich worker variety (rules engine + workflow engine + optimizer + AI)

Node-RED is the closest analogy: flow-based orchestration sometimes used inside HA. Local-only, no formal case management, no HITL, no audit, no workers beyond basic flow steps.

Generic edge runtimes (AWS Greengrass, Azure IoT Edge) have the edge/cloud split architecture but carry zero home automation semantics and are industrial/enterprise tools.

**The market has bifurcated into local-only vs cloud-primary with the gap between them unoccupied.** CaseHub's three-tier model occupies that gap.

### Property management as SaaS wedge

A property manager running 50 rental properties cannot maintain CaseHub on 50 Raspberry Pis. Cloud + bridge is the natural model: one central CaseHub instance, each property has a lightweight bridge, all tenants managed centrally. YAML automation definitions updateable across all properties from one place without local redeployment.

This is a commercially compelling use case that justifies the SaaS tier and creates the monetisation path while keeping the local mode free for the self-hosted community (which drives adoption and ecosystem).

---

## Value Propositions (What CaseHub Adds That HA/OpenHAB Cannot)

### "Your house needs approval before it does something important"
Qhorus oversight gates with formal commitment tracking. A delivery driver arrives → CaseHub opens a COMMAND on the household-admin channel → waits for RESPONSE. No response in 5 minutes → escalates to household-member quorum. Still nothing → declines the action and logs why. HA can call a webhook. It cannot track commitment, escalate on silence, or enforce quorum.

### "One event, coordinated response"
Smoke detector triggers → `FireAlert` case opens → Drools detects pattern → Flow sequences: notify household via Qhorus, HITL gate for false-positive acknowledgment, dispatch emergency services via connectors if not acknowledged in 2 minutes, write ledger entry throughout. Not YAML — a formal case with SLA enforcement and complete audit trail.

### "Prove it"
Merkle-verified, independently auditable ledger of every home automation decision. Insurance disputes, energy supplier billing disputes, landlord-tenant maintenance notifications — all provable from the tamper-evident record. No other home automation platform has this.

### "Optimize, don't just automate"
OptaPlanner schedules EV charging, dishwasher, washing machine, and HVAC pre-heating as a single optimization problem — considering solar generation forecast, tariff bands, occupancy patterns, and car departure times across a week. The difference between a rule and a solver.

### "Your home and your life are one system"
A `HolidayTrip` case opens in casehub-life → the same application opens a `HolidayHomeMode` case that adjusts heating, heightens security, dispatches mail stop notification via connectors. Trip case closes → home case closes. No cross-app event bridging — it is case-to-case coordination within casehub-life. HA and OpenHAB have no model for this kind of life-domain-aware device orchestration.

### "Ask your house"
OpenClaw speech act interaction. "Why did the heating come on at 3am?" → formal agent response on the observe channel, backed by the ledger entry recording the actual trigger. DECLINE the explanation and request a different agent. Tracked, auditable, trust-scored. Not a chatbot.

### "Your house gets smarter"
CaseMemoryStore retains prior case outcomes. Trust routing sends higher-stakes decisions to agents with better track records. A security agent that correctly suppressed 47 false positives (the cat) earns higher trust and gets harder decisions routed to it automatically.

### "Install it, it just works"
Add `casehub-automation-morning-routine` as a Maven dependency. Configure device IDs. That automation runs on HA today, OpenHAB tomorrow — written against `LightDevice`, `ThermostatDevice`, not platform-specific YAML or DSL. Community marketplace of cross-platform, cross-worker automations that no other platform has.

### "Choose how much runs locally"
Privacy-first: run everything on-premises, no internet dependency. Property manager: cloud SaaS with bridge per property, centrally managed. Hybrid: latency-sensitive Drools rules at edge, optimization and audit in cloud. All three modes, one automation library.

---

### OpenClaw integration

Home automation in casehub-life uses OpenClaw via the same pattern as casehub-life Layer 7 (pending). An AI agent reasoning about device state — "should I adjust the thermostat given current occupancy and forecast?", "why did the heating come on at 3am?" — is provisioned via `ClaudonyReactiveWorkerProvisioner`, receives context via `WorkerContextProvider` (device state + case history + `CaseMemoryStore` prior context), responds through Qhorus channels (observe for status, oversight for HITL), and is trust-scored via casehub-ledger. No new OpenClaw integration pattern is required — the "ask your house" value proposition is Layer 7 applied to the household device domain.

---

## Open Questions (Not Yet Resolved)

- OpenHAB provider fallback strategy when semantic model is not configured — Thing-scoped item discovery as alternative discovery path.
- Bridge offline resilience — which case types continue locally when cloud is unreachable in hybrid mode.
- How `StateChangeEvent`s from `casehub-iot` trigger case creation within casehub-life — CDI observer pattern within the same application, no cross-app bridge needed.
- `casehub-iot-api` versioning policy — semantic versioning discipline required since community automations depend on it.
