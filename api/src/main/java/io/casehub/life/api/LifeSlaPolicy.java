package io.casehub.life.api;

import java.time.Duration;
import java.util.Objects;

public record LifeSlaPolicy(String escalationGroup, Duration escalationDeadline) {
    public LifeSlaPolicy {
        Objects.requireNonNull(escalationGroup, "escalationGroup must not be null");
        Objects.requireNonNull(escalationDeadline, "escalationDeadline must not be null");
    }
}
