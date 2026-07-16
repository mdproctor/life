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
public class HomeMaintenanceAdaptationRule implements LifeAdaptationRule {

    private static final Set<String> CAPABILITIES = Set.of(
            "schedule-inspection", "get-quotes", "issue-commitment",
            "monitor-job", "record-completion", "maintenance-sentinel");

    private static final Set<String> WINTER_URGENT_TYPES = Set.of(
            "heating", "plumbing", "boiler", "insulation", "roof-leak");

    @Override
    public String caseType() {
        return "home-maintenance";
    }

    @Override
    public Set<String> knownCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                                   Map<String, FeatureValue> currentFeatures) {
        PlanCbrCase past                = retrieved.cbrCase();
        String      currentSeason       = stringFeature(currentFeatures, "season");
        String      currentIssueType    = stringFeature(currentFeatures, "issueType");
        double      currentCost         = numericFeature(currentFeatures, "estimatedCost");
        double      pastCost            = numericFeature(past.features(), "estimatedCost");
        double      pastResolutionDays  = numericFeature(past.features(), "resolutionDays");
        boolean     pastFailed          = "FAILED".equals(past.outcome());
        double      trustScore          = numericFeature(currentFeatures, "actorTrustScore");
        double      deadlineReliability = numericFeature(currentFeatures, "actorDeadlineReliability");

        boolean isWinterUrgent = "winter".equalsIgnoreCase(currentSeason)
                                 && WINTER_URGENT_TYPES.contains(currentIssueType.toLowerCase());

        List<AdaptedStep> steps = new ArrayList<>();
        for (PlanTrace trace : past.planTrace()) {
            if (pastFailed && !"maintenance-sentinel".equals(trace.capabilityName())) {
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                                          trace.workerName(), trace.stepOutcome(), trace.priority(),
                                          trace.parameters(), AdaptationAction.SUPPRESSED,
                                          "Past case with this approach failed"));
                continue;
            }

            Map<String, Object> params   = new LinkedHashMap<>(trace.parameters());
            AdaptationAction    action   = AdaptationAction.RETAINED;
            String              reason   = null;
            int                 priority = trace.priority();

            if (isWinterUrgent && pastResolutionDays > 0) {
                int adjustedDays = (int) Math.max(1, pastResolutionDays / 2);
                params.put("resolutionDays", adjustedDays);
                action   = AdaptationAction.BOOSTED;
                priority = Math.min(priority + 2, 10);
                reason   = "Winter " + currentIssueType + " — SLA halved to " + adjustedDays + " days";
            }

            if (currentCost > 0 && pastCost > 0 && Math.abs(currentCost / pastCost - 1.0) >= 0.05) {
                params.put("costRatio", Math.round(currentCost / pastCost * 100) / 100.0);
                if (reason == null) {
                    reason = "Cost scaled by %.0f%%".formatted(((currentCost / pastCost) - 1) * 100);
                }
            }

            if (trustScore > 0 && trustScore < 0.3) {
                reason = (reason != null ? reason + "; " : "")
                         + "Actor trust critically low (%.2f) — consider alternative contractor".formatted(trustScore);
            }

            if (deadlineReliability > 0 && deadlineReliability < 0.5
                && "maintenance-sentinel".equals(trace.capabilityName())) {
                priority = Math.min(priority + 2, 10);
                action   = AdaptationAction.BOOSTED;
                reason   = (reason != null ? reason + "; " : "")
                           + "Low deadline reliability (%.2f) — tighter monitoring".formatted(deadlineReliability);
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
