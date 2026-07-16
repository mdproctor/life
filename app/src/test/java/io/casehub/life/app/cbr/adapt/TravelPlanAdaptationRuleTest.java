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

class TravelPlanAdaptationRuleTest {

    private final TravelPlanAdaptationRule rule = new TravelPlanAdaptationRule();

    @Test
    void caseType() {
        assertEquals("travel-plan", rule.caseType());
    }

    @Test
    void budgetDelta_scalesRatio() {
        var scored = scored(Map.of(
                "budget", FeatureValue.number(2000),
                "season", FeatureValue.string("summer")));
        Map<String, FeatureValue> current = Map.of(
                "budget", FeatureValue.number(3000),
                "season", FeatureValue.string("summer"));
        var steps = rule.adapt(scored, current);
        assertEquals(1.5, steps.getFirst().parameters().get("budgetRatio"));
        assertTrue(steps.getFirst().reason().contains("50%"));
    }

    @Test
    void seasonChanged_annotates() {
        var scored = scored(Map.of(
                "budget", FeatureValue.number(2000),
                "season", FeatureValue.string("summer")));
        Map<String, FeatureValue> current = Map.of(
                "budget", FeatureValue.number(2000),
                "season", FeatureValue.string("winter"));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.getFirst().reason().contains("Season changed"));
    }

    @Test
    void rejectedBooking_suppresses() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                Map.of("budget", FeatureValue.number(2000)),
                List.of(new PlanTrace("b1", "booking", "w1", "rejected-by-airline", 5, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        Map<String, FeatureValue> current = Map.of("budget", FeatureValue.number(2000));
        var steps = rule.adapt(scored, current);
        assertEquals(AdaptationAction.SUPPRESSED, steps.getFirst().action());
        assertTrue(steps.getFirst().reason().contains("rejected"));
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

    private ScoredCbrCase<PlanCbrCase> scored(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "destination-research", "w1", "ok", 5, Map.of()),
                                new PlanTrace("b2", "booking", "w2", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }
}
