CREATE TABLE external_actor_erasure_ledger_entry (
    id              UUID         NOT NULL,
    erased_actor_id UUID         NOT NULL,
    contact_method  VARCHAR(50)  NOT NULL,
    erased_by       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_external_actor_erasure_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_external_actor_erasure_base FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
