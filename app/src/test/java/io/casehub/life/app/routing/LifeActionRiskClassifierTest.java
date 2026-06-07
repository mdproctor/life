package io.casehub.life.app.routing;

import io.casehub.api.spi.PlannedAction;
import io.casehub.api.spi.RiskDecision;
import io.casehub.api.spi.RiskDecision.Autonomous;
import io.casehub.api.spi.RiskDecision.GateRequired;
import io.casehub.life.api.HouseholdActionType;
import io.casehub.life.api.HouseholdGroups;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static io.casehub.life.api.HouseholdActionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifeActionRiskClassifierTest {

    @Mock
    private PreferenceProvider preferenceProvider;

    @InjectMocks
    private LifeActionRiskClassifier classifier;

    @BeforeEach
    void setUp() {
        Preferences prefs = mock(Preferences.class);
        when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
        when(prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD)).thenReturn(DoublePreference.of(100.0));
        when(prefs.get(LifeRiskPolicyKeys.CONTRACTOR_THRESHOLD)).thenReturn(DoublePreference.of(200.0));
        when(prefs.get(LifeRiskPolicyKeys.BOOKING_THRESHOLD)).thenReturn(DoublePreference.of(150.0));
        when(prefs.get(LifeRiskPolicyKeys.APPROVAL_EXPIRES_HOURS)).thenReturn(DoublePreference.of(24.0));
    }

    // --- helpers ---

    private PlannedAction action(HouseholdActionType type) {
        return PlannedAction.of("test", type.actionType(), Map.of());
    }

    private PlannedAction actionWithAmount(HouseholdActionType type, double amount) {
        return PlannedAction.of("test", type.actionType(), Map.of("amount", String.valueOf(amount)));
    }

    // --- ALWAYS gate types ---

    @Test
    void spendSubscriptionCancel_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(SPEND_SUBSCRIPTION_CANCEL)));
    }

    @Test
    void bookingNonrefundable_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(BOOKING_NONREFUNDABLE)));
    }

    @Test
    void healthAppointmentSpecialist_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(HEALTH_APPOINTMENT_SPECIALIST)));
    }

    @Test
    void healthMedicationFlag_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(HEALTH_MEDICATION_FLAG)));
    }

    @Test
    void legalDocumentSubmit_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(LEGAL_DOCUMENT_SUBMIT)));
    }

    @Test
    void elderCareDecision_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(ELDER_CARE_DECISION)));
    }

    // --- NEVER gate ---

    @Test
    void healthAppointmentGp_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(action(HEALTH_APPOINTMENT_GP)));
    }

    // --- reversible ---

    @Test
    void bookingNonrefundable_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE));
        assertFalse(result.reversible());
    }

    @Test
    void legalDocumentSubmit_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(LEGAL_DOCUMENT_SUBMIT));
        assertFalse(result.reversible());
    }

    @Test
    void healthMedicationFlag_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(HEALTH_MEDICATION_FLAG));
        assertFalse(result.reversible());
    }

    // --- candidateGroups ---

    @Test
    void healthMedicationFlag_candidateGroupsIncludesMember() {
        GateRequired result = (GateRequired) classifier.classify(action(HEALTH_MEDICATION_FLAG));
        assertTrue(result.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void elderCareDecision_candidateGroupsIncludesMember() {
        GateRequired result = (GateRequired) classifier.classify(action(ELDER_CARE_DECISION));
        assertTrue(result.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void bookingNonrefundable_candidateGroupsAdminOnly() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE));
        assertEquals(1, result.candidateGroups().size());
        assertEquals(HouseholdGroups.ADMIN, result.candidateGroups().get(0));
    }

    // --- scope and expiry ---

    @Test
    void gateRequired_scopeIsOversightScope() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE));
        assertEquals("casehubio/life/oversight", result.scope());
    }

    @Test
    void gateRequired_expiresIn24Hours() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE));
        assertEquals(Duration.ofHours(24), result.expiresIn());
    }

    // --- AMOUNT_THRESHOLD: spend ---

    @Test
    void spendPurchase_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 99.99)));
    }

    @Test
    void spendPurchase_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 100.0)));
    }

    @Test
    void spendSubscriptionModify_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_SUBSCRIPTION_MODIFY, 100.0)));
    }

    // --- AMOUNT_THRESHOLD: contractor ---

    @Test
    void contractorEngage_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 199.99)));
    }

    @Test
    void contractorEngage_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 200.0)));
    }

    // --- AMOUNT_THRESHOLD: booking ---

    @Test
    void bookingRefundable_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 149.99)));
    }

    @Test
    void bookingRefundable_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 150.0)));
    }

    // --- missing / bad amount ---

    @Test
    void spendPurchase_missingAmount_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(action(SPEND_PURCHASE)));
    }

    @Test
    void spendPurchase_unparsableAmount_returnsAutonomous() {
        PlannedAction bad = PlannedAction.of("test", SPEND_PURCHASE.actionType(),
            Map.of("amount", "not-a-number"));
        assertInstanceOf(Autonomous.class, classifier.classify(bad));
    }

    // --- unknown / null actionType ---

    @Test
    void unknownActionType_returnsAutonomous() {
        PlannedAction unknown = PlannedAction.of("test", "foo.bar", Map.of());
        assertInstanceOf(Autonomous.class, classifier.classify(unknown));
    }

    @Test
    void nullActionType_returnsAutonomous() {
        PlannedAction nullType = PlannedAction.of("test", null, Map.of());
        assertInstanceOf(Autonomous.class, classifier.classify(nullType));
    }
}
