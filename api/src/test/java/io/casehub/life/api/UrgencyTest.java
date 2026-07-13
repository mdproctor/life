package io.casehub.life.api;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class UrgencyTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void classify_nullExpiresAt_returnsNoDeadline() {
        assertEquals(Urgency.NO_DEADLINE, Urgency.classify(null, NOW, 24));
    }

    @Test
    void classify_pastDeadline_returnsOverdue() {
        Instant expired = NOW.minusSeconds(3600);
        assertEquals(Urgency.OVERDUE, Urgency.classify(expired, NOW, 24));
    }

    @Test
    void classify_within24Hours_returnsDueSoon() {
        Instant soonExpires = NOW.plusSeconds(3600 * 12);
        assertEquals(Urgency.DUE_SOON, Urgency.classify(soonExpires, NOW, 24));
    }

    @Test
    void classify_beyondWindow_returnsNormal() {
        Instant farExpires = NOW.plusSeconds(3600 * 48);
        assertEquals(Urgency.NORMAL, Urgency.classify(farExpires, NOW, 24));
    }

    @Test
    void classify_customDueSoonHours() {
        Instant in6Hours = NOW.plusSeconds(3600 * 6);
        assertEquals(Urgency.DUE_SOON, Urgency.classify(in6Hours, NOW, 8));
        assertEquals(Urgency.NORMAL, Urgency.classify(in6Hours, NOW, 4));
    }

    @Test
    void classify_exactlyAtDeadline_returnsOverdue() {
        assertEquals(Urgency.OVERDUE, Urgency.classify(NOW, NOW, 24));
    }

    @Test
    void classify_exactlyAtWindowBoundary_returnsDueSoon() {
        Instant exactly24h = NOW.plusSeconds(3600 * 24);
        assertEquals(Urgency.DUE_SOON, Urgency.classify(exactly24h, NOW, 24));
    }

    @Test
    void daysOverdue_nullExpires_returnsNull() {
        assertNull(Urgency.daysOverdue(null, NOW));
    }

    @Test
    void daysOverdue_futureDeadline_returnsNull() {
        assertNull(Urgency.daysOverdue(NOW.plusSeconds(3600), NOW));
    }

    @Test
    void daysOverdue_3DaysOverdue_returns3() {
        Instant expired = NOW.minusSeconds(3600 * 24 * 3);
        assertEquals(3L, Urgency.daysOverdue(expired, NOW));
    }
}
