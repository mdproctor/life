CREATE TABLE legal_action_ledger_entry (
    id               UUID         NOT NULL,
    work_item_id     UUID         NOT NULL,
    legal_obligation VARCHAR(255) NOT NULL,
    filing_deadline  TIMESTAMP WITH TIME ZONE NOT NULL,
    jurisdiction     VARCHAR(100),
    event_type       VARCHAR(30)  NOT NULL,
    action_taken     VARCHAR(255),
    CONSTRAINT pk_legal_action_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_legal_action_base FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
