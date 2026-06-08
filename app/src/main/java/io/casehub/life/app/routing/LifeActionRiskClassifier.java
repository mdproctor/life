package io.casehub.life.app.routing;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.PlannedAction;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.life.api.HouseholdActionType;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;

/**
 * Life-specific ActionRiskClassifier. Discovered by the engine via @RiskClassifier CDI qualifier.
 * Classifies consequential household agent actions as Autonomous or GateRequired
 * using HouseholdActionType policy plus YAML-configured thresholds.
 *
 * Scope: casehubio/life/oversight (verify mapping against engine#437).
 */
@ApplicationScoped
@RiskClassifier
public class LifeActionRiskClassifier implements ActionRiskClassifier {

    private static final String OVERSIGHT_SCOPE = "casehubio/life/oversight";
    private static final SettingsScope RISK_POLICY_SCOPE =
        SettingsScope.of("casehubio", "life", "risk-policy");

    @Inject
    PreferenceProvider preferenceProvider;

    @Override
    public RiskDecision classify(PlannedAction action) {
        return HouseholdActionType.fromActionType(action.actionType())
            .map(type -> classifyKnownType(type, action))
            .orElse(new RiskDecision.Autonomous());
    }

    private RiskDecision classifyKnownType(HouseholdActionType type, PlannedAction action) {
        return switch (type.gatePolicy()) {
            case ALWAYS           -> buildGate(type, action, preferenceProvider.resolve(RISK_POLICY_SCOPE));
            case NEVER            -> new RiskDecision.Autonomous();
            case AMOUNT_THRESHOLD -> classifyByAmount(type, action);
        };
    }

    private RiskDecision classifyByAmount(HouseholdActionType type, PlannedAction action) {
        Object raw = action.context().get("amount");
        if (raw == null) return new RiskDecision.Autonomous();
        double amount;
        try {
            amount = Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            return new RiskDecision.Autonomous();
        }
        Preferences prefs = preferenceProvider.resolve(RISK_POLICY_SCOPE);
        double threshold = resolveThreshold(type, prefs);
        return amount >= threshold ? buildGate(type, action, prefs) : new RiskDecision.Autonomous();
    }

    // ThresholdCategory removed from HouseholdActionType in #27 — switch directly on type.
    // This method is interim: replaced by per-type HouseholdRiskRule implementations in Plan B.
    private double resolveThreshold(HouseholdActionType type, Preferences prefs) {
        return switch (type) {
            case SPEND_PURCHASE, SPEND_SUBSCRIPTION_MODIFY ->
                prefs.get(LifeRiskPolicyKeys.SPEND_THRESHOLD).value();
            case BOOKING_REFUNDABLE ->
                prefs.get(LifeRiskPolicyKeys.BOOKING_THRESHOLD).value();
            case CONTRACTOR_ENGAGE ->
                prefs.get(LifeRiskPolicyKeys.CONTRACTOR_THRESHOLD).value();
            default -> throw new IllegalStateException(
                "resolveThreshold called for non-AMOUNT_THRESHOLD type: " + type);
        };
    }

    private RiskDecision.GateRequired buildGate(HouseholdActionType type, PlannedAction action, Preferences prefs) {
        long hours = (long) prefs.get(LifeRiskPolicyKeys.APPROVAL_EXPIRES_HOURS).value();
        return new RiskDecision.GateRequired(
            buildReason(type, action),
            type.reversible(),
            type.candidateGroups(),
            Duration.ofHours(hours),
            OVERSIGHT_SCOPE
        );
    }

    private String buildReason(HouseholdActionType type, PlannedAction action) {
        String amt = formatAmount(action.context());
        return switch (type) {
            case SPEND_PURCHASE, SPEND_SUBSCRIPTION_MODIFY ->
                "Spend of " + amt + " requires household approval";
            case SPEND_SUBSCRIPTION_CANCEL ->
                "Subscription cancellation — confirm before proceeding";
            case BOOKING_NONREFUNDABLE -> {
                String amtStr = action.context().containsKey("amount") ? " of " + amt : "";
                yield "Non-refundable booking" + amtStr + " — cannot be undone once confirmed";
            }
            case BOOKING_REFUNDABLE ->
                "Refundable booking of " + amt + " requires household approval";
            case HEALTH_APPOINTMENT_SPECIALIST ->
                "Specialist appointment referral — confirm before booking";
            case HEALTH_APPOINTMENT_GP ->
                throw new IllegalStateException("buildReason called for non-gated type: " + type);
            case HEALTH_MEDICATION_FLAG ->
                "Medication concern — family awareness required before any action";
            case CONTRACTOR_ENGAGE ->
                "Contractor instruction estimated at " + amt + " — approval required";
            case LEGAL_DOCUMENT_SUBMIT ->
                "Legal document submission — confirm before filing (irreversible)";
            case ELDER_CARE_DECISION ->
                "Care decision for dependent — family approval required";
        };
    }

    private String formatAmount(Map<String, Object> context) {
        Object amount   = context.get("amount");
        Object currency = context.getOrDefault("currency", "GBP");
        return amount != null ? currency + " " + amount : "unspecified amount";
    }
}
