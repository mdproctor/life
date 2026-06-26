---
id: PP-20260618-openclaw-agent
title: "AgentWorkerFunction + LifeOpenClawChatModelFactory pattern for LLM-backed life workers"
type: rule
scope: application
applies_to: "casehub-life — any app/ code declaring workers backed by an LLM or OpenClaw"
severity: important
refs:
  - casehubio/life#25
  - casehubio/life#38
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
updated: 2026-06-26
---

Use `Worker.builder().function(new AgentWorkerFunction(agent))` for workers that call
OpenClaw's `/hooks/agent` endpoint via the direct-call bridge. The `Agent` is built with
`Agent.builder().model(openClawFactory.forAgent("<openClawAgentId>"))` where the factory
produces a `ChatModelProvider` backed by `OpenClawAgentProvider` → `DirectCallBridge` →
`/hooks/agent` (webhook delivery, virtual thread blocking).

Use `Worker.builder().function(lambda)` (resolved to `WorkerFunction.Sync` by the builder)
only for in-process stub workers. **Never cast a lambda to `(WorkerFunction)` — the cast
bypasses the builder's `function(Function)` overload which wraps in `WorkerFunction.Sync`.
The engine's `SyncAgentWorkerFunctionHandler.supports()` requires `WorkerFunction.Sync`
or `AgentWorkerFunction`; a raw lambda cast to `WorkerFunction` is unrecognised.**

**AgentDescriptor is registered on CaseDefinition (not Worker)** per engine#543.
In `augment()`, after adding workers:
```java
yaml.setAgentDescriptors(Map.of("openclaw:health-agent@1", healthDescriptor()));
```
`CaseDefinition.agentDescriptorFor(agentId)` returns `Optional<AgentDescriptor>`.

**Agent identity format:** `{model-family}:{persona}@{major}` per docs/specs/life-actor-model.md.
Named life personas: `home-agent`, `health-agent`, `finance-agent`, `travel-agent`.
Example: `"openclaw:health-agent@1"`.

**Factory pattern:** `LifeOpenClawChatModelFactory.forAgent("<openClawAgentId>")` creates a
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

**Configuration:** standard `casehub-openclaw` config keys (`OpenClawClientConfig`):
- `casehub.openclaw.gateway.url` — OpenClaw gateway base URL
- `casehub.openclaw.gateway.bearer-token` — API key
- `casehub.openclaw.delivery.base-url` — webhook callback base URL
- `casehub.openclaw.agent.default-timeout-seconds` — default 120

**Config changes require restart:** `forAgent()` creates the ChatModel once during
`augment()` (double-checked lock, once per JVM lifetime).

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
