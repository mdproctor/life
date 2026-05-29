-- Layer 3: life_commitment_record — qhorus commitment supplement for casehub-life.
-- Tracks DELEGATION, CONTRACTOR, and OVERSIGHT commitment lifecycle alongside native
-- qhorus Commitment records (linked by correlationId).
-- For OVERSIGHT gates, workItemId is null until household-admin RESPONSE fulfills the gate.

CREATE TABLE life_commitment_record (
    id                UUID                     NOT NULL,
    correlation_id    VARCHAR(255)             NOT NULL,
    mode              VARCHAR(32)              NOT NULL,
    status            VARCHAR(32)              NOT NULL,
    work_item_id      UUID,
    external_actor_id UUID,
    delegate_to       VARCHAR(255),
    channel_id        VARCHAR(255)             NOT NULL,
    deadline          TIMESTAMP WITH TIME ZONE,
    pending_task_json TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_life_commitment_record PRIMARY KEY (id),
    CONSTRAINT uq_life_commitment_correlation UNIQUE (correlation_id)
);

CREATE INDEX idx_life_commitment_work_item   ON life_commitment_record (work_item_id);
CREATE INDEX idx_life_commitment_correlation ON life_commitment_record (correlation_id);
CREATE INDEX idx_life_commitment_channel_status
    ON life_commitment_record (channel_id, status);
-- Partial unique index: prevents duplicate pending oversight gates with the same dedup key.
-- delegate_to is repurposed as a title:templateRef dedup key for OVERSIGHT mode.
-- This closes the race condition in the application-level duplicate check.
CREATE UNIQUE INDEX uq_oversight_pending_key
    ON life_commitment_record (delegate_to)
    WHERE mode = 'OVERSIGHT' AND status = 'PENDING_RESPONSE';
