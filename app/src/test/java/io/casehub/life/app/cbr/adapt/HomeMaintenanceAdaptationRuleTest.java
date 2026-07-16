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

class HomeMaintenanceAdaptationRuleTest {

    private final HomeMaintenanceAdaptationRule rule = new HomeMaintenanceAdaptationRule();

    @Test
    void caseType() {
        assertEquals("home-maintenance", rule.caseType());
    }

    @Test
    void winterHeating_halvesSla() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "issueType", FeatureValue.string("general"),
                "resolutionDays", FeatureValue.number(14)));
        Map<String, FeatureValue> current = Map.of(
                "season", FeatureValue.string("winter"),
                "issueType", FeatureValue.string("heating"));
        var steps = rule.adapt(scored, current);
        var step = steps.getFirst();
        assertEquals(AdaptationAction.BOOSTED, step.action());
        assertEquals(7, step.parameters().get("resolutionDays"));
        assertTrue(step.reason().contains("Winter"));
    }

    @Test
    void costDelta_addsRatio() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "issueType", FeatureValue.string("painting"),
                "estimatedCost", FeatureValue.number(500)));
        Map<String, FeatureValue> current = Map.of(
                "season", FeatureValue.string("summer"),
                "issueType", FeatureValue.string("painting"),
                "estimatedCost", FeatureValue.number(750));
        var steps = rule.adapt(scored, current);
        assertEquals(1.5, steps.getFirst().parameters().get("costRatio"));
    }

    @Test
    void failedOutcome_suppresses() {
        var past = new PlanCbrCase("p", "s", "FAILED", 0.5,
                Map.of(), List.of(
                        new PlanTrace("b1", "schedule-inspection", "w1", "ok", 5, Map.of()),
                        new PlanTrace("b2", "maintenance-sentinel", "w2", "ok", 3, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        var steps = rule.adapt(scored, Map.of());
        assertEquals(AdaptationAction.SUPPRESSED, steps.get(0).action());
        assertEquals(AdaptationAction.RETAINED, steps.get(1).action());
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
    void lowActorTrust_flagsSteps() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "issueType", FeatureValue.string("painting"),
                "estimatedCost", FeatureValue.number(500)));
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "season", FeatureValue.string("summer"),
                "issueType", FeatureValue.string("painting"),
                "estimatedCost", FeatureValue.number(500),
                "actorTrustScore", FeatureValue.number(0.2)));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.stream().anyMatch(s -> s.reason() != null && s.reason().toLowerCase().contains("trust")));
    }

    @Test
    void lowDeadlineReliability_boostsSentinel() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                                   Map.of("estimatedCost", FeatureValue.number(1000)),
                                   List.of(new PlanTrace("b1", "schedule-inspection", "w1", "ok", 5, Map.of()),
                                           new PlanTrace("b2", "maintenance-sentinel", "w2", "ok", 3, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.85);
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "estimatedCost", FeatureValue.number(1000),
                "actorDeadlineReliability", FeatureValue.number(0.3)));
        var steps = rule.adapt(scored, current);
        var sentinel = steps.stream()
                            .filter(s -> "maintenance-sentinel".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.BOOSTED, sentinel.action());
        assertTrue(sentinel.reason().toLowerCase().contains("deadline"));
    }


    private ScoredCbrCase<PlanCbrCase> scored(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "schedule-inspection", "w1", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }
}
