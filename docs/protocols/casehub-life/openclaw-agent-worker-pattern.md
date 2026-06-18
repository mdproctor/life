---
id: PP-20260618-openclaw-agent
title: "WorkerFunction.AgentExec(Agent) pattern for LLM-backed life workers"
type: rule
scope: application
applies_to: "casehub-life — any app/ code declaring workers backed by an LLM or OpenClaw"
severity: important
refs:
  - casehubio/life#25
  - casehubio/engine#463
  - docs/specs/life-actor-model.md
  - app/src/main/java/io/casehub/life/app/engine/AppointmentCycleCaseHub.java
violation_hint: >
  An LLM-backed worker uses Worker.builder().function(lambda) instead of
  Worker.builder().function(Agent.builder()...build()), OR agentId is absent,
  OR agentId does not follow {model-family}:{persona}@{major}
created: 2026-06-18
---

Use `Worker.builder().function(Agent.builder().model(provider)...build())` (WorkerFunction.AgentExec)
for workers that call an LLM or OpenClaw's /v1/chat/completions endpoint. Use
`Worker.builder().function(lambda)` (WorkerFunction.Sync) only for in-process stub workers.

**Worker.agentDescriptor() is architecturally required (not build-time enforced)** on every
LLM-backed worker. Worker.Builder.build() does not validate the field — it is silently nullable.
Omitting it produces a worker with null descriptor, which means: (1) trust routing has no identity
to score against, (2) the Layer 6 attestation pipeline (LifeOutcomeAttestationWriter) cannot
attribute outcomes to the correct agent.

**Agent identity format:** `{model-family}:{persona}@{major}` per docs/specs/life-actor-model.md.
Named life personas: `home-agent`, `health-agent`, `finance-agent`, `travel-agent`, `care-agent`.
Example: `"openclaw:health-agent@1"`.

**Cross-repo consistency:** when casehub-openclaw-casehub WorkerProvisioner is wired (life#37),
the casehub.openclaw.agents map key for the health agent MUST be the full agentId
`"openclaw:health-agent@1"` (Quarkus escaping: `casehub.openclaw.agents.openclaw\:health-agent@1`).
OpenClawWorkerProvisioner.resolveAgentId() returns the config map key — it must match the
AgentDescriptor.agentId stored in the trust system.

**responseSchema is required:** use AgentBuilder.responseSchema(Record.class) with a typed record.
This enforces structured output and prevents hallucinated field names.

**Runtime dependency on OpenClaw:** failure of LifeOpenClawChatModelProvider.get() (at augment()
time, not at startup) defers silently to the first agent.execute() invocation. Deploy
OpenClawHealthProbe (@IfBuildProfile("prod")) on every deployment that registers LLM-backed workers.

**Config changes require restart:** chatModelProvider.get() is called once in Agent.build() during
augment(), which runs on first getDefinition() access (double-checked lock). Changes to
casehub.life.openclaw.api-url or casehub.life.openclaw.timeout-seconds have no effect until restart.

**PP-20260531 superseded for LLM-backed workers:** the original requirement "FuncWorkflowBuilder
for all workers" (engine#463 pre-decision) is superseded. After engine#463:
- Single LLM call → AgentExec (this protocol)
- Multi-step durable → FuncWorkflowBuilder or YAML workflow (WorkerFunction.Flow)
- Stub / in-process → Sync lambda (WorkerFunction.Sync)
