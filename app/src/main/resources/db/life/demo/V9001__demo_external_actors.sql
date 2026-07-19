-- Demo ExternalActors for Household Hub standalone mode.
-- These represent the household's regular service contacts.
INSERT INTO external_actor (id, name, actor_type, contact_method, contact_value, created_at)
VALUES
  ('a0000000-0000-0000-0000-000000000001', 'Dave Wilson Plumbing', 'CONTRACTOR', 'PHONE', '+44 7700 900001', NOW()),
  ('a0000000-0000-0000-0000-000000000002', 'Dr Sarah Chen', 'DOCTOR', 'EMAIL', 'dr.chen@nhs.example.uk', NOW()),
  ('a0000000-0000-0000-0000-000000000003', 'Harper & Associates', 'INSTITUTION', 'EMAIL', 'enquiries@harper-law.example.uk', NOW()),
  ('a0000000-0000-0000-0000-000000000004', 'Oakwood Primary School', 'INSTITUTION', 'EMAIL', 'office@oakwood.example.uk', NOW()),
  ('a0000000-0000-0000-0000-000000000005', 'Maria Santos — Carer', 'SERVICE_PROVIDER', 'PHONE', '+44 7700 900005', NOW());
