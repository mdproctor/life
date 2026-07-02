package io.casehub.life.app.routing;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;
import io.casehub.life.api.HouseholdGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.casehub.life.api.HouseholdActionType.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LifeActionRiskClassifierQuarkusTest {

    @Inject
    @RiskClassifier
    LifeActionRiskClassifier classifier;

    @Inject
    @RiskClassifier
    Instance<ActionRiskClassifier> riskClassifiers;

    @Inject
    FixedCurrentPrincipal fixedPrincipal;

    @BeforeEach
    void setMemberPrincipal() {
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.MEMBER));
    }

    @AfterEach
    void resetPrincipal() {
        fixedPrincipal.reset();
    }

    @Test
    void riskClassifierInstance_isSatisfied() {
        assertFalse(riskClassifiers.isUnsatisfied(),
            "@RiskClassifier Instance<ActionRiskClassifier> must not be empty");
    }

    @Test
    void alwaysGateType_returnsGateRequired() {
        PlannedAction action = PlannedAction.of(
            "book specialist", HEALTH_APPOINTMENT_SPECIALIST.actionType(), Map.of());
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, null));
    }

    @Test
    void spendPurchase_belowYamlThreshold_returnsAutonomous() {
        // Confirms risk-policy.yaml loaded: member threshold is 100.0
        PlannedAction action = PlannedAction.of(
            "buy groceries", SPEND_PURCHASE.actionType(), Map.of("amount", "99.0"));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, null));
    }

    @Test
    void spendPurchase_atYamlThreshold_returnsGateRequired() {
        PlannedAction action = PlannedAction.of(
            "buy groceries", SPEND_PURCHASE.actionType(), Map.of("amount", "100.0"));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, null));
    }

    @Test
    void contractorEngage_atYamlThreshold_returnsGateRequired() {
        PlannedAction action = PlannedAction.of(
            "hire plumber", CONTRACTOR_ENGAGE.actionType(), Map.of("amount", "200.0"));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, null));
    }

    // --- RBAC: admin elevated threshold (end-to-end through YAML) ---

    @Test
    void admin_spendPurchase_belowAdminYamlThreshold_returnsAutonomous() {
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.ADMIN));
        // Admin threshold from YAML: 500.0. 400.0 is below that; above member threshold (100.0).
        PlannedAction action = PlannedAction.of(
            "large purchase", SPEND_PURCHASE.actionType(), Map.of("amount", "400.0"));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, null));
    }

    @Test
    void admin_spendPurchase_atAdminYamlThreshold_returnsGateRequired() {
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.ADMIN));
        PlannedAction action = PlannedAction.of(
            "large purchase", SPEND_PURCHASE.actionType(), Map.of("amount", "500.0"));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, null));
    }

    @Test
    void admin_bookingRefundable_belowAdminYamlThreshold_returnsAutonomous() {
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.ADMIN));
        // Admin booking threshold from YAML: 300.0. 200.0 is below that; above member threshold (150.0).
        PlannedAction action = PlannedAction.of(
            "hotel booking", BOOKING_REFUNDABLE.actionType(), Map.of("amount", "200.0"));
        assertInstanceOf(RiskDecision.Autonomous.class, classifier.classify(action, null));
    }

    // --- RBAC: junior always gates (end-to-end) ---

    @Test
    void junior_spendPurchase_belowMemberYamlThreshold_returnsGateRequired() {
        fixedPrincipal.setGroups(Set.of(HouseholdGroups.JUNIOR));
        // Member threshold: 100.0. 50.0 is below — junior always gates regardless.
        PlannedAction action = PlannedAction.of(
            "small purchase", SPEND_PURCHASE.actionType(), Map.of("amount", "50.0"));
        assertInstanceOf(RiskDecision.GateRequired.class, classifier.classify(action, null));
    }
}
