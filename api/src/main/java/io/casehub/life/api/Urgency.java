package io.casehub.life.api;

import java.time.Duration;
import java.time.Instant;

public enum Urgency {
    OVERDUE, DUE_SOON, NORMAL, NO_DEADLINE;

    public static Urgency classify(Instant expiresAt, Instant now, int dueSoonHours) {
        if (expiresAt == null) return NO_DEADLINE;
        if (!expiresAt.isAfter(now)) return OVERDUE;
        if (Duration.between(now, expiresAt).toHours() <= dueSoonHours) return DUE_SOON;
        return NORMAL;
    }

    public static Long daysOverdue(Instant expiresAt, Instant now) {
        if (expiresAt == null || expiresAt.isAfter(now)) return null;
        return Duration.between(expiresAt, now).toDays();
    }
}
