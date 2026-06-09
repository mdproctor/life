# casehub-life Protocols

Rules specific to the casehub-life application repo.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [platform-config-yaml-registration.md](platform-config-yaml-registration.md) | Register new YAML files in both main and test application.properties | Any new casehub/life/*.yaml config file |
| [descriptor-handler-no-domain-switches.md](descriptor-handler-no-domain-switches.md) | No switch/if-else on LifeDomain, HouseholdActionType, or LifeCaseType in service/observer classes — use descriptor or handler | All app/ service and observer classes |
| [non-jpa-tables-sql-load-script.md](non-jpa-tables-sql-load-script.md) | Non-JPA tables required at test time must be created via sql-load-script | app/src/test — named or default PU needing a plain SQL table with no JPA entity |
