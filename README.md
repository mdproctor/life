# casehub-life

[![Build](https://github.com/casehubio/life/actions/workflows/publish.yml/badge.svg?branch=main)](https://github.com/casehubio/life/actions/workflows/publish.yml) [![Open PRs](https://img.shields.io/github/issues-pr/casehubio/life)](https://github.com/casehubio/life/pulls)

Personal life automation on the CaseHub agentic harness — household coordination,
health management, family obligations, and legal compliance with formal accountability.

## What it provides

A field showcase and tutorial application demonstrating that personal life automation
is structurally better served by a formal accountability layer than by best-effort
automation. The comparison baseline: OpenClaw alone.

| Domain | OpenClaw alone | casehub-life |
|---|---|---|
| Contractor follow-up | Agent says it will chase — silent if it doesn't | Commitment + Watchdog; automated SMS if no ETA |
| Health appointment | Reminder sent, then forgotten | WorkItem with SLA deadline; escalation to named GP |
| Major financial decision | Best-effort research, no approval gate | Oversight channel; human RESPONSE required before action |
| Legal deadline | Calendar reminder | WorkItem with hard deadline + ledger record |

## Tutorial Layers

| Layer | Adds | Gap it closes |
|---|---|---|
| 1 | Naive Java | Baseline: direct persistence, no accountability |
| 2 | casehub-work | No SLA enforcement on household tasks |
| 3 | casehub-qhorus | No commitment tracking; no oversight gates |
| 4 | casehub-ledger | No tamper-evident audit for health/financial decisions |
| 5 | casehub-engine | No multi-step workflow orchestration |
| 6 | Trust routing | No trust model for agent routing |
| 7 | casehub-openclaw | OpenClaw as execution layer with pre-built skill ecosystem |

## Documentation

- [Life automation spec](docs/specs/life-automation.md)
- [Actor model](docs/specs/life-actor-model.md)
- [Platform context](https://github.com/casehubio/parent/blob/main/docs/repos/casehub-life.md)
- [Research spec](https://github.com/casehubio/parent/blob/main/docs/specs/2026-05-25-openclaw-casehub-integration.md)

## Status

Scaffold only — Layer 1 pending. See [Epic 1](https://github.com/casehubio/life/issues/1).
