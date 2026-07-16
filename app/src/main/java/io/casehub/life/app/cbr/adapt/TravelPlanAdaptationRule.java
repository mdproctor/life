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
public class TravelPlanAdaptationRule implements LifeAdaptationRule {

    private static final Set<String> CAPABILITIES = Set.of(
            "destination-research", "flight-search", "hotel-search",
            "budget-assessment", "booking", "rebooking",
            "confirmation", "booking-sentinel");

    @Override
    public String caseType() {
        return "travel-plan";
    }

    @Override
    public Set<String> knownCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                                   Map<String, FeatureValue> currentFeatures) {
        PlanCbrCase past = retrieved.cbrCase();
        double currentBudget = numericFeature(currentFeatures, "budget");
        double pastBudget = numericFeature(past.features(), "budget");
        String currentSeason = stringFeature(currentFeatures, "season");
        String pastSeason = stringFeature(past.features(), "season");
        boolean pastHadRejectedBooking = past.planTrace().stream()
                .anyMatch(t -> "booking".equals(t.capabilityName())
                        && t.stepOutcome() != null
                        && t.stepOutcome().toLowerCase().contains("reject"));

        List<AdaptedStep> steps = new ArrayList<>();
        for (PlanTrace trace : past.planTrace()) {
            Map<String, Object> params = new LinkedHashMap<>(trace.parameters());
            int priority = trace.priority();
            AdaptationAction action = AdaptationAction.RETAINED;
            String reason = null;

            if (pastHadRejectedBooking && "booking".equals(trace.capabilityName())) {
                action = AdaptationAction.SUPPRESSED;
                reason = "Past booking was rejected — try different approach";
                steps.add(new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                        trace.workerName(), trace.stepOutcome(), priority,
                        params, action, reason));
                continue;
            }

            if (currentBudget > 0 && pastBudget > 0 && Math.abs(currentBudget / pastBudget - 1.0) >= 0.05) {
                double ratio = Math.round(currentBudget / pastBudget * 100) / 100.0;
                params.put("budgetRatio", ratio);
                reason = "Budget scaled by %.0f%%".formatted(((currentBudget / pastBudget) - 1) * 100);
            }

            if (!currentSeason.isEmpty() && !pastSeason.isEmpty()
                    && !currentSeason.equalsIgnoreCase(pastSeason)) {
                reason = (reason != null ? reason + "; " : "")
                        + "Season changed (%s → %s) — pricing may differ".formatted(
                        pastSeason, currentSeason);
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
