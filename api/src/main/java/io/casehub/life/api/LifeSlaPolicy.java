package io.casehub.life.api;

import java.time.Duration;

public record LifeSlaPolicy(String escalationGroup, Duration escalationDeadline) {}
