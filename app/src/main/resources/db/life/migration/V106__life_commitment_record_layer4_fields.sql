ALTER TABLE life_commitment_record
    ADD COLUMN approved_by       VARCHAR(255),
    ADD COLUMN amount_threshold  NUMERIC(15, 2),
    ADD COLUMN purchase_category VARCHAR(100);
