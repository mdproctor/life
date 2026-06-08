package io.casehub.life.api.descriptor;

import io.casehub.life.api.LifeCapabilities;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HealthDomainDescriptorTest {

    private final HealthDomainDescriptor descriptor = new HealthDomainDescriptor();

    @Test void capability_isHealthCoordination() {
        assertEquals(LifeCapabilities.HEALTH_COORDINATION, descriptor.capability());
    }

    @Test void templateCategory_isHealth() {
        assertEquals("health", descriptor.templateCategory());
    }

    @Test void routingPolicy_threshold075() {
        assertEquals(0.75, descriptor.routingPolicy().threshold().getAsDouble());
    }

    @Test void routingPolicy_minObservations10() {
        assertEquals(10, descriptor.routingPolicy().minimumObservations().getAsInt());
    }

    @Test void routingPolicy_hasFallback() {
        assertTrue(descriptor.routingPolicy().fallbackType().isPresent());
    }

    @Test void workerCapabilities_containsBookAppointment() {
        assertTrue(descriptor.workerCapabilities().contains("book-appointment"));
    }

    @Test void workerCapabilities_fiveEntries() {
        assertEquals(5, descriptor.workerCapabilities().size());
    }

    @Test void slaPolicy_escalationGroup_isAdmin() {
        assertEquals("household-admin", descriptor.slaPolicy().escalationGroup());
    }

    @Test void slaPolicy_deadline_is24Hours() {
        assertEquals(Duration.ofHours(24), descriptor.slaPolicy().escalationDeadline());
    }
}
