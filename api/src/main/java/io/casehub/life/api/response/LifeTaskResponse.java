package io.casehub.life.api.response;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.commitment.CommitmentMode;
import io.casehub.life.api.commitment.CommitmentStatus;

import java.time.Instant;
import java.util.UUID;

public record LifeTaskResponse(
        UUID workItemId,
        String templateRef,
        LifeDomain domain,
        String status,
        UUID externalActorId,          // nullable
        Instant createdAt,
        CommitmentMode commitmentMode,  // null if no commitment on this task
        CommitmentStatus commitmentStatus
) {}
