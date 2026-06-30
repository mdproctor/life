---
id: PP-20260618-openclaw-agent
title: "AgentWorkerFunction + LifeOpenClawChatModelFactory pattern for LLM-backed life workers"
type: rule
scope: application
applies_to: "casehub-life — any app/ code declaring workers backed by an LLM or OpenClaw"
severity: important
refs:
  - casehubio/life#25
  - casehubio/life#37
  - casehubio/life#38
  - casehubio/life#46
  - casehubio/openclaw#49
  - casehubio/engine#463
  - casehubio/engine#543
  - docs/specs/life-actor-model.md
  - app/src/main/java/io/casehub/life/app/engine/AppointmentCycleCaseHub.java
violation_hint: >
  An LLM-backed worker uses Worker.builder().function(lambda) instead of
  Worker.builder().function(new AgentWorkerFunction(Agent.builder()...build())),
  OR agentId is absent, OR agentId does not follow {model-family}:{persona}@{major},
  OR AgentDescriptor is not registered on CaseDefinition
created: 2026-06-18
updated: 2026-06-30
---

**Agent worker construction (LLM-backed workers):** Use `LifeTypedCaseHub.agentWorker()` helper
in `configureCase()` override to build LLM-backed workers. The helper constructs a complete
`Worker` with `new AgentWorkerFunction(agent)` where the `Agent` is built with
`Agent.builder().model(openClawFactory.forAgent(AGENT))`, producing a `ChatModelProvider`
backed by `OpenClawAgentProvider` → `DirectCallBridge` → `/hooks/agent` (webhook delivery,
virtual thread blocking). Example:
```java
protected void configureCase(CaseDefinition definition) {
  definition.getWorkers().addAll(List.of(
    agentWorker("research", "Perform parallel research...", ResearchResult.class),
    agentWorker("analyse", "Analyse gathered data...", AnalysisResult.class)
  ));
}
```

The `agentWorker(String capabilityName, String systemPrompt, Class<?> responseSchema)` helper
is defined in `LifeTypedCaseHub` and returns a fully-constructed `Worker` with the capability
name, system prompt, and response schema already configured.

**Stub worker construction (in-process workers):** Use `Worker.builder().function(lambda)`
(resolved to `WorkerFunction.Sync` by the builder) only for in-process stub workers.
**Never cast a lambda to `(WorkerFunction)` — the cast bypasses the builder's `function(Function)`
overload which wraps in `WorkerFunction.Sync`. The engine's `SyncAgentWorkerFunctionHandler.supports()`
requires `WorkerFunction.Sync` or `AgentWorkerFunction`; a raw lambda cast to `WorkerFunction` is unrecognised.**

**Capability naming:** use `Worker.capabilityName(String)` to set the capability on a worker.
Example: `agentWorker("name", AGENT).capabilityName("health-coordination")`. The capability
name routes the worker to a trust-weighted agent via `TrustRoutingPolicyProvider` and provides
the target for sentinel provisioning (Layer 7).

**AgentDescriptor is registered on CaseDefinition (not Worker)** per engine#543.
`LifeTypedCaseHub.augment()` handles descriptor registration automatically after
`configureCase()` returns. Subclasses should NOT call `setAgentDescriptors()` manually —
it is already done by the base class:

```java
// In LifeTypedCaseHub:
@Override
protected final void augment(CaseDefinition definition) {
    configureCase(definition);
    definition.setAgentDescriptors(Map.of(
            agent.agentId(), descriptorFactory.descriptorFor(agent)));
}
```

`LifeAgentDescriptorFactory` (CDI bean, `app.engine.agent`) owns config→descriptor
construction. `LifeAgent` enum (`app.engine`) defines the 4 agent identity constants.
`CaseDefinition.agentDescriptorFor(agentId)` returns `Optional<AgentDescriptor>`.

**Agent identity format:** `{model-family}:{persona}@{major}` per docs/specs/life-actor-model.md.
Named life personas: `home-agent`, `health-agent`, `finance-agent`, `travel-agent`.
Example: `"openclaw:health-agent@1"`.

**Factory pattern:** `LifeOpenClawChatModelFactory.forAgent(LifeAgent)` creates a
per-agent `ChatModelProvider`. Each worker gets its own `Agent` with its own system prompt
and response schema, all routed through the same OpenClaw agent. The factory injects
`DirectCallBridge` and `OpenClawHookClient` from `casehub-openclaw-casehub`/`casehub-openclaw-core`.

