package io.casehub.life.api;

import java.util.List;
import java.util.Optional;

/**
 * Typed taxonomy of consequential household actions declared by workers before execution.
 * Workers use actionType() when constructing PlannedAction; fromActionType() reverses the mapping.
 * Each constant encodes its inherent domain properties — gatePolicy, reversible, candidateGroups,
 * reasonTemplate — so all logic for a type lives here. Threshold key resolution is handled in app/
 * routing via LifeRiskPolicyKeys, not in this enum.
 */
public enum HouseholdActionType {

    SPEND_PURCHASE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN),
        "Spend of %s requires household approval"),

    SPEND_SUBSCRIPTION_CANCEL(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN),
        "Subscription cancellation — confirm before proceeding"),

    SPEND_SUBSCRIPTION_MODIFY(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN),
        "Spend of %s requires household approval"),

    BOOKING_NONREFUNDABLE(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN),
        "Non-refundable booking of %s — cannot be undone once confirmed"),

    BOOKING_REFUNDABLE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN),
        "Refundable booking of %s requires household approval"),

    HEALTH_APPOINTMENT_SPECIALIST(
        GatePolicy.ALWAYS, true,
        List.of(HouseholdGroups.ADMIN),
        "Specialist appointment referral — confirm before booking"),

    /** Routine GP booking — no gate required. */
    HEALTH_APPOINTMENT_GP(
        GatePolicy.NEVER, true,
        List.of(),
        null),

    /** Medication interaction — irreversible safety concern; any adult can approve (speed matters). */
    HEALTH_MEDICATION_FLAG(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN, HouseholdGroups.MEMBER),
        "Medication concern — family awareness required before any action"),

    CONTRACTOR_ENGAGE(
        GatePolicy.AMOUNT_THRESHOLD, true,
        List.of(HouseholdGroups.ADMIN),
        "Contractor instruction estimated at %s — approval required"),

    LEGAL_DOCUMENT_SUBMIT(
        GatePolicy.ALWAYS, false,
        List.of(HouseholdGroups.ADMIN),
        "Legal document submission — confirm before filing (irreversible)"),

    /** Care decision for a dependent — any adult can approve (urgency matters). */
    ELDER_CARE_DECISION(
        GatePolicy.ALWAYS, true,
        List.of(HouseholdGroups.ADMIN, HouseholdGroups.MEMBER),
        "Care decision for dependent — family approval required");

    public enum GatePolicy {
        ALWAYS,           // unconditional gate
        AMOUNT_THRESHOLD, // gate when context["amount"] >= configured threshold
        NEVER             // always autonomous
    }

    private final GatePolicy gatePolicy;
    private final boolean reversible;
    private final List<String> candidateGroups;
    private final String reasonTemplate;

    HouseholdActionType(GatePolicy gatePolicy, boolean reversible, List<String> candidateGroups, String reasonTemplate) {
        this.gatePolicy = gatePolicy;
        this.reversible = reversible;
        this.candidateGroups = List.copyOf(candidateGroups);
        this.reasonTemplate = reasonTemplate;
    }

    public GatePolicy gatePolicy() { return gatePolicy; }

    public boolean reversible() { return reversible; }

    public List<String> candidateGroups() { return candidateGroups; }

    /**
     * Reason template for GateRequired.reason(). Nullable for NEVER-gated types (HEALTH_APPOINTMENT_GP).
     * May contain %s format specifiers for amount substitution. String.formatted() silently ignores
     * extra args, so templates without %s work fine when formatted with amount.
     */
    public String reasonTemplate() { return reasonTemplate; }

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
