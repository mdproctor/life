CREATE TABLE IF NOT EXISTS ledger_subject_sequence (
    subject_id UUID         NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    next_seq   INT          NOT NULL DEFAULT 1,
    CONSTRAINT pk_ledger_subject_sequence PRIMARY KEY (subject_id, tenancy_id)
);
