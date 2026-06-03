package io.casehub.life.api.response;

import io.casehub.life.api.LifeActorType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ExternalActorResponse(
        UUID id,
        String name,
        LifeActorType actorType,
        String contactMethod,
        String contactValue,
        Instant createdAt,
        Instant gdprErasedAt,
        TrustProfile trustProfile
) {
    public record TrustProfile(
        Double globalScore,
        Map<String, Double> dimensionScores,
        Map<String, Double> capabilityScores
    ) {
        public static final TrustProfile EMPTY =
            new TrustProfile(null, Map.of(), Map.of());
    }
}
