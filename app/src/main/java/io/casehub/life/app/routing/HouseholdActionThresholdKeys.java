package io.casehub.life.app.routing;

import io.casehub.life.api.HouseholdActionType;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.Preferences;

import java.util.Map;
import java.util.Objects;

import static io.casehub.life.api.HouseholdActionType.*;

/**
 * Descriptor map linking HouseholdActionType constants to their threshold preference keys.
 * Only AMOUNT_THRESHOLD types have entries — calling forType() with a non-threshold type
 * (ALWAYS/NEVER) throws IllegalStateException.
 */
public final class HouseholdActionThresholdKeys {

    /**
     * Pair of preference keys: one for household-member threshold, one for household-admin.
     * resolve() selects the correct key based on caller RBAC tier.
     */
    public record ThresholdKeyPair(
            PreferenceKey<DoublePreference> member,
            PreferenceKey<DoublePreference> admin) {

        /**
         * Resolve the threshold value from preferences using the appropriate tier key.
         * @param prefs resolved Preferences for the risk-policy scope
         * @param isAdmin true if caller is household-admin, false otherwise
         * @return the configured threshold value
         */
        public double resolve(Preferences prefs, boolean isAdmin) {
            return prefs.get(isAdmin ? admin : member).value();
        }
    }

    private static final Map<HouseholdActionType, ThresholdKeyPair> KEYS = Map.of(
        SPEND_PURCHASE,            new ThresholdKeyPair(LifeRiskPolicyKeys.SPEND_THRESHOLD, LifeRiskPolicyKeys.ADMIN_SPEND_THRESHOLD),
        SPEND_SUBSCRIPTION_MODIFY, new ThresholdKeyPair(LifeRiskPolicyKeys.SPEND_THRESHOLD, LifeRiskPolicyKeys.ADMIN_SPEND_THRESHOLD),
        BOOKING_REFUNDABLE,        new ThresholdKeyPair(LifeRiskPolicyKeys.BOOKING_THRESHOLD, LifeRiskPolicyKeys.ADMIN_BOOKING_THRESHOLD),
        CONTRACTOR_ENGAGE,         new ThresholdKeyPair(LifeRiskPolicyKeys.CONTRACTOR_THRESHOLD, LifeRiskPolicyKeys.ADMIN_CONTRACTOR_THRESHOLD)
    );

    /**
     * Get the threshold key pair for the given action type.
     * @param type the action type (must have GatePolicy.AMOUNT_THRESHOLD)
     * @return the threshold key pair
     * @throws NullPointerException if no threshold keys are configured for this type
     *         (i.e. the type is not AMOUNT_THRESHOLD gated)
     */
    public static ThresholdKeyPair forType(HouseholdActionType type) {
        return Objects.requireNonNull(KEYS.get(type),
            "No threshold keys for non-AMOUNT_THRESHOLD type: " + type);
    }

    private HouseholdActionThresholdKeys() {}
}
