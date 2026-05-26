CREATE TABLE external_actor (
    id             UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    actor_type     VARCHAR(50)  NOT NULL,
    contact_method VARCHAR(50)  NOT NULL,
    contact_value  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
