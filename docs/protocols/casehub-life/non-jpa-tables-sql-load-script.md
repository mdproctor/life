---
id: PP-20260609-e2c3a1
title: "Non-JPA tables required at test time must be created via sql-load-script"
type: rule
scope: repo
applies_to: "app/src/test — any named or default PU that needs a plain SQL table not mapped by a JPA entity"
severity: important
refs:
  - app/src/test/resources/application.properties
  - app/src/test/resources/import-qhorus.sql
violation_hint: "Test fails with 'Table not found' on a table that exists in production but has no JPA entity — no Flyway migration ran in the test environment"
garden_ref: GE-20260609-ef7dbe
created: 2026-06-09
---

When a dependency (casehub-ledger, casehub-work, etc.) creates a plain SQL table via Flyway that has no JPA entity mapping (e.g. `ledger_subject_sequence`), Hibernate `drop-and-create` will not create it. Since Flyway is disabled in tests (`migrate-at-start=false`), the table is silently absent. Use `quarkus.hibernate-orm."<pu-name>".sql-load-script` pointing to a small SQL file that creates the missing table with `CREATE TABLE IF NOT EXISTS`. Place the script in `app/src/test/resources/` and reference it in the test `application.properties`. The `IF NOT EXISTS` guard makes it idempotent across test runs.
