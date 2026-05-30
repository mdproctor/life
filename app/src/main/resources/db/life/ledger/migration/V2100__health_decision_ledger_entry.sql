CREATE TABLE health_decision_ledger_entry (
    id               UUID         NOT NULL,
    work_item_id     UUID         NOT NULL,
    provider_id      UUID,
    appointment_date TIMESTAMP WITH TIME ZONE,
    task_category    VARCHAR(100) NOT NULL,
    sla_deadline     TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    outcome          VARCHAR(255),
    CONSTRAINT pk_health_decision_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_health_decision_base FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
