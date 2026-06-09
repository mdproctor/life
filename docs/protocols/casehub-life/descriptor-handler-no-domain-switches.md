---
id: PP-20260609-bd9d27
title: "Descriptor+handler pattern: no domain switches in casehub-life service classes"
type: rule
scope: application
applies_to: "All app/ service, observer, and SPI classes in casehub-life"
severity: important
refs:
  - docs/specs/2026-06-08-business-logic-centralization.md
violation_hint: "A switch or if/else chain keyed on LifeDomain, HouseholdActionType, or LifeCaseType appearing in any service or observer class body"
created: 2026-06-09
---

All domain-specific behaviour in casehub-life must live in `LifeDomainDescriptor` implementations (attached to `LifeDomain` enum values via `descriptor()`) or in CDI handler beans (`DomainLedgerHandler`, `HouseholdRiskRule`, `LifeCommitmentStrategy`, `LifeTypedCaseHub`) discovered via `@Inject @Any Instance<T>`. Service and observer classes (`LifeDecisionLedgerObserver`, `LifeTaskService`, `LifeSlaBreachPolicy`, `LifeTrustRoutingPolicyProvider`, `LifeActionRiskClassifier`, `LifeCaseService`) must contain no switch statements or static maps keyed on `LifeDomain`, `HouseholdActionType`, or `LifeCaseType` values. Adding a new domain or action type means one new descriptor or handler class — zero changes to service classes. See parent#202 for the platform-level coherence rule that this application-tier rule implements.
