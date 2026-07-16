package io.casehub.life.app.cbr;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.life.api.LifeActorIds;
import io.casehub.life.api.LifeTrustDimensions;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

@ApplicationScoped
public class LifeTrustFeatureEnricher {


    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(LifeTrustFeatureEnricher.class);
    static final String ACTOR_TRUST_SCORE = "actorTrustScore";
    static final String ACTOR_DEADLINE_RELIABILITY = "actorDeadlineReliability";
    static final String ACTOR_COST_ACCURACY = "actorCostAccuracy";
    static final String ACTOR_FACTUAL_ACCURACY = "actorFactualAccuracy";
    static final String ACTOR_PROACTIVE_ALERTING = "actorProactiveAlerting";

    private final TrustGateService trustGateService;

    @Inject
    public LifeTrustFeatureEnricher(TrustGateService trustGateService) {
        this.trustGateService = trustGateService;
    }

    public Map<String, FeatureValue> enrich(Map<String, FeatureValue> features,
                                            Map<String, Object> context) {
        Object actorIdObj = context.get("externalActorId");
        if (actorIdObj == null) {
            return features;
        }

        String actorId;
        try {
            actorId = LifeActorIds.of(UUID.fromString(actorIdObj.toString()));
        } catch (IllegalArgumentException e) {
            LOG.warnf("Malformed externalActorId '%s' — skipping trust enrichment", actorIdObj);
            return features;
        }

        Map<String, FeatureValue> enriched = new LinkedHashMap<>(features);
        boolean                   added    = false;

        added |= addIfPresent(enriched, ACTOR_TRUST_SCORE,
                              trustGateService.currentScore(actorId));
        added |= addIfPresent(enriched, ACTOR_DEADLINE_RELIABILITY,
                              trustGateService.dimensionScore(actorId, LifeTrustDimensions.DEADLINE_RELIABILITY));
        added |= addIfPresent(enriched, ACTOR_COST_ACCURACY,
                              trustGateService.dimensionScore(actorId, LifeTrustDimensions.COST_ACCURACY));
        added |= addIfPresent(enriched, ACTOR_FACTUAL_ACCURACY,
                              trustGateService.dimensionScore(actorId, LifeTrustDimensions.FACTUAL_ACCURACY));
        added |= addIfPresent(enriched, ACTOR_PROACTIVE_ALERTING,
                              trustGateService.dimensionScore(actorId, LifeTrustDimensions.PROACTIVE_ALERTING));

        return added ? enriched : features;
    }

    private static boolean addIfPresent(Map<String, FeatureValue> target,
                                         String key, OptionalDouble score) {
        if (score.isPresent()) {
            target.put(key, FeatureValue.number(score.getAsDouble()));
            return true;
        }
        return false;
    }
}
