package io.casehub.life.app.routing;

import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.platform.api.preferences.PreferenceKey;

import java.util.Map;

/**
 * PreferenceKey constants for life trust routing configuration.
 * Namespace: casehubio.life.trust-routing
 * Includes blend factor and per-dimension quality floors.
 */
public final class LifeTrustRoutingPolicyKeys {

    private static final String NAMESPACE = "casehubio.life.trust-routing";

    public static final PreferenceKey<DoublePreference> BLEND_FACTOR =
        new PreferenceKey<>(NAMESPACE, "blend-factor", DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> FLOOR_DEADLINE_RELIABILITY =
        new PreferenceKey<>(NAMESPACE, "floor." + LifeTrustDimensions.DEADLINE_RELIABILITY, DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> FLOOR_COST_ACCURACY =
        new PreferenceKey<>(NAMESPACE, "floor." + LifeTrustDimensions.COST_ACCURACY, DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> FLOOR_FACTUAL_ACCURACY =
        new PreferenceKey<>(NAMESPACE, "floor." + LifeTrustDimensions.FACTUAL_ACCURACY, DoublePreference.of(0.0), DoublePreference::parse);

    public static final PreferenceKey<DoublePreference> FLOOR_PROACTIVE_ALERTING =
        new PreferenceKey<>(NAMESPACE, "floor." + LifeTrustDimensions.PROACTIVE_ALERTING, DoublePreference.of(0.0), DoublePreference::parse);

    private static final Map<String, PreferenceKey<DoublePreference>> ALL_FLOOR_KEYS = Map.of(
        LifeTrustDimensions.DEADLINE_RELIABILITY, FLOOR_DEADLINE_RELIABILITY,
        LifeTrustDimensions.COST_ACCURACY, FLOOR_COST_ACCURACY,
        LifeTrustDimensions.FACTUAL_ACCURACY, FLOOR_FACTUAL_ACCURACY,
        LifeTrustDimensions.PROACTIVE_ALERTING, FLOOR_PROACTIVE_ALERTING
    );

    /**
     * Returns all quality floor preference keys keyed by trust dimension name.
     */
    public static Map<String, PreferenceKey<DoublePreference>> allFloorKeys() {
        return ALL_FLOOR_KEYS;
    }

    private LifeTrustRoutingPolicyKeys() {}
}
