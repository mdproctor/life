package io.casehub.life.app.cbr.adapt;

import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthAdaptationRuleTest {

    private final HealthAdaptationRule rule = new HealthAdaptationRule();

    @Test
    void caseType() {
        assertEquals("care-coordination", rule.caseType());
    }

    @Test
    void higherRisk_boostsPriority() {
        var scored = scored(Map.of(
                "patientRiskLevel", FeatureValue.number(3.0),
                "careType", FeatureValue.string("general")));
        Map<String, FeatureValue> current = Map.of(
                "patientRiskLevel", FeatureValue.number(8.0),
                "careType", FeatureValue.string("general"));
        var steps = rule.adapt(scored, current);
        var step = steps.getFirst();
        assertEquals(AdaptationAction.BOOSTED, step.action());
        assertTrue(step.reason().contains("Higher patient risk"));
    }

    @Test
    void slaBreach_boostsHealthCheck() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                Map.of("patientRiskLevel", FeatureValue.number(5.0)),
                List.of(new PlanTrace("b1", "health-check", "w1", "breach-occurred", 5, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        Map<String, FeatureValue> current = Map.of("patientRiskLevel", FeatureValue.number(5.0));
        var steps = rule.adapt(scored, current);
        assertEquals(AdaptationAction.BOOSTED, steps.getFirst().action());
        assertTrue(steps.getFirst().reason().contains("SLA breach"));
    }

    @Test
    void careTypeChanged_annotates() {
        var scored = scored(Map.of(
                "patientRiskLevel", FeatureValue.number(5.0),
                "careType", FeatureValue.string("palliative")));
        Map<String, FeatureValue> current = Map.of(
                "patientRiskLevel", FeatureValue.number(5.0),
                "careType", FeatureValue.string("rehabilitative"));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.getFirst().reason().contains("Care type changed"));
    }

    @Test
    void noFeatures_retainsAll() {
        var scored = scored(Map.of());
        assertTrue(rule.adapt(scored, Map.of()).stream()
                .allMatch(s -> s.action() == AdaptationAction.RETAINED));
    }

    @Test
    void emptyTrace_returnsEmpty() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9, Map.of(), List.of());
        assertTrue(rule.adapt(new ScoredCbrCase<>(past, "c1", 0.8), Map.of()).isEmpty());
    }

    @Test
    void lowActorTrust_flagsProviderReview() {
        var scored = scored(Map.of(
                "patientRiskLevel", FeatureValue.number(5.0),
                "careType", FeatureValue.string("general")));
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "patientRiskLevel", FeatureValue.number(5.0),
                "careType", FeatureValue.string("general"),
                "actorTrustScore", FeatureValue.number(0.2)));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.stream().anyMatch(s -> s.reason() != null && s.reason().toLowerCase().contains("trust")));
    }

    @Test
    void lowFactualAccuracy_boostsHealthCheck() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                                   Map.of("patientRiskLevel", FeatureValue.number(5.0)),
                                   List.of(new PlanTrace("b1", "health-check", "w1", "ok", 5, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.85);
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "patientRiskLevel", FeatureValue.number(5.0),
                "actorFactualAccuracy", FeatureValue.number(0.3)));
        var steps       = rule.adapt(scored, current);
        var healthCheck = steps.getFirst();
        assertEquals(AdaptationAction.BOOSTED, healthCheck.action());
        assertTrue(healthCheck.reason().toLowerCase().contains("factual accuracy"));
    }


    private ScoredCbrCase<PlanCbrCase> scored(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "needs-assessment", "w1", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }
}
