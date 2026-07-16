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

class AppointmentCycleAdaptationRuleTest {

    private final AppointmentCycleAdaptationRule rule = new AppointmentCycleAdaptationRule();

    @Test
    void caseType() {
        assertEquals("appointment-cycle", rule.caseType());
    }

    @Test
    void shorterFollowUp_boostsPriority() {
        var scored = scored(Map.of(
                "followUpIntervalDays", FeatureValue.number(30),
                "providerType", FeatureValue.string("GP")));
        Map<String, FeatureValue> current = Map.of(
                "followUpIntervalDays", FeatureValue.number(7),
                "providerType", FeatureValue.string("GP"));
        var steps = rule.adapt(scored, current);
        var step = steps.getFirst();
        assertEquals(AdaptationAction.BOOSTED, step.action());
        assertTrue(step.reason().contains("Shorter follow-up"));
        assertEquals(7, step.parameters().get("followUpIntervalDays"));
    }

    @Test
    void providerChanged_annotates() {
        var scored = scored(Map.of(
                "followUpIntervalDays", FeatureValue.number(14),
                "providerType", FeatureValue.string("GP")));
        Map<String, FeatureValue> current = Map.of(
                "followUpIntervalDays", FeatureValue.number(14),
                "providerType", FeatureValue.string("Specialist"));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.getFirst().reason().contains("Provider type changed"));
    }

    @Test
    void conditionChanged_adjustsPrep() {
        var scored = scoredWithPrep(Map.of(
                "conditionCategory", FeatureValue.string("cardiology")));
        Map<String, FeatureValue> current = Map.<String, FeatureValue>of(
                "conditionCategory", FeatureValue.string("neurology"));
        var steps = rule.adapt(scored, current);
        var prepStep = steps.stream()
                .filter(s -> "pre-visit-prep".equals(s.capabilityName())).findFirst().orElseThrow();
        assertTrue(prepStep.reason().contains("Condition changed"));
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
                        List.of(new PlanTrace("b1", "book-appointment", "w1", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }

    private ScoredCbrCase<PlanCbrCase> scoredWithPrep(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "book-appointment", "w1", "ok", 5, Map.of()),
                                new PlanTrace("b2", "pre-visit-prep", "w2", "ok", 3, Map.of()))),
                "case-1", 0.85);
    }
}
