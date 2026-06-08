package io.casehub.life.api.descriptor;

import io.casehub.life.api.LifeDomainDescriptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AllDomainDescriptorsTest {

    static Stream<LifeDomainDescriptor> allDescriptors() {
        return Stream.of(
            new HealthDomainDescriptor(),
            new LegalDomainDescriptor(),
            new FinanceDomainDescriptor(),
            new HouseholdDomainDescriptor(),
            new FamilySchedulingDomainDescriptor(),
            new TravelDomainDescriptor(),
            new ContractorCoordinationDomainDescriptor(),
            new ElderCareDomainDescriptor()
        );
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void capability_notNull(LifeDomainDescriptor d) {
        assertNotNull(d.capability());
        assertFalse(d.capability().isBlank());
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void templateCategory_notNull(LifeDomainDescriptor d) {
        assertNotNull(d.templateCategory());
        assertFalse(d.templateCategory().isBlank());
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void workerCapabilities_notNull(LifeDomainDescriptor d) {
        assertNotNull(d.workerCapabilities());
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void routingPolicy_notNull(LifeDomainDescriptor d) {
        assertNotNull(d.routingPolicy());
        assertTrue(d.routingPolicy().threshold().isPresent());
        assertTrue(d.routingPolicy().minimumObservations().isPresent());
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void slaPolicy_notNull(LifeDomainDescriptor d) {
        assertNotNull(d.slaPolicy());
        assertNotNull(d.slaPolicy().escalationGroup());
        assertNotNull(d.slaPolicy().escalationDeadline());
    }

    @ParameterizedTest @MethodSource("allDescriptors")
    void householdAndFamilyScheduling_emptyMarginAndFallback(LifeDomainDescriptor d) {
        if (d instanceof HouseholdDomainDescriptor || d instanceof FamilySchedulingDomainDescriptor) {
            assertTrue(d.routingPolicy().borderlineMargin().isEmpty());
            assertTrue(d.routingPolicy().fallbackType().isEmpty());
        }
    }
}
