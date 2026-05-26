-- external_actor_id is a logical reference — no FK constraint.
-- Service layer guards deletion of ExternalActor when tasks reference it.
CREATE TABLE household_task (
    id                UUID         NOT NULL,
    domain            VARCHAR(50)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    deadline          TIMESTAMP,
    sla_hours         INTEGER,
    status            VARCHAR(50)  NOT NULL,
    assigned_to       VARCHAR(255),
    external_actor_id UUID,
    recurrence        VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
