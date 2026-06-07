package io.casehub.life.app.routing;

import io.casehub.platform.api.preferences.PreferenceKey;

/**
 * PreferenceKey constants for household risk policy configuration.
 * Namespace: casehubio.life.risk-policy
 * All amounts are in the household's local currency (default GBP).
 */
public final class LifeRiskPolicyKeys {

    private static final String NS = "casehubio.life.risk-policy";

    /** Purchases and subscription modifications at or above this amount require approval. Default: 100.0. */
    public static final PreferenceKey<DoublePreference> SPEND_THRESHOLD =
        new PreferenceKey<>(NS, "spend.threshold", DoublePreference.of(100.0), DoublePreference::parse);

    /** Contractor work instructions at or above this estimated cost require approval. Default: 200.0. */
    public static final PreferenceKey<DoublePreference> CONTRACTOR_THRESHOLD =
        new PreferenceKey<>(NS, "contractor.threshold", DoublePreference.of(200.0), DoublePreference::parse);

    /** Refundable bookings at or above this amount require approval. Default: 150.0. */
    public static final PreferenceKey<DoublePreference> BOOKING_THRESHOLD =
        new PreferenceKey<>(NS, "booking.threshold", DoublePreference.of(150.0), DoublePreference::parse);

    /** Hours before an unanswered approval gate expires. Default: 24.0. */
    public static final PreferenceKey<DoublePreference> APPROVAL_EXPIRES_HOURS =
        new PreferenceKey<>(NS, "approval.expires-hours", DoublePreference.of(24.0), DoublePreference::parse);

    private LifeRiskPolicyKeys() {}
}
