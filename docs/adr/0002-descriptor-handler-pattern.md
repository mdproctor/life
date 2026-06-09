# 0002 — Business Logic Organization: Descriptor+Handler over Scattered Switches

Date: 2026-06-09
Status: Accepted

## Context and Problem Statement

After Layer 7 partial implementation, casehub-life's domain-specific business logic
was scattered across 11 locations in 6 service and observer classes: static maps,
switch statements, and hardcoded conditionals keyed on `LifeDomain`, `HouseholdActionType`,
and `LifeCaseType`. Adding a new domain required touching 4–6 files with no compiler
enforcement between them; a missed update silently degraded behaviour.

## Decision Drivers

* Compiler-enforced cohesion: all knowledge about a domain/type in one place
* Adding a new domain or action type should require exactly one new class
* Domain descriptors must be testable without CDI or a database
* The existing `LifeCommitmentStrategy` pattern (SPI + CDI discovery) had already proven the approach

## Considered Options

* **Option A** — Status quo: switch statements and static maps in service/observer classes
* **Option B** — POJO descriptors + CDI handler supplements (chosen)
* **Option C** — CDI strategy beans per concern only (no descriptor layer)

## Decision Outcome

Chosen option: **Option B — POJO descriptors + CDI handler supplements**, because it
separates declarative knowledge (what a domain IS) from behavioural execution (what
HAPPENS when an event occurs), enables pure-Java unit testing of domain facts without
any framework startup, and the `LifeCommitmentStrategy` precedent demonstrated the CDI
discovery pattern already works at scale in this codebase.

### Positive Consequences

* Adding a new `LifeDomain` value = one new `LifeDomainDescriptor` class; zero changes to service classes
* Descriptors are pure Java records/classes testable with `new HealthDomainDescriptor()` and plain JUnit
* Handler absence is an explicit, graceful no-op (not a silent switch default)
* `LifeDomain.fromCategory()` and `LifeDomain.descriptor()` become the single lookup API, replacing three correlated static maps
* `LifeCommitmentStrategy`, `DomainLedgerHandler`, `HouseholdRiskRule`, and `LifeTypedCaseHub` form a consistent family

### Negative Consequences / Tradeoffs

* More files: each domain/type gets its own class (8 domain descriptors, 11 risk rules, 3 ledger handlers)
* CDI `@Any Instance<T>` injection is unfamiliar to developers used to direct field injection
* `FinanceDomainLedgerHandler` requires two `writeEntry` overloads due to the Finance domain's commitment-based write path, making the interface slightly asymmetric

## Pros and Cons of the Options

### Option A — Status quo (switch statements + static maps)

* ✅ Fewer files
* ❌ Adding a domain requires N-file edits with no compiler enforcement
* ❌ `DOMAIN_TO_CAPABILITY`, `CAPABILITY_TO_DOMAIN`, `POLICIES` maps are correlated but unrelated code — drift is silent
* ❌ Service classes carry domain knowledge they should not own

### Option B — POJO descriptors + CDI handler supplements

* ✅ One class per domain/type, all knowledge co-located
* ✅ Descriptors testable in pure Java
* ✅ Graceful no-op for missing handlers (no handler = no write)
* ✅ Consistent with existing `LifeCommitmentStrategy` CDI pattern
* ❌ More files per domain
* ❌ Finance domain requires two-overload handler interface

### Option C — CDI strategy beans per concern only

* ✅ Fewer abstraction layers than Option B
* ❌ No descriptor layer means declarative knowledge (routing policy, SLA deadline, capability tag) is still scattered
* ❌ Strategies have framework dependencies (CDI, repositories) even for pure-knowledge queries

## Links

* Spec: `docs/specs/2026-06-08-business-logic-centralization.md`
* Protocol: `docs/protocols/casehub-life/descriptor-handler-no-domain-switches.md` (PP-20260609-bd9d27)
* Platform coherence issue: casehubio/parent#202 (universal descriptor+handler protocol)
* ADR-0001: Layer 2 decision to remove duplicate foundation entities — motivates keeping domain logic in application layer
