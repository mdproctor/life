# casehub-life Layer 9 — Home Automation Design Spec
**Date:** 2026-06-05  
**Foundation spec:** `casehubio/casehub-iot` — `docs/superpowers/specs/2026-06-05-iot-foundation-design.md`  
**Research:** `docs/superpowers/research/2026-06-05-home-automation-research.md`  
**Depends on:** `casehub-iot` published to GitHub Packages before this layer begins

---

## Overview

Layer 9 extends `casehub-life` with home automation support, integrating the `casehub-iot` foundation module as the device layer. Home automation IS household management — `LifeDomain.HOUSEHOLD` expands to include device-driven cases. No separate repo.

**New dependencies (user chooses platform):**
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-iot-api</artifactId>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <!-- choose one: -->
    <artifactId>casehub-iot-homeassistant</artifactId>
    <!-- or -->
    <artifactId>casehub-iot-openhab</artifactId>
</dependency>
```

---

## Trigger Mechanism

`HomeAutomationEventObserver @ApplicationScoped` observes `StateChangeEvent` asynchronously and evaluates two trigger sources in order:

1. **`YamlTriggerRegistry`** — simple YAML-defined conditions (device + capability + value + time window). Community automations register triggers here via CDI bean discovery at startup.
2. **`DroolsTriggerSession`** — complex multi-condition triggers (temporal patterns, cross-device correlation, statistical thresholds). Fires `TriggerFired` facts collected by the observer.

Results merged, deduplicated, filtered for already-open cases, then cases opened.

```java
@ApplicationScoped
public class HomeAutomationEventObserver {

    void onStateChange(@ObservesAsync StateChangeEvent event) {
        Stream.concat(
            yamlTriggerRegistry.evaluate(event),
            droolsTriggerSession.evaluate(event)
        )
        .distinct()
        .filter(trigger -> !caseAlreadyOpen(trigger))
        .forEach(trigger -> openCase(trigger.planModelId(), event));
    }
}
```

Duplicate prevention: query open `CaseInstance`s for same case type + primary device before opening. If already open, update the existing case.

---

## YAML Trigger Definition

Community automations bundle `triggers.yaml` alongside their `CasePlanModel`:

```yaml
triggers:
  - id: morning-routine
    planModel: morning-routine
    conditions:
      - device: "@morning-motion-sensor"    # named qualifier — bound in config
        capability: isPresent
        value: true
        timeWindow: "06:00-09:00"
      - device: "@main-light"
        capability: isOn
        value: false
  - id: security-alert
    planModel: security-alert
    conditions:
      - device: "@entry-motion-sensor"
        capability: isPresent
        value: true
        changedCapabilities: [isPresent]   # only on transition, not on poll
      - timeCondition: "after 23:00"
```

---

## DeviceCommand Authorization

casehub-life enforces household permission checks before calling `DeviceProvider.dispatch()`. The `iot-api` itself is authorization-agnostic.

| Action | Required role | Qhorus oversight gate |
|---|---|---|
| turn_on / turn_off lights | household-junior | No |
| set_temperature | household-member | No |
| lock / unlock | household-member | Yes — COMMAND → RESPONSE |
| disable security | household-admin | Yes + M-of-N quorum |

Oversight gate pattern is identical to existing casehub-life HITL gates (Layer 3 Qhorus integration).

`ProviderStatusEvent` (DISCONNECTED) observed to open `ConnectivityAlertCase` — notifies household-admin via connectors.

---

## Case Types

```
MorningRoutineCase      episodic    — presence + time window trigger
EveningWindDownCase     episodic    — time + occupancy trigger
SecurityAlertCase       episodic    — motion/contact + time trigger
HolidayHomeModeCase     persistent  — linked to LifeDomain TRAVEL case lifecycle
ConnectivityAlertCase   episodic    — ProviderStatusEvent DISCONNECTED trigger
EnergyOptimizationCase  persistent  — optimization window, OptaPlanner worker
```

`HolidayHomeModeCase` opens when a casehub-life `HolidayTrip` case opens, closes when it closes — case-to-case coordination within the same application via `CaseLifecycleEvent` CDI observer. No cross-app bridge.

---

## Community Automations

### Packaging as Quarkus extensions

A `casehub-automation-morning-routine` JAR contains:

```
META-INF/
  quarkus-extension.properties
