package io.casehub.life.api.response;

import io.casehub.life.api.LifeActorType;

import java.time.Instant;
import java.util.UUID;

public record ExternalActorResponse(
        UUID id,
        String name,
        LifeActorType actorType,
        String contactMethod,
        String contactValue,
        Instant createdAt
) {}
