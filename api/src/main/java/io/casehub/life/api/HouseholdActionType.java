package io.casehub.life.api;

import java.util.List;
import java.util.Optional;

/**
 * Typed taxonomy of consequential household actions declared by workers before execution.
 * Workers use actionType() when constructing PlannedAction; fromActionType() reverses the mapping.
 * Each constant encodes its inherent domain properties — gatePolicy, reversible, candidateGroups
 * — so all logic for a type lives here. Threshold key resolution is handled in app/ routing
 * via LifeRiskPolicyKeys, not in this enum.
 */
public enum HouseholdActionType {

    SPEND_PURCHASE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN)),

    SPEND_SUBSCRIPTION_CANCEL(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN)),

    SPEND_SUBSCRIPTION_MODIFY(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN)),

    BOOKING_NONREFUNDABLE(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN)),

    BOOKING_REFUNDABLE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN)),

    HEALTH_APPOINTMENT_SPECIALIST(
        GatePolicy.ALWAYS, true,
        List.of(HouseholdGroups.ADMIN)),

    /** Routine GP booking — no gate required. */
    HEALTH_APPOINTMENT_GP(
        GatePolicy.NEVER, true,
        List.of()),

    /** Medication interaction — irreversible safety concern; any adult can approve (speed matters). */
    HEALTH_MEDICATION_FLAG(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN, HouseholdGroups.MEMBER)),

    CONTRACTOR_ENGAGE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN)),

    LEGAL_DOCUMENT_SUBMIT(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN)),

    /** Care decision for a dependent — any adult can approve (urgency matters). */
    ELDER_CARE_DECISION(
        GatePolicy.ALWAYS, true,
        List.of(HouseholdGroups.ADMIN, HouseholdGroups.MEMBER));

    public enum GatePolicy {
        ALWAYS,           // unconditional gate
        AMOUNT_THRESHOLD, // gate when context["amount"] >= configured threshold
        NEVER             // always autonomous
    }

    private final GatePolicy gatePolicy;
    private final boolean reversible;
    private final List<String> candidateGroups;

    HouseholdActionType(GatePolicy gatePolicy, boolean reversible, List<String> candidateGroups) {
        this.gatePolicy = gatePolicy;
        this.reversible = reversible;
        this.candidateGroups = List.copyOf(candidateGroups);
    }

    public GatePolicy gatePolicy() { return gatePolicy; }

    public boolean reversible() { return reversible; }

    public List<String> candidateGroups() { return candidateGroups; }

    /** The actionType string for PlannedAction.actionType(). e.g. SPEND_PURCHASE → "spend.purchase" */
    public String actionType() {
        return name().toLowerCase().replace('_', '.');
    }

    /** Parse a PlannedAction.actionType() string back to enum. Empty if unknown. */
    public static Optional<HouseholdActionType> fromActionType(String actionType) {
        if (actionType == null) return Optional.empty();
        try {
            return Optional.of(valueOf(actionType.toUpperCase().replace('.', '_')));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
