package io.casehub.life.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LifeDomainTest {

    @ParameterizedTest
    @EnumSource(LifeDomain.class)
    void allDomains_haveDescriptor(LifeDomain domain) {
        assertNotNull(domain.descriptor());
    }

    @ParameterizedTest
    @EnumSource(LifeDomain.class)
    void fromCategory_roundTrips(LifeDomain domain) {
        Optional<LifeDomain> result = LifeDomain.fromCategory(domain.descriptor().templateCategory());
        assertTrue(result.isPresent(), "fromCategory() should find: " + domain);
        assertEquals(domain, result.get());
    }

    @Test void fromCategory_null_returnsEmpty() {
        assertTrue(LifeDomain.fromCategory(null).isEmpty());
    }

    @Test void fromCategory_unknown_returnsEmpty() {
        assertTrue(LifeDomain.fromCategory("xyz-unknown").isEmpty());
    }

    @Test void fromCategory_health_returnsHealth() {
        assertEquals(Optional.of(LifeDomain.HEALTH), LifeDomain.fromCategory("health"));
    }

    @Test void fromCategory_elderCare_returnsElderCare() {
        assertEquals(Optional.of(LifeDomain.ELDER_CARE), LifeDomain.fromCategory("elder-care"));
    }
}
