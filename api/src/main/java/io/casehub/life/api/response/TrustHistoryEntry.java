package io.casehub.life.api.response;

import java.time.Instant;

public record TrustHistoryEntry(
        Instant occurredAt,
        String capabilityTag,
        String dimension,
        Double score,
        String verdict) {}
