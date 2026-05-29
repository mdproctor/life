package io.casehub.life.api.commitment;

import java.util.UUID;

public record CommitmentOutcome(
        UUID recordId,
        String correlationId,
        CommitmentMode mode,
        CommitmentStatus status
) {}
