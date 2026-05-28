# 0001 — Life domain model: foundation primitives over wrapper entities

Date: 2026-05-27
Status: Accepted

## Context and Problem Statement

Layer 1 introduced `HouseholdTask`, `LifeGoal`, and `LifeEvent` as JPA entities. Brainstorming for Layer 2 revealed that these entities were thin wrappers over foundation primitives (`WorkItem`, `CaseInstance`, `LedgerEntry`) — duplicating fields that already live in the foundation and adding no domain-specific behaviour. At Layer 5, the engine's `CasePlanModel` would become the coordination record, making these wrappers redundant at that point.

## Decision Drivers

* Domain entities that duplicate foundation primitives become redundant as layers progress — the AGENTIC-HARNESS-GUIDE prohibits temporary scaffolding ("code that exists only for the tutorial is wrong code")
* `HouseholdTask` had no fields that weren't either already in `WorkItem` or expressible through template configuration
* `LifeGoal` maps cleanly to a `CaseInstance` with domain data in context; `targetDate` = `caseBudgetDeadline`
* `LifeEvent` maps to a completed case outcome recorded in `CaseLedgerEntry`
* AML and devtown have no domain wrapper entities — their domain concepts ARE cases and WorkItems

## Considered Options

* **Option A** — Keep wrapper entities, add `workItemId` FK to link them to foundation primitives
* **Option B** — Remove wrappers entirely; use foundation primitives + a thin `LifeTaskContext` supplement for domain-only fields
* **Option C** — Rename wrappers to be less misleading (`LifeTask` instead of `HouseholdTask`)

## Decision Outcome

Chosen option: **Option B**, because the wrapper entities would become redundant at Layer 5 when `CasePlanModel` orchestration takes over, the supplement pattern correctly separates domain context from foundation primitives, and no data is lost — all fields in the removed entities map directly to foundation fields or the supplement.

### Positive Consequences

* Domain model stays clean as layers advance — no redundant entities to maintain alongside foundation records
* `LifeTaskContext` carries only fields the foundation cannot represent (`domain`, `externalActorId`, `recurrence`)
* WorkItemTemplate seed data expresses the domain vocabulary without entity overhead
* Consistent with AML and devtown — neither has wrapper entities around their coordination records

### Negative Consequences / Tradeoffs

* Layer 1 codebase required a significant rewrite at the start of Layer 2 (ShowcaseScenarioTest, ExternalActorService, resource layer)
* `recurrence` field has no direct foundation equivalent yet — deferred to a planned `WorkItemTemplate.cronExpression` enhancement in casehub-work

## Pros and Cons of the Options

### Option A — Wrapper entities with workItemId FK

* ✅ Layer 1 ShowcaseScenarioTest survives with minimal changes
* ✅ listTasks() endpoint continues to work via the wrapper entity
* ❌ Wrapper becomes redundant at Layer 5 — two records tracking the same concept
* ❌ Violates AGENTIC-HARNESS-GUIDE principle against temporary scaffolding

### Option B — Foundation primitives + LifeTaskContext supplement

* ✅ No redundant entities; foundation primitive IS the coordination record from Layer 2 onwards
* ✅ LifeTaskContext carries only what has no foundation home (domain, externalActorId, recurrence)
* ✅ Consistent with AML/devtown pattern
* ❌ Layer 1 code required a rewrite at Layer 2 boundary
* ❌ `recurrence` still has no foundation equivalent — captured in supplement, pending casehub-work enhancement

### Option C — Rename wrappers

* ✅ Low disruption at Layer 2
* ❌ Renaming doesn't fix the duplication — a `LifeTask` entity is still a wrapped `WorkItem`

## Links

* [docs/specs/2026-05-27-layer2-casehub-work-sla.md](../specs/2026-05-27-layer2-casehub-work-sla.md) — spec covering the full redesign
* [casehubio/parent#79](https://github.com/casehubio/parent/issues/79) — AGENTIC-HARNESS-GUIDE domain entity discipline update
* [PP-20260527-da1f66](../../wksp/docs/protocols/casehub/domain-supplement-pattern.md) — supplement pattern protocol
