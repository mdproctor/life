---
id: PP-20260615-8ed738
title: "Exclude competing @Default CurrentPrincipal beans per context"
type: rule
scope: repo
applies_to: "casehub-life app/ — application.properties and test/resources/application.properties"
severity: critical
refs:
  - app/src/main/resources/application.properties
  - app/src/test/resources/application.properties
violation_hint: "27 'Ambiguous dependencies for type CurrentPrincipal' ARC deployment errors at quarkus:build"
created: 2026-06-15
---

When a foundation SNAPSHOT introduces a new `@Default CurrentPrincipal` bean (e.g. `QhorusInboundCurrentPrincipal`), add it to `quarkus.arc.exclude-types` in both configs — do not rely on `selected-alternatives` to resolve the ambiguity, as `DefaultTestPrincipal` is not `@Alternative`. Production excludes `QhorusInboundCurrentPrincipal` and `DefaultTestPrincipal` so `TenantScopedPrincipal` (@RequestScoped) wins. Tests exclude `QhorusInboundCurrentPrincipal` and `TenantScopedPrincipal` so `DefaultTestPrincipal` (@ApplicationScoped, fixed tenancyId `278776f9-e1b0-46fb-9032-8bddebdcf9ce`) wins.