resources/
  automations/morning-routine/
    triggers.yaml          YAML trigger conditions
    plan.yaml              CasePlanModel (Serverless Workflow YAML)
    rules.drl              Drools rules for in-case decisions
    constraints.java       OptaPlanner constraints (optional)
    agent.yaml             OpenClaw agent descriptor (optional)
```

Classpath activation: adding the JAR registers all triggers and plan models automatically. No code changes to the host casehub-life application.

### Named qualifier device binding

Automations declare roles, not device IDs:

```properties
# application.properties — deployment-specific
casehub.automation.morning-routine.morning-motion-sensor=sensor.hallway_motion
casehub.automation.morning-routine.main-light=light.living_room_main
```

`YamlTriggerRegistry` resolves `@morning-motion-sensor` to the configured device ID at evaluation time. Automations are portable across installations.

### Cross-platform guarantee

Automations depending only on `casehub-iot-api` types run identically on HA and OpenHAB. Automations importing HA or OpenHAB supplement types are platform-specific and must declare this.

---

## Workers

### Drools

Two roles:

1. **`DroolsTriggerSession`** — evaluates complex multi-condition triggers at the observer level before case opening.
2. **In-case decision rules** — once a case is open, Drools evaluates device state + case context to determine next actions (which device to command, whether to escalate, when to close).

Community automations bundle `.drl` files. The Drools session receives `StateChangeEvent`s and fires `TriggerFired` / `DeviceCommandNeeded` facts.

### Quarkus Flow

Multi-step sequenced automations where order and timing matter. A `MorningRoutineFlow` sequences: turn on hallway light → wait 2 minutes → adjust thermostat → start coffee maker, dispatching `DeviceCommand`s at each step via `CasehubFlow` helper.

### OptaPlanner

`EnergyOptimizationCase` runs OptaPlanner as the worker. Constraints operate over `PowerSensor`, `SwitchDevice`, and `ThermostatDevice` states. Solver input: current device states, electricity tariff schedule, solar forecast, car departure times, occupancy patterns. Output: time-stamped sequence of `DeviceCommand`s dispatched via the provider.

Replanning triggered by `StateChangeEvent` where `changedCapabilities` intersects with fields relevant to active constraints (occupancy change, tariff update, solar reading).

### OpenClaw (Layer 7 pattern — no new integration)

Home automation AI agents follow the same pattern as all casehub-life OpenClaw workers:

- Provisioned via `ClaudonyReactiveWorkerProvisioner`
- Context: device state snapshot + case history + `CaseMemoryStore` prior context (occupancy patterns, past case outcomes)
- Channels: observe (status + explanations), oversight (HITL for high-stakes commands)
- Trust-scored via casehub-ledger — agents with better track records receive higher-stakes decisions

"Ask your house" — QUERY speech act on the observe channel, agent responds with explanation backed by the ledger entry recording the trigger. No new OpenClaw integration beyond Layer 7.

---

## Tutorial Layers

| Sub-layer | Adds | Gap it closes |
|---|---|---|
| 9a | `casehub-iot-api` + one provider, `DeviceRegistry` | No typed device abstraction; raw platform API calls |
| 9b | `HomeAutomationEventObserver` + YAML trigger rules | No formal case opening from device events |
| 9c | `DeviceCommand` dispatch + authorization | No structured command authorization or audit trail |
| 9d | Community automation packaging (Quarkus extension) | No portable cross-platform automation format |
| 9e | `EnergyOptimizationCase` with OptaPlanner | Rule-based scheduling vs constraint optimization |

---

## Testing

```java
@QuarkusTest
class MorningRoutineTriggerIT {

    @Inject StateChangeEventPublisher publisher;   // from casehub-iot-testing
    @Inject CaseInstanceRepository caseInstances;

    @Test
    void motionAtSevenAm_opensMorningRoutineCase() {
        publisher.publish(
            presenceSensor("hallway").notPresent(),
            presenceSensor("hallway").present()
        );
        await().until(() ->
            caseInstances.findOpenByType("morning-routine").isPresent()
        );
    }
}
```

Add `casehub-iot-testing` in test scope alongside the chosen platform provider.
