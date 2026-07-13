package io.casehub.life.api.response;

import io.casehub.life.api.LifeDomain;
import java.time.Instant;
import java.util.UUID;

public record ActorActivityEntry(
        UUID workItemId,
        String title,
        LifeDomain domain,
        String status,
        String scope,
        Instant createdAt,
        Instant completedAt,
        String outcome) {}
