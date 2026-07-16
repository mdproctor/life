package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanAdapter;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.All;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Alternative
@Priority(10)
public class LifePlanAdapter implements PlanAdapter {

    private static final Logger LOG = Logger.getLogger(LifePlanAdapter.class);

    private final Map<String, LifeAdaptationRule> rulesByType;
    private final Map<String, LifeAdaptationRule> rulesByCapability;

    @Inject
    public LifePlanAdapter(@All List<LifeAdaptationRule> rules) {
        this.rulesByType = new LinkedHashMap<>();
        this.rulesByCapability = new LinkedHashMap<>();
        for (var rule : rules) {
            var prev = rulesByType.put(rule.caseType(), rule);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate LifeAdaptationRule for caseType '" + rule.caseType()
                        + "': " + prev.getClass().getName() + " and " + rule.getClass().getName());
            }
            for (var cap : rule.knownCapabilities()) {
                var prevCap = rulesByCapability.put(cap, rule);
                if (prevCap != null) {
                    throw new IllegalStateException(
                            "Duplicate capability '" + cap + "': "
                            + prevCap.getClass().getName() + " and " + rule.getClass().getName());
                }
            }
        }
    }

    @Override
    public AdaptedPlan adapt(ScoredCbrCase<PlanCbrCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        String inferred = inferCaseType(retrieved.cbrCase().planTrace());
        return adapt(inferred, retrieved, currentFeatures);
    }

    public AdaptedPlan adapt(String caseType,
                             ScoredCbrCase<PlanCbrCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        if (retrieved.cbrCase().planTrace().isEmpty()) {
            return new AdaptedPlan(List.of());
        }
        LifeAdaptationRule rule = rulesByType.get(caseType);
        if (rule == null) {
            return retainAll(retrieved);
        }
        List<AdaptedStep> steps = rule.adapt(retrieved, currentFeatures);
        return new AdaptedPlan(steps);
    }

    private String inferCaseType(List<PlanTrace> traces) {
        for (var trace : traces) {
            LifeAdaptationRule rule = rulesByCapability.get(trace.capabilityName());
            if (rule != null) {
                return rule.caseType();
            }
        }
        return "";
    }

    private AdaptedPlan retainAll(ScoredCbrCase<PlanCbrCase> retrieved) {
        return new AdaptedPlan(
                retrieved.cbrCase().planTrace().stream()
                        .map(t -> new AdaptedStep(t.bindingName(), t.capabilityName(),
                                t.workerName(), t.stepOutcome(), t.priority(),
                                t.parameters(), AdaptationAction.RETAINED, null))
                        .toList());
    }
}
