CREATE TABLE life_event (
    id          UUID         NOT NULL,
    domain      VARCHAR(50)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
