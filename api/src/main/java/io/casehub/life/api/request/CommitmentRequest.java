package io.casehub.life.api.request;

import java.time.Instant;
import java.util.UUID;

/**
 * Request for POST /life-tasks/{id}/commit.
 * Exactly one of delegateTo or externalActorId must be non-null, and deadline must be non-null.
 * Validated in LifeCommitmentService before strategy dispatch.
 */
public record CommitmentRequest(
        String delegateTo,       // DELEGATION: principal id (household-member)
        UUID externalActorId,    // CONTRACTOR: ExternalActor to hold to commitment
        Instant deadline         // required for both modes
) {}
