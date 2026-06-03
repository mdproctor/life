package io.casehub.life.api;

import java.util.Objects;
import java.util.UUID;

public final class LifeActorIds {

    public static final String PREFIX = "life-actor:";

    public static String of(final UUID externalActorId) {
        Objects.requireNonNull(externalActorId, "externalActorId must not be null");
        return PREFIX + externalActorId;
    }

    public static boolean isLifeActor(final String actorId) {
        return actorId != null && actorId.startsWith(PREFIX);
    }

    public static UUID extractId(final String actorId) {
        return UUID.fromString(actorId.substring(PREFIX.length()));
    }

    private LifeActorIds() {}
}
