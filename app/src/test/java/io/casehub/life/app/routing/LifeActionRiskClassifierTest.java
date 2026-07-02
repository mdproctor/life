package io.casehub.life.app.routing;

import io.casehub.api.spi.RiskDecision.Autonomous;
import io.casehub.api.spi.RiskDecision.GateRequired;
import io.casehub.worker.api.PlannedAction;
import io.casehub.life.api.HouseholdActionType;
import io.casehub.life.api.HouseholdGroups;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ContextNotActiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static io.casehub.life.api.HouseholdActionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifeActionRiskClassifierTest {

    @Mock
    private PreferenceProvider preferenceProvider;

    @Mock
    private CurrentPrincipal principal;

    @InjectMocks
    private LifeActionRiskClassifier classifier;

    @BeforeEach
    void setUp() {
        Preferences prefs = mock(Preferences.class);
        lenient().when(preferenceProvider.resolve(any(SettingsScope.class))).thenReturn(prefs);
        // Member thresholds
        lenient().when(prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD)).thenReturn(DoublePreference.of(100.0));
        lenient().when(prefs.get(LifeRiskPolicyKeys.CONTRACTOR_THRESHOLD)).thenReturn(DoublePreference.of(200.0));
        lenient().when(prefs.get(LifeRiskPolicyKeys.BOOKING_THRESHOLD)).thenReturn(DoublePreference.of(150.0));
        lenient().when(prefs.get(LifeRiskPolicyKeys.APPROVAL_EXPIRES_HOURS)).thenReturn(DoublePreference.of(24.0));
        // Admin thresholds
        lenient().when(prefs.get(LifeRiskPolicyKeys.ADMIN_SPEND_THRESHOLD)).thenReturn(DoublePreference.of(500.0));
        lenient().when(prefs.get(LifeRiskPolicyKeys.ADMIN_CONTRACTOR_THRESHOLD)).thenReturn(DoublePreference.of(500.0));
        lenient().when(prefs.get(LifeRiskPolicyKeys.ADMIN_BOOKING_THRESHOLD)).thenReturn(DoublePreference.of(300.0));
        // Default: member groups — AMOUNT_THRESHOLD tests exercise member-threshold path
        lenient().when(principal.groups()).thenReturn(Set.of(HouseholdGroups.MEMBER));
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
        assertInstanceOf(GateRequired.class, classifier.classify(action(SPEND_SUBSCRIPTION_CANCEL), null));
    }

    @Test
    void bookingNonrefundable_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(BOOKING_NONREFUNDABLE), null));
    }

    @Test
    void healthAppointmentSpecialist_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(HEALTH_APPOINTMENT_SPECIALIST), null));
    }

    @Test
    void healthMedicationFlag_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(HEALTH_MEDICATION_FLAG), null));
    }

    @Test
    void legalDocumentSubmit_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(LEGAL_DOCUMENT_SUBMIT), null));
    }

    @Test
    void elderCareDecision_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(action(ELDER_CARE_DECISION), null));
    }

    // --- NEVER gate ---

    @Test
    void healthAppointmentGp_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(action(HEALTH_APPOINTMENT_GP), null));
    }

    // --- reversible ---

    @Test
    void bookingNonrefundable_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE), null);
        assertFalse(result.reversible());
    }

    @Test
    void legalDocumentSubmit_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(LEGAL_DOCUMENT_SUBMIT), null);
        assertFalse(result.reversible());
    }

    @Test
    void healthMedicationFlag_isIrreversible() {
        GateRequired result = (GateRequired) classifier.classify(action(HEALTH_MEDICATION_FLAG), null);
        assertFalse(result.reversible());
    }

    // --- candidateGroups ---

    @Test
    void healthMedicationFlag_candidateGroupsIncludesMember() {
        GateRequired result = (GateRequired) classifier.classify(action(HEALTH_MEDICATION_FLAG), null);
        assertTrue(result.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void elderCareDecision_candidateGroupsIncludesMember() {
        GateRequired result = (GateRequired) classifier.classify(action(ELDER_CARE_DECISION), null);
        assertTrue(result.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void bookingNonrefundable_candidateGroupsAdminOnly() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE), null);
        assertEquals(1, result.candidateGroups().size());
        assertEquals(HouseholdGroups.ADMIN, result.candidateGroups().get(0));
    }

    // --- scope and expiry ---

    @Test
    void gateRequired_scopeIsOversightScope() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE), null);
        assertEquals("casehubio/life/oversight", result.scope());
    }

    @Test
    void gateRequired_expiresIn24Hours() {
        GateRequired result = (GateRequired) classifier.classify(action(BOOKING_NONREFUNDABLE), null);
        assertEquals(Duration.ofHours(24), result.expiresIn());
    }

    // --- AMOUNT_THRESHOLD: spend ---

    @Test
    void spendPurchase_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 99.99), null));
    }

    @Test
    void spendPurchase_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 100.0), null));
    }

    @Test
    void spendSubscriptionModify_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_SUBSCRIPTION_MODIFY, 100.0), null));
    }

    @Test
    void spendSubscriptionModify_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(SPEND_SUBSCRIPTION_MODIFY, 99.99), null));
    }

    // --- AMOUNT_THRESHOLD: contractor ---

    @Test
    void contractorEngage_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 199.99), null));
    }

    @Test
    void contractorEngage_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 200.0), null));
    }

    // --- AMOUNT_THRESHOLD: booking ---

    @Test
    void bookingRefundable_belowThreshold_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 149.99), null));
    }

    @Test
    void bookingRefundable_atThreshold_returnsGateRequired() {
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 150.0), null));
    }

    // --- missing / bad amount ---

    @Test
    void spendPurchase_missingAmount_returnsAutonomous() {
        assertInstanceOf(Autonomous.class, classifier.classify(action(SPEND_PURCHASE), null));
    }

    @Test
    void spendPurchase_unparsableAmount_returnsAutonomous() {
        PlannedAction bad = PlannedAction.of("test", SPEND_PURCHASE.actionType(),
            Map.of("amount", "not-a-number"));
        assertInstanceOf(Autonomous.class, classifier.classify(bad, null));
    }

    // --- unknown / null actionType ---

    @Test
    void unknownActionType_returnsAutonomous() {
        PlannedAction unknown = PlannedAction.of("test", "foo.bar", Map.of());
        assertInstanceOf(Autonomous.class, classifier.classify(unknown, null));
    }

    @Test
    void nullActionType_throwsNullPointer() {
        // PlannedAction (worker-api record) rejects null actionType via requireNonNull.
        assertThrows(NullPointerException.class, () -> PlannedAction.of("test", null, Map.of()));
    }

    // --- RBAC: admin elevated threshold ---

    @Test
    void admin_spendPurchase_belowAdminThreshold_returnsAutonomous() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.ADMIN));
        // 400.0 is below admin threshold (500.0) but above member threshold (100.0)
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 400.0), null));
    }

    @Test
    void admin_spendPurchase_atAdminThreshold_returnsGateRequired() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.ADMIN));
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 500.0), null));
    }

    @Test
    void admin_contractorEngage_belowAdminThreshold_returnsAutonomous() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.ADMIN));
        // 400.0 is below admin contractor threshold (500.0) but above member threshold (200.0)
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 400.0), null));
    }

    @Test
    void admin_bookingRefundable_belowAdminThreshold_returnsAutonomous() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.ADMIN));
        // 200.0 is below admin booking threshold (300.0) but above member threshold (150.0)
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 200.0), null));
    }

    @Test
    void admin_bookingRefundable_atAdminThreshold_returnsGateRequired() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.ADMIN));
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(BOOKING_REFUNDABLE, 300.0), null));
    }

    // --- RBAC: junior always gates on AMOUNT_THRESHOLD ---

    @Test
    void junior_spendPurchase_belowMemberThreshold_returnsGateRequired() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.JUNIOR));
        // 50.0 is below member threshold (100.0) — junior always gates regardless
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 50.0), null));
    }

    @Test
    void junior_contractorEngage_belowMemberThreshold_returnsGateRequired() {
        when(principal.groups()).thenReturn(Set.of(HouseholdGroups.JUNIOR));
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(CONTRACTOR_ENGAGE, 10.0), null));
    }

    // --- RBAC: context inactive — member threshold fallback ---

    @Test
    void contextInactive_aboveThreshold_returnsGateRequired() {
        when(principal.groups()).thenThrow(ContextNotActiveException.class);
        // No context → member fallback: 100.0 >= 100.0 → gate
        assertInstanceOf(GateRequired.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 100.0), null));
    }

    @Test
    void contextInactive_belowThreshold_returnsAutonomous() {
        when(principal.groups()).thenThrow(ContextNotActiveException.class);
        // No context → member fallback: 50.0 < 100.0 → autonomous (NOT always-gate for background workers)
        assertInstanceOf(Autonomous.class, classifier.classify(actionWithAmount(SPEND_PURCHASE, 50.0), null));
    }
}
