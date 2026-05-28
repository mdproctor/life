-- Seed life-domain WorkItemTemplates. These give foundation WorkItems their life-domain identity.
-- Runs after casehub-work V1-V31 (work_item_template table created at V5).
-- Flyway sorts by version number: work V1-V31 run before life V100-V102.
-- gen_random_uuid() available in H2 MODE=PostgreSQL and PostgreSQL.

INSERT INTO work_item_template
    (id, name, description, category, priority, candidate_groups,
     default_expiry_hours, created_by, created_at)
VALUES
    (gen_random_uuid(),
     'household-task',
     'Routine household coordination task',
     'household', 'MEDIUM', 'household-member',
     24, 'life-system', now()),
    (gen_random_uuid(),
     'health-appointment',
     'Health appointment or follow-up',
     'health', 'MEDIUM', 'household-member',
     48, 'life-system', now()),
    (gen_random_uuid(),
     'contractor-coordination',
     'Contractor task with commitment tracking',
     'contractor', 'MEDIUM', 'household-member',
     72, 'life-system', now());
