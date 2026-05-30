CREATE TABLE financial_decision_ledger_entry (
    id                UUID          NOT NULL,
    work_item_id      UUID,
    oversight_ref     UUID          NOT NULL,
    amount_threshold  NUMERIC(15,2) NOT NULL,
    purchase_category VARCHAR(100)  NOT NULL,
    approved_by       VARCHAR(255),
    event_type        VARCHAR(30)   NOT NULL,
    CONSTRAINT pk_financial_decision_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_financial_decision_base FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
