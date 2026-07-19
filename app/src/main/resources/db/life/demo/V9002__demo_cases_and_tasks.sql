-- Demo LifeCaseTracker records for Household Hub standalone mode.
-- Static records — no engine dependency. Demonstrates case lifecycle visibility.
INSERT INTO life_case_tracker (id, case_type, domain, status, created_at, completed_at)
VALUES
  ('c0000000-0000-0000-0000-000000000001', 'contractor-coordination', 'CONTRACTOR_COORDINATION', 'ACTIVE', NOW() - INTERVAL '3' DAY, NULL),
  ('c0000000-0000-0000-0000-000000000002', 'care-coordination', 'ELDER_CARE', 'ACTIVE', NOW() - INTERVAL '7' DAY, NULL),
  ('c0000000-0000-0000-0000-000000000003', 'travel-plan', 'TRAVEL', 'COMPLETED', NOW() - INTERVAL '14' DAY, NOW() - INTERVAL '2' DAY),
  ('c0000000-0000-0000-0000-000000000004', 'appointment-cycle', 'HEALTH', 'ACTIVE', NOW() - INTERVAL '1' DAY, NULL),
  ('c0000000-0000-0000-0000-000000000005', 'financial-review', 'FINANCE', 'ACTIVE', NOW() - INTERVAL '5' DAY, NULL);
