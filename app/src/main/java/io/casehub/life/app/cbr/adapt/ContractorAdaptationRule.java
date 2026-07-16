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
public class ContractorAdaptationRule implements LifeAdaptationRule {

    private static final Set<String> CAPABILITIES = Set.of(
            "request-quote", "watchdog-escalation", "quote-received",
            "job-monitoring", "record-payment", "contractor-sentinel");

    private static final Set<String> WINTER_PROBLEM_TYPES = Set.of(
            "heating", "plumbing", "boiler", "insulation");

    @Override
    public String caseType() {
        return "contractor-coordination";
    }

    @Override
    public Set<String> knownCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                                   Map<String, FeatureValue> currentFeatures) {
        PlanCbrCase past = retrieved.cbrCase();
        String currentSeason = stringFeature(currentFeatures, "season");
        String currentProblemType = stringFeature(currentFeatures, "problemType");
        double currentBudget = numericFeature(currentFeatures, "budget");
        double pastBudget = numericFeature(past.features(), "budget");
        boolean pastFailed = "FAILED".equals(past.outcome());

        List<AdaptedStep> steps = new ArrayList<>();
        for (PlanTrace trace : past.planTrace()) {
            if (pastFailed && !"contractor-sentinel".equals(trace.capabilityName())) {
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                        trace.workerName(), trace.stepOutcome(), trace.priority(),
                        trace.parameters(), AdaptationAction.SUPPRESSED,
                        "Past case with this approach failed"));
                continue;
            }

            boolean isWinterUrgent = "winter".equalsIgnoreCase(currentSeason)
                    && WINTER_PROBLEM_TYPES.contains(currentProblemType.toLowerCase());

            if ("request-quote".equals(trace.capabilityName()) && isWinterUrgent) {
                Map<String, Object> params = new LinkedHashMap<>(trace.parameters());
                double pastSla = numericFeature(past.features(), "slaHours");
                if (pastSla > 0) {
                    params.put("slaHours", (int) Math.max(24, pastSla / 2));
                }
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                        trace.workerName(), trace.stepOutcome(),
                        Math.min(trace.priority() + 2, 10), params,
                        AdaptationAction.BOOSTED,
                        "Winter " + currentProblemType + " — tighter SLA needed"));
                continue;
            }

            Map<String, Object> params = adjustCostParams(trace, currentBudget, pastBudget);
            if (params != null) {
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                        trace.workerName(), trace.stepOutcome(), trace.priority(),
                        params, AdaptationAction.RETAINED,
                        "Budget scaled by %.0f%%".formatted(
                                ((currentBudget / pastBudget) - 1) * 100)));
            } else {
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                        trace.workerName(), trace.stepOutcome(), trace.priority(),
                        trace.parameters(), AdaptationAction.RETAINED, null));
            }
        }
        return steps;
    }

    private Map<String, Object> adjustCostParams(PlanTrace trace,
                                                  double currentBudget, double pastBudget) {
        if (currentBudget <= 0 || pastBudget <= 0) return null;
        double ratio = currentBudget / pastBudget;
        if (Math.abs(ratio - 1.0) < 0.05) return null;
        Map<String, Object> params = new LinkedHashMap<>(trace.parameters());
        params.put("budgetRatio", Math.round(ratio * 100) / 100.0);
        return params;
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
