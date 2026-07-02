package io.casehub.life.app.routing;

import io.casehub.api.spi.ActionRiskClassifier;
import io.casehub.api.spi.ClassificationContext;
import io.casehub.api.spi.RiskClassifier;
import io.casehub.api.spi.RiskDecision;
import io.casehub.worker.api.PlannedAction;
import io.casehub.life.api.HouseholdActionType;
import io.casehub.life.api.HouseholdGroups;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
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

    @Inject PreferenceProvider preferenceProvider;
    @Inject CurrentPrincipal principal;

    @Override
    public RiskDecision classify(PlannedAction action, ClassificationContext context) {
        return HouseholdActionType.fromActionType(action.actionType())
            .map(type -> classifyKnownType(type, action))
            .orElse(new RiskDecision.Autonomous());
    }

    private RiskDecision classifyKnownType(HouseholdActionType type, PlannedAction action) {
        return switch (type.gatePolicy()) {
            case ALWAYS -> buildGate(type, action, preferenceProvider.resolve(RISK_POLICY_SCOPE));
            case NEVER  -> new RiskDecision.Autonomous();
            case AMOUNT_THRESHOLD -> {
                // Compute admin once; pass it down to avoid additional groups() lookups.
                final boolean admin = isAdmin();
                yield isJunior(admin)
                    ? buildGate(type, action, preferenceProvider.resolve(RISK_POLICY_SCOPE))
                    : classifyByAmount(type, action, admin);
            }
        };
    }

    private RiskDecision classifyByAmount(HouseholdActionType type, PlannedAction action, boolean admin) {
        Object raw = action.parameters().get("amount");
        if (raw == null) return new RiskDecision.Autonomous();
        double amount;
        try {
            amount = Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            return new RiskDecision.Autonomous();
        }
        Preferences prefs = preferenceProvider.resolve(RISK_POLICY_SCOPE);
        double threshold = resolveThreshold(type, prefs, admin);
        return amount >= threshold ? buildGate(type, action, prefs) : new RiskDecision.Autonomous();
    }

    private double resolveThreshold(HouseholdActionType type, Preferences prefs, boolean admin) {
        return HouseholdActionThresholdKeys.forType(type).resolve(prefs, admin);
    }

    private boolean isAdmin() {
        try {
            return principal.groups().contains(HouseholdGroups.ADMIN);
        } catch (ContextNotActiveException e) {
            return false;
        }
    }

    private boolean isJunior(boolean admin) {
        // Short-circuit: if caller is admin they cannot also be junior.
        if (admin) return false;
        try {
            // Negative definition is deliberate: unknown/unrecognised roles → always-gate.
            // Fail-secure for a financial-gate system: an unrecognised identity must never
            // act autonomously. The JUNIOR constant is used in @RolesAllowed; the classifier
            // uses the negative form so any non-admin, non-member identity gets the same
            // restrictive treatment.
            return !principal.groups().contains(HouseholdGroups.MEMBER);
        } catch (ContextNotActiveException e) {
            return false;
        }
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
        return type.reasonTemplate().formatted(formatAmount(action.parameters()));
    }

    private String formatAmount(Map<String, Object> context) {
        Object amount   = context.get("amount");
        Object currency = context.getOrDefault("currency", "GBP");
        return amount != null ? currency + " " + amount : "unspecified amount";
    }
}
