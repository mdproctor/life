-- Layer 3: life-escalation WorkItemTemplate.
-- Used by LifeWatchdogAlertObserver when a commitment deadline passes without response.
-- Routes to household-admin for manual resolution.

INSERT INTO work_item_template
    (id, name, description, category, priority, candidate_groups,
     default_expiry_hours, created_by, created_at)
VALUES
    (gen_random_uuid(),
     'life-escalation',
     'Commitment deadline passed — manual action required by household-admin',
     'household', 'HIGH', 'household-admin',
     24, 'life-system', now());
