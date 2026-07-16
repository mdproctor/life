package io.casehub.life.app.cbr.adapt;

import io.casehub.life.app.cbr.LifeAdaptationRule;
import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class HealthAdaptationRule implements LifeAdaptationRule {

    private static final Set<String> CAPABILITIES = Set.of(
            "needs-assessment", "care-plan", "health-check",
            "care-quality-sentinel");

    @Override
    public String caseType() {
        return "care-coordination";
    }

    @Override
    public Set<String> knownCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                                   Map<String, FeatureValue> currentFeatures) {
        PlanCbrCase past            = retrieved.cbrCase();
        double      currentRisk     = numericFeature(currentFeatures, "patientRiskLevel");
        double      pastRisk        = numericFeature(past.features(), "patientRiskLevel");
        String      currentCareType = stringFeature(currentFeatures, "careType");
        String      pastCareType    = stringFeature(past.features(), "careType");
        boolean pastHadSlaBreach = past.planTrace().stream()
                                       .anyMatch(t -> t.stepOutcome() != null && t.stepOutcome().contains("breach"));
        double trustScore      = numericFeature(currentFeatures, "actorTrustScore");
        double factualAccuracy = numericFeature(currentFeatures, "actorFactualAccuracy");

        List<AdaptedStep> steps = new ArrayList<>();
        for (PlanTrace trace : past.planTrace()) {
            Map<String, Object> params   = new LinkedHashMap<>(trace.parameters());
            int                 priority = trace.priority();
            AdaptationAction    action   = AdaptationAction.RETAINED;
            String              reason   = null;

            int scaled = SeverityScaling.scale(pastRisk, currentRisk, priority);
            if (scaled > priority) {
                priority = scaled;
                action   = AdaptationAction.BOOSTED;
                reason   = "Higher patient risk level (%.1f → %.1f)".formatted(pastRisk, currentRisk);
            }

            if (pastHadSlaBreach && "health-check".equals(trace.capabilityName())) {
                priority = Math.min(priority + 2, 10);
                action   = AdaptationAction.BOOSTED;
                reason   = (reason != null ? reason + "; " : "") + "Past case had SLA breach";
            }

            if (!pastCareType.isEmpty() && !currentCareType.isEmpty()
                && !pastCareType.equals(currentCareType)) {
                reason = (reason != null ? reason + "; " : "")
                         + "Care type changed (%s → %s) — review applicability".formatted(
                        pastCareType, currentCareType);
            }

            if (trustScore > 0 && trustScore < 0.3) {
                reason = (reason != null ? reason + "; " : "")
                         + "Provider trust below threshold (%.2f) — review provider selection".formatted(trustScore);
            }

            if (factualAccuracy > 0 && factualAccuracy < 0.5
                && "health-check".equals(trace.capabilityName())) {
                priority = Math.min(priority + 1, 10);
                action   = AdaptationAction.BOOSTED;
                reason   = (reason != null ? reason + "; " : "")
                           + "Low factual accuracy (%.2f) — additional verification needed".formatted(factualAccuracy);
            }

            steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                                      trace.workerName(), trace.stepOutcome(), priority,
                                      params, action, reason));
        }
        return steps;
    }

    private static String stringFeature(Map<String, FeatureValue> features, String key) {
        FeatureValue fv = features.get(key);
        return fv instanceof FeatureValue.StringVal sv ? sv.value() : "";
    }

    private static double numericFeature(Map<String, FeatureValue> features, String key) {
        FeatureValue fv = features.get(key);
        return fv instanceof FeatureValue.NumberVal nv ? nv.value() : 0.0;
    }
}
