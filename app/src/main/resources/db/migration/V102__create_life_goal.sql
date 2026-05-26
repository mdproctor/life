CREATE TABLE life_goal (
    id          UUID         NOT NULL,
    domain      VARCHAR(50)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    target_date TIMESTAMP,
    status      VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
