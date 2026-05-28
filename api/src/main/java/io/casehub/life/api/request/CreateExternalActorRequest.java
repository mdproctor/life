package io.casehub.life.api.request;

import io.casehub.life.api.LifeActorType;
import jakarta.validation.constraints.NotNull;

public record CreateExternalActorRequest(
        @NotNull String name,
        @NotNull LifeActorType actorType,
        @NotNull String contactMethod,
        @NotNull String contactValue
) {}
