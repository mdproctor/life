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

class FinancialAdaptationRuleTest {

    private final FinancialAdaptationRule rule = new FinancialAdaptationRule();

    @Test
    void caseType() {
        assertEquals("financial-review", rule.caseType());
    }

    @Test
    void significantlyHigherAmount_boostsEscalation() {
        var scored = scored(Map.of("amount", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = Map.of("amount", FeatureValue.number(2000));
        var steps = rule.adapt(scored, current);
        var escalateStep = steps.stream()
                .filter(s -> "escalate-anomalies".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.BOOSTED, escalateStep.action());
        assertTrue(escalateStep.reason().contains("significantly higher"));
    }

    @Test
    void moderateIncrease_retains() {
        var scored = scored(Map.of("amount", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = Map.of("amount", FeatureValue.number(1200));
        var steps = rule.adapt(scored, current);
        var escalateStep = steps.stream()
                .filter(s -> "escalate-anomalies".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.RETAINED, escalateStep.action());
    }

    @Test
    void pastEscalation_flagsMiscalibration() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                Map.of("amount", FeatureValue.number(1000)),
                List.of(new PlanTrace("b1", "escalate-anomalies", "w1", "escalated-to-admin", 5, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        Map<String, FeatureValue> current = Map.of("amount", FeatureValue.number(1000));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.getFirst().reason().contains("miscalibrated"));
    }

    @Test
    void amountDelta_addsRatio() {
        var scored = scored(Map.of("amount", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = Map.of("amount", FeatureValue.number(1300));
        var steps = rule.adapt(scored, current);
        assertEquals(1.3, steps.getFirst().parameters().get("amountRatio"));
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
                        List.of(new PlanTrace("b1", "gather-data", "w1", "ok", 5, Map.of()),
                                new PlanTrace("b2", "escalate-anomalies", "w2", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }
}
