package io.casehub.life.app.routing;

import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.api.spi.routing.DoublePreference;
import io.casehub.api.spi.routing.TrustRoutingPolicyKeys;
import io.casehub.api.spi.routing.TrustRoutingPolicyResolver;
import io.casehub.life.api.LifeDomain;
import io.casehub.life.api.LifeRoutingPolicy;
import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class LifeTrustRoutingPolicyProvider implements TrustRoutingPolicyProvider {

    static final TrustRoutingPolicyKeys KEYS =
            TrustRoutingPolicyKeys.create("casehubio.life.trust-routing")
                    .withFloor(LifeTrustDimensions.DEADLINE_RELIABILITY, "deadline-reliability")
                    .withFloor(LifeTrustDimensions.COST_ACCURACY, "cost-accuracy")
                    .withFloor(LifeTrustDimensions.FACTUAL_ACCURACY, "factual-accuracy")
                    .withFloor(LifeTrustDimensions.PROACTIVE_ALERTING, "proactive-alerting");

    @Override
    public String id() {
        return "life";
    }

    @Inject
    PreferenceProvider preferenceProvider;

    private Map<String, LifeDomain> capabilityIndex;

    @PostConstruct
    void buildCapabilityIndex() {
        Map<String, LifeDomain> index = new HashMap<>();
        for (LifeDomain domain : LifeDomain.values()) {
            for (String cap : domain.descriptor().workerCapabilities()) {
                index.put(cap, domain);
            }
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
        SettingsScope scope = SettingsScope.of("casehubio", "life", "trust-routing",
                domain.descriptor().capability());
        Preferences prefs = preferenceProvider.resolve(scope);

        double threshold = base.threshold().orElse(TrustRoutingPolicy.DEFAULT.threshold());
        int    minObs    = base.minimumObservations().orElse(TrustRoutingPolicy.DEFAULT.minimumObservations());
        double margin    = base.borderlineMargin().orElse(TrustRoutingPolicy.DEFAULT.borderlineMargin());

        DoublePreference blendPref = prefs.get(KEYS.blendFactor());
        double blendFactor = blendPref != null ? blendPref.value() : TrustRoutingPolicy.DEFAULT.blendFactor();

        Map<String, Double> qualityFloors =
                TrustRoutingPolicyResolver.collectFloors(prefs, KEYS.allFloorKeys());

        return new TrustRoutingPolicy(threshold, minObs, margin, blendFactor,
                Map.copyOf(qualityFloors), false,
                TrustRoutingPolicy.DEFAULT.fallbackBinding());
    }
}
