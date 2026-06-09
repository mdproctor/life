package io.casehub.life.app.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.LifeRoutingPolicy;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Life-specific trust routing policy provider.
 * Builds a capability index at startup from domain descriptors, eliminating static maps.
 * Each domain descriptor owns its worker capabilities and routing policy.
 * The coarse capability name itself (e.g. "legal-deadline") is also indexed, mirroring
 * the old static-map fallback behaviour for domains with no fine-grained capabilities.
 */
@ApplicationScoped
public class LifeTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    @Inject
    PreferenceProvider preferenceProvider;

    private Map<String, LifeDomain> capabilityIndex;

    @PostConstruct
    void buildCapabilityIndex() {
        Map<String, LifeDomain> index = new HashMap<>();
        for (LifeDomain domain : LifeDomain.values()) {
            // Index every fine-grained worker capability name declared by the descriptor.
            for (String cap : domain.descriptor().workerCapabilities()) {
                index.put(cap, domain);
            }
            // Also index the coarse capability string itself (e.g. "legal-deadline").
            // This preserves the old fallback: forCapability("legal-deadline") resolves
            // even when no fine-grained capabilities are declared for that domain.
            index.putIfAbsent(domain.descriptor().capability(), domain);
        }
        this.capabilityIndex = Map.copyOf(index);
    }

    @Override
    public TrustRoutingPolicy forCapability(String capabilityName) {
        LifeDomain domain = capabilityIndex.get(capabilityName);
        if (domain == null) {
            return TrustRoutingPolicy.DEFAULT;
        }

        LifeRoutingPolicy base = domain.descriptor().routingPolicy();
        // Scope key = coarse capability string (e.g. "health-coordination") — matches trust-routing.yaml.
        SettingsScope scope = SettingsScope.of("casehubio", "life", "trust-routing",
                domain.descriptor().capability());
        Preferences prefs = preferenceProvider.resolve(scope);

        double threshold = base.threshold().orElse(TrustRoutingPolicy.DEFAULT.threshold());
        int    minObs    = base.minimumObservations().orElse(TrustRoutingPolicy.DEFAULT.minimumObservations());
        double margin    = base.borderlineMargin().orElse(TrustRoutingPolicy.DEFAULT.borderlineMargin());

        DoublePreference blendPref = prefs.get(LifeTrustRoutingPolicyKeys.BLEND_FACTOR);
        double blendFactor = blendPref != null ? blendPref.value() : TrustRoutingPolicy.DEFAULT.blendFactor();

        Map<String, Double> qualityFloors = buildQualityFloors(prefs);

        return new TrustRoutingPolicy(threshold, minObs, margin, blendFactor,
                Map.copyOf(qualityFloors), false);
    }

    private Map<String, Double> buildQualityFloors(Preferences prefs) {
        Map<String, Double> floors = new HashMap<>();
        for (Map.Entry<String, PreferenceKey<DoublePreference>> entry
                : LifeTrustRoutingPolicyKeys.allFloorKeys().entrySet()) {
            DoublePreference value = prefs.get(entry.getValue());
            if (value != null && value.value() > 0.0) {
                floors.put(entry.getKey(), value.value());
            }
        }
        return floors;
    }
}
