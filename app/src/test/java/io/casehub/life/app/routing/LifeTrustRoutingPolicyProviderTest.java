package io.casehub.life.app.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LifeTrustRoutingPolicyProvider.
 * Verifies CDI wiring, capability→domain mapping, and YAML preference overlay.
 */
@QuarkusTest
class LifeTrustRoutingPolicyProviderTest {

    @Inject
    TrustRoutingPolicyProvider provider;

    @Test
    void providerIsLifeImplementation() {
        assertInstanceOf(LifeTrustRoutingPolicyProvider.class, provider,
            "Injected provider should be LifeTrustRoutingPolicyProvider");
    }

    @Test
    void bookAppointmentResolvesToHealthCoordinationPolicy() {
        TrustRoutingPolicy policy = provider.forCapability("book-appointment");

        assertEquals(0.75, policy.threshold(), 0.0001, "Health coordination threshold should be 0.75");
        assertEquals(10, policy.minimumObservations(), "Health coordination minObs should be 10");
        assertEquals(0.05, policy.borderlineMargin(), 0.0001, "Health coordination margin should be 0.05");
    }

    @Test
    void requestQuoteResolvesToContractorCoordinationPolicy() {
        TrustRoutingPolicy policy = provider.forCapability("request-quote");

        assertEquals(0.65, policy.threshold(), 0.0001, "Contractor coordination threshold should be 0.65");
        assertEquals(8, policy.minimumObservations(), "Contractor coordination minObs should be 8");
    }

    @Test
    void unknownCapabilityReturnsDefault() {
        TrustRoutingPolicy policy = provider.forCapability("totally-unknown");

        assertEquals(TrustRoutingPolicy.DEFAULT.threshold(), policy.threshold(), 0.0001,
            "Unknown capability should return DEFAULT threshold");
        assertEquals(TrustRoutingPolicy.DEFAULT.minimumObservations(), policy.minimumObservations(),
            "Unknown capability should return DEFAULT minObs");
    }

    @Test
    void yamlBlendFactorWiredForHealthCoordination() {
        TrustRoutingPolicy policy = provider.forCapability("book-appointment");

        assertEquals(0.70, policy.blendFactor(), 0.0001,
            "Health coordination blend factor should be 0.70 from YAML");
    }

    @Test
    void healthCoordinationHasFactualAccuracyFloor() {
        TrustRoutingPolicy policy = provider.forCapability("book-appointment");

        assertTrue(policy.qualityFloors().containsKey("factual-accuracy"),
            "Health coordination should have factual-accuracy floor");
        assertEquals(0.60, policy.qualityFloors().get("factual-accuracy"), 0.0001,
            "Factual accuracy floor should be 0.60");
    }

    @Test
    void contractorCoordinationHasDeadlineReliabilityFloor() {
        TrustRoutingPolicy policy = provider.forCapability("request-quote");

        assertTrue(policy.qualityFloors().containsKey("deadline-reliability"),
            "Contractor coordination should have deadline-reliability floor");
        assertEquals(0.50, policy.qualityFloors().get("deadline-reliability"), 0.0001,
            "Deadline reliability floor should be 0.50");
    }

    @Test
    void contractorCoordinationHasCostAccuracyFloor() {
        TrustRoutingPolicy policy = provider.forCapability("request-quote");

        assertTrue(policy.qualityFloors().containsKey("cost-accuracy"),
            "Contractor coordination should have cost-accuracy floor");
        assertEquals(0.50, policy.qualityFloors().get("cost-accuracy"), 0.0001,
            "Cost accuracy floor should be 0.50");
    }

    @Test
    void householdManagementHasLowBlendFactor() {
        TrustRoutingPolicy policy = provider.forCapability("schedule-inspection");

        assertEquals(0.40, policy.blendFactor(), 0.0001,
            "Household management blend factor should be 0.40 from YAML");
    }

    @Test
    void legalDeadlineHasHighThreshold() {
        TrustRoutingPolicy policy = provider.forCapability("legal-deadline");

        assertEquals(0.80, policy.threshold(), 0.0001, "Legal deadline threshold should be 0.80");
        assertEquals(12, policy.minimumObservations(), "Legal deadline minObs should be 12");
    }

    @Test
    void elderCareResolvesFromMultipleCapabilities() {
        TrustRoutingPolicy needsAssessment = provider.forCapability("needs-assessment");
        TrustRoutingPolicy assessPatient = provider.forCapability("assess-patient");

        assertEquals(needsAssessment.threshold(), assessPatient.threshold(), 0.0001,
            "Both elder-care capabilities should map to same policy");
        assertEquals(0.75, needsAssessment.threshold(), 0.0001, "Elder care threshold should be 0.75");
    }

    @Test
    void travelPlanningHasMixedCapabilities() {
        TrustRoutingPolicy destResearch = provider.forCapability("destination-research");
        TrustRoutingPolicy booking = provider.forCapability("booking");

        assertEquals(destResearch.threshold(), booking.threshold(), 0.0001,
            "All travel-planning capabilities should map to same policy");
        assertEquals(0.55, destResearch.threshold(), 0.0001, "Travel planning threshold should be 0.55");
        assertEquals(0.50, destResearch.blendFactor(), 0.0001, "Travel planning blend factor should be 0.50");
    }
}
