package io.casehub.life.api.response;

import io.casehub.life.api.LifeDomain;

import java.util.UUID;

public record LifeTaskContextResponse(
        UUID workItemId,
        LifeDomain domain,
        UUID externalActorId,
        String recurrence,
        String jurisdiction
) {}
