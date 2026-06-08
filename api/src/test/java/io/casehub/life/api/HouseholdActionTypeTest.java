package io.casehub.life.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static io.casehub.life.api.HouseholdActionType.*;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdActionTypeTest {

    @ParameterizedTest
    @EnumSource(HouseholdActionType.class)
    void actionType_roundTrips(HouseholdActionType type) {
        String s = type.actionType();
        Optional<HouseholdActionType> parsed = HouseholdActionType.fromActionType(s);
        assertTrue(parsed.isPresent(), "round-trip failed for: " + s);
        assertEquals(type, parsed.get());
    }

    @Test
    void fromActionType_null_returnsEmpty() {
        assertTrue(HouseholdActionType.fromActionType(null).isEmpty());
    }

    @Test
    void fromActionType_unknown_returnsEmpty() {
        assertTrue(HouseholdActionType.fromActionType("foo.bar").isEmpty());
    }

    @Test
    void spendPurchase_actionTypeString() {
        assertEquals("spend.purchase", SPEND_PURCHASE.actionType());
    }

    @Test
    void bookingNonrefundable_actionTypeString() {
        assertEquals("booking.nonrefundable", BOOKING_NONREFUNDABLE.actionType());
    }

    @Test
    void healthMedicationFlag_actionTypeString() {
        assertEquals("health.medication.flag", HEALTH_MEDICATION_FLAG.actionType());
    }

    @Test
    void bookingNonrefundable_isIrreversible() {
        assertFalse(BOOKING_NONREFUNDABLE.reversible());
    }

    @Test
    void legalDocumentSubmit_isIrreversible() {
        assertFalse(LEGAL_DOCUMENT_SUBMIT.reversible());
    }

    @Test
    void spendSubscriptionCancel_isIrreversible() {
        assertFalse(SPEND_SUBSCRIPTION_CANCEL.reversible());
    }

    @Test
    void healthMedicationFlag_isIrreversible() {
        assertFalse(HEALTH_MEDICATION_FLAG.reversible());
    }

    @Test
    void healthMedicationFlag_candidateGroupsIncludesMember() {
        assertTrue(HEALTH_MEDICATION_FLAG.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void elderCareDecision_candidateGroupsIncludesMember() {
        assertTrue(ELDER_CARE_DECISION.candidateGroups().contains(HouseholdGroups.MEMBER));
    }

    @Test
    void healthAppointmentGp_isNeverGated() {
        assertEquals(HouseholdActionType.GatePolicy.NEVER, HEALTH_APPOINTMENT_GP.gatePolicy());
    }

}