**Response schema is required:** use `AgentBuilder.responseSchema(Record.class)` with a typed
record. `OpenClawChatModel.doChat()` auto-extracts the `JsonSchema` from the ChatRequest's
`ResponseFormat` and serialises it into the message sent to `/hooks/agent`, enforcing
structured output at the prompt level.

**System prompt forwarding:** the CaseHub system prompt is prepended to the user message in
`OpenClawChatModel.doChat()`. OpenClaw agents have their own system prompts configured
server-side (persona-level). CaseHub's system prompt is task-level (what to do, output format).
They are complementary.

**Template method contract:** `LifeTypedCaseHub` subclasses override `configureCase(CaseDefinition definition)`
to customize the case definition loaded from YAML. This hook is called once during initialization
and must be idempotent. Use `definition.getWorkers().addAll()` and other mutation methods within
this override. Do NOT call `setAgentDescriptors()` — the base class handles descriptor registration
automatically after `configureCase()` returns. Do NOT cache state or call `getDefinition()` — the
template method owns the final definition contract.

**Exception case — manual Agent construction:** if a worker requires a `userMessage` parameter
or other customization not covered by `agentWorker()`, construct the `Agent` manually:
```java
protected void configureCase(CaseDefinition definition) {
  Agent customAgent = Agent.builder()
    .model(openClawFactory.forAgent(agent()))
    .systemPrompt("custom prompt...")
    .userMessage("Custom user message template...")
    .responseSchema(CustomResponseRecord.class)
    .build();
  
  Worker customWorker = Worker.builder()
    .name("custom-agent")
    .capabilityName("custom-capability")
    .function(new AgentWorkerFunction(customAgent))
    .build();
  
  definition.getWorkers().addAll(List.of(customWorker));
}
```
The factory still owns model creation; manual construction is only needed when
`agentWorker()` cannot express the required configuration (e.g., `userMessage` parameter).

**Configuration:** standard `casehub-openclaw` config keys (`OpenClawClientConfig`):
- `casehub.openclaw.gateway.url` — OpenClaw gateway base URL
- `casehub.openclaw.gateway.bearer-token` — API key
- `casehub.openclaw.delivery.base-url` — webhook callback base URL
- `casehub.openclaw.agent.default-timeout-seconds` — default 120

**Config changes require restart:** `forAgent()` creates the ChatModel once during
`configureCase()` initialization (double-checked lock, once per JVM lifetime).

**CDI exclusions:** `casehub-openclaw-casehub` brings several `@ApplicationScoped` beans
designed for heartbeat/provisioner mode (life#37). Life excludes them via
`quarkus.arc.exclude-types` and keeps only `DirectCallBridge` and
`DirectCallDeliveryResource` for direct-call mode.

**Cross-repo consistency:** when casehub-openclaw-casehub WorkerProvisioner is wired (life#37),
the casehub.openclaw.agents map key for the health agent MUST be the full agentId
`"openclaw:health-agent@1"`. OpenClawWorkerProvisioner.resolveAgentId() returns the config
map key — it must match the AgentDescriptor.agentId stored in the trust system.

**PP-20260531 superseded for LLM-backed workers:** the original requirement "FuncWorkflowBuilder
for all workers" (engine#463 pre-decision) is superseded. After engine#463:
- Single LLM call → AgentWorkerFunction (this protocol)
- Multi-step durable → FuncWorkflowBuilder or YAML workflow
- Stub / in-process → Sync lambda via `function(Function)` overload

**Provisioner mode (life#37):** Sentinel capabilities use the provisioner path — no inline
worker exists, so the engine falls through to `LifeReactiveWorkerProvisioner`. The provisioner
registers the sentinel in `LifeSentinelRegistry` and schedules a Quartz `LifeHeartbeatJob`.
Each heartbeat tick: `CaseHubRuntime.query()` for fresh case context, `Agent.execute()` via
DirectCallBridge for structured result, `CaseHubRuntime.signal()` to deliver the result.
`LifeProvisionerCleanupObserver` terminates sentinels on `CaseLifecycleEvent` terminal states.
Sentinel capabilities must NEVER have inline workers registered — they are reserved for the
provisioner path. Uses life's own `LifeSentinelRegistry` (not `OpenClawAgentRegistry`).
