package io.casehub.life.app.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.life.api.LifeCapabilities;
import io.casehub.life.api.LifeRoutingPolicy;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Life-specific trust routing policy provider.
 * Maps fine-grained worker capability names to coarse-grained life domains,
 * then applies domain-specific thresholds, observation requirements, and margins.
 * Overlays YAML-configured blend factors and quality floors via PreferenceProvider.
 */
@ApplicationScoped
public class LifeTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    private static final Map<String, String> CAPABILITY_TO_DOMAIN = createCapabilityMap();
    private static final Map<String, LifeRoutingPolicy> POLICIES = createPolicyMap();

    @Inject
    PreferenceProvider preferenceProvider;

    @Override
    public TrustRoutingPolicy forCapability(String capabilityName) {
        String domainKey = CAPABILITY_TO_DOMAIN.getOrDefault(capabilityName, capabilityName);
        LifeRoutingPolicy lifePolicy = POLICIES.get(domainKey);

        if (lifePolicy == null) {
            return TrustRoutingPolicy.DEFAULT;
        }

        SettingsScope scope = SettingsScope.of("casehubio", "life", "trust-routing", domainKey);
        Preferences prefs = preferenceProvider.resolve(scope);

        double threshold = lifePolicy.threshold().orElse(TrustRoutingPolicy.DEFAULT.threshold());
        int minObs = lifePolicy.minimumObservations().orElse(TrustRoutingPolicy.DEFAULT.minimumObservations());
        double margin = lifePolicy.borderlineMargin().orElse(TrustRoutingPolicy.DEFAULT.borderlineMargin());

        DoublePreference blendPref = prefs.get(LifeTrustRoutingPolicyKeys.BLEND_FACTOR);
        double blendFactor = (blendPref != null) ? blendPref.value() : TrustRoutingPolicy.DEFAULT.blendFactor();

        Map<String, Double> qualityFloors = buildQualityFloors(prefs);

        return new TrustRoutingPolicy(threshold, minObs, margin, blendFactor, Map.copyOf(qualityFloors), false);
    }

    private Map<String, Double> buildQualityFloors(Preferences prefs) {
        Map<String, Double> floors = new HashMap<>();
        for (Map.Entry<String, PreferenceKey<DoublePreference>> entry
                : LifeTrustRoutingPolicyKeys.allFloorKeys().entrySet()) {
            String dimensionName = entry.getKey();
            PreferenceKey<DoublePreference> key = entry.getValue();
            DoublePreference value = prefs.get(key);
            if (value != null && value.value() > 0.0) {
                floors.put(dimensionName, value.value());
            }
        }
        return floors;
    }

    private static Map<String, String> createCapabilityMap() {
        Map<String, String> map = new HashMap<>();

        // appointment-cycle → health-coordination
        map.put("book-appointment", LifeCapabilities.HEALTH_COORDINATION);
        map.put("find-alternative", LifeCapabilities.HEALTH_COORDINATION);
        map.put("confirm-appointment", LifeCapabilities.HEALTH_COORDINATION);
        map.put("pre-visit-prep", LifeCapabilities.HEALTH_COORDINATION);
        map.put("record-health-decision", LifeCapabilities.HEALTH_COORDINATION);

        // care-coordination → elder-care
        map.put("needs-assessment", LifeCapabilities.ELDER_CARE);
        map.put("care-plan", LifeCapabilities.ELDER_CARE);
        map.put("health-check", LifeCapabilities.ELDER_CARE);

        // care-episode → elder-care
        map.put("assess-patient", LifeCapabilities.ELDER_CARE);
        map.put("provide-care", LifeCapabilities.ELDER_CARE);

        // contractor-coordination → contractor-coordination
        map.put("request-quote", LifeCapabilities.CONTRACTOR_COORDINATION);
        map.put("watchdog-escalation", LifeCapabilities.CONTRACTOR_COORDINATION);
        map.put("quote-received", LifeCapabilities.CONTRACTOR_COORDINATION);
        map.put("job-monitoring", LifeCapabilities.CONTRACTOR_COORDINATION);
        map.put("record-payment", LifeCapabilities.CONTRACTOR_COORDINATION);

        // financial-review → financial-planning
        map.put("gather-data", LifeCapabilities.FINANCIAL_PLANNING);
        map.put("analyse-anomalies", LifeCapabilities.FINANCIAL_PLANNING);
        map.put("escalate-anomalies", LifeCapabilities.FINANCIAL_PLANNING);
        map.put("oversight-response", LifeCapabilities.FINANCIAL_PLANNING);
        map.put("produce-report", LifeCapabilities.FINANCIAL_PLANNING);

        // home-maintenance → household-management
        map.put("schedule-inspection", LifeCapabilities.HOUSEHOLD_MANAGEMENT);
        map.put("get-quotes", LifeCapabilities.HOUSEHOLD_MANAGEMENT);
        map.put("issue-commitment", LifeCapabilities.HOUSEHOLD_MANAGEMENT);
        map.put("monitor-job", LifeCapabilities.HOUSEHOLD_MANAGEMENT);
        map.put("record-completion", LifeCapabilities.HOUSEHOLD_MANAGEMENT);

        // travel-plan → travel-planning
        map.put("destination-research", LifeCapabilities.TRAVEL_PLANNING);
        map.put("flight-search", LifeCapabilities.TRAVEL_PLANNING);
        map.put("hotel-search", LifeCapabilities.TRAVEL_PLANNING);
        map.put("budget-assessment", LifeCapabilities.TRAVEL_PLANNING);
        map.put("booking", LifeCapabilities.TRAVEL_PLANNING);
        map.put("rebooking", LifeCapabilities.TRAVEL_PLANNING);
        map.put("confirmation", LifeCapabilities.TRAVEL_PLANNING);

        return Map.copyOf(map);
    }

    private static Map<String, LifeRoutingPolicy> createPolicyMap() {
        Map<String, LifeRoutingPolicy> map = new HashMap<>();

        map.put(LifeCapabilities.HEALTH_COORDINATION, new LifeRoutingPolicy(
            OptionalDouble.of(0.75),
            OptionalInt.of(10),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "High reliability required for health appointments and follow-ups"
        ));

        map.put(LifeCapabilities.LEGAL_DEADLINE, new LifeRoutingPolicy(
            OptionalDouble.of(0.80),
            OptionalInt.of(12),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "Critical deadlines with legal consequences require highest reliability"
        ));

        map.put(LifeCapabilities.FINANCIAL_PLANNING, new LifeRoutingPolicy(
            OptionalDouble.of(0.70),
            OptionalInt.of(10),
            OptionalDouble.of(0.10),
            Optional.of("household-admin"),
            "Financial decisions require cost accuracy but tolerate wider margin"
        ));

        map.put(LifeCapabilities.CONTRACTOR_COORDINATION, new LifeRoutingPolicy(
            OptionalDouble.of(0.65),
            OptionalInt.of(8),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "Contractor follow-up balances deadline reliability and cost accuracy"
        ));

        map.put(LifeCapabilities.ELDER_CARE, new LifeRoutingPolicy(
            OptionalDouble.of(0.75),
            OptionalInt.of(10),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "Care coordination requires high reliability and proactive alerting"
        ));

        map.put(LifeCapabilities.HOUSEHOLD_MANAGEMENT, new LifeRoutingPolicy(
            OptionalDouble.of(0.50),
            OptionalInt.of(5),
            OptionalDouble.empty(),
            Optional.empty(),
            "Routine household tasks tolerate lower threshold, no escalation"
        ));

        map.put(LifeCapabilities.FAMILY_SCHEDULING, new LifeRoutingPolicy(
            OptionalDouble.of(0.50),
            OptionalInt.of(5),
            OptionalDouble.empty(),
            Optional.empty(),
            "Family calendar coordination is low-stakes"
        ));

        map.put(LifeCapabilities.TRAVEL_PLANNING, new LifeRoutingPolicy(
            OptionalDouble.of(0.55),
            OptionalInt.of(6),
            OptionalDouble.of(0.05),
            Optional.of("household-admin"),
            "Travel research and booking require moderate reliability"
        ));

        return Map.copyOf(map);
    }
}
