ALTER TABLE life_case_tracker ADD COLUMN domain VARCHAR(32);

UPDATE life_case_tracker SET domain = 'TRAVEL' WHERE case_type = 'travel-plan';
UPDATE life_case_tracker SET domain = 'HOUSEHOLD' WHERE case_type = 'home-maintenance';
UPDATE life_case_tracker SET domain = 'ELDER_CARE' WHERE case_type = 'care-coordination';
UPDATE life_case_tracker SET domain = 'HEALTH' WHERE case_type = 'appointment-cycle';
UPDATE life_case_tracker SET domain = 'CONTRACTOR_COORDINATION' WHERE case_type = 'contractor-coordination';
UPDATE life_case_tracker SET domain = 'FINANCE' WHERE case_type = 'financial-review';

ALTER TABLE life_case_tracker ALTER COLUMN domain SET NOT NULL;
