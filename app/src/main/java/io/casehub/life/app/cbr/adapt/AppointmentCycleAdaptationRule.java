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
public class AppointmentCycleAdaptationRule implements LifeAdaptationRule {

    private static final Set<String> CAPABILITIES = Set.of(
            "book-appointment", "find-alternative", "confirm-appointment",
            "pre-visit-prep", "record-health-decision", "follow-up-sentinel");

    @Override
    public String caseType() {
        return "appointment-cycle";
    }

    @Override
    public Set<String> knownCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<AdaptedStep> adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                                   Map<String, FeatureValue> currentFeatures) {
        PlanCbrCase past = retrieved.cbrCase();
        double currentFollowUp = numericFeature(currentFeatures, "followUpIntervalDays");
        double pastFollowUp = numericFeature(past.features(), "followUpIntervalDays");
        String currentProvider = stringFeature(currentFeatures, "providerType");
        String pastProvider = stringFeature(past.features(), "providerType");
        String currentCondition = stringFeature(currentFeatures, "conditionCategory");
        String pastCondition = stringFeature(past.features(), "conditionCategory");

        List<AdaptedStep> steps = new ArrayList<>();
        for (PlanTrace trace : past.planTrace()) {
            Map<String, Object> params = new LinkedHashMap<>(trace.parameters());
            int priority = trace.priority();
            AdaptationAction action = AdaptationAction.RETAINED;
            String reason = null;

            int scaled = SeverityScaling.scale(pastFollowUp > 0 ? 1.0 / pastFollowUp : 0,
                    currentFollowUp > 0 ? 1.0 / currentFollowUp : 0, priority);
            if (scaled > priority) {
                priority = scaled;
                action = AdaptationAction.BOOSTED;
                reason = "Shorter follow-up interval (%.0f → %.0f days)".formatted(
                        pastFollowUp, currentFollowUp);
                if (currentFollowUp > 0) {
                    params.put("followUpIntervalDays", (int) currentFollowUp);
                }
            }

            if (!pastProvider.isEmpty() && !currentProvider.isEmpty()
                    && !pastProvider.equals(currentProvider)) {
                reason = (reason != null ? reason + "; " : "")
                        + "Provider type changed (%s → %s) — review applicability".formatted(
                        pastProvider, currentProvider);
            }

            if ("pre-visit-prep".equals(trace.capabilityName())
                    && !pastCondition.isEmpty() && !currentCondition.isEmpty()
                    && !pastCondition.equals(currentCondition)) {
                reason = (reason != null ? reason + "; " : "")
                        + "Condition changed (%s → %s) — adjust preparation".formatted(
                        pastCondition, currentCondition);
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
