package io.casehub.life.api.response;

import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.Urgency;
import java.time.Instant;
import java.util.UUID;

public record PendingActionResponse(
        UUID workItemId,
        String title,
        String description,
        String status,
        LifeDomain domain,
        String candidateGroups,
        Instant createdAt,
        Instant expiresAt,
        Urgency urgency,
        Long daysOverdue) {}
