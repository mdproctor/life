package io.casehub.life.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Domain-specific routing policy configuration record.
 * Holds threshold, observation requirements, margin, fallback strategy, and rationale.
 * Mapped from static configuration to engine's TrustRoutingPolicy via preference overlay.
 */
public record LifeRoutingPolicy(
    OptionalDouble threshold,
    OptionalInt minimumObservations,
    OptionalDouble borderlineMargin,
    Optional<String> fallbackType,
    String rationale
) {
    public LifeRoutingPolicy {
        Objects.requireNonNull(threshold, "threshold must not be null");
        Objects.requireNonNull(minimumObservations, "minimumObservations must not be null");
        Objects.requireNonNull(borderlineMargin, "borderlineMargin must not be null");
        Objects.requireNonNull(fallbackType, "fallbackType must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
    }
}
