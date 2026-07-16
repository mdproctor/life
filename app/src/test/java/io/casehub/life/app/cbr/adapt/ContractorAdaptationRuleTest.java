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

class ContractorAdaptationRuleTest {

    private final ContractorAdaptationRule rule = new ContractorAdaptationRule();

    @Test
    void caseType() {
        assertEquals("contractor-coordination", rule.caseType());
    }

    @Test
    void winterHeating_boostsRequestQuote_tightensSla() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("plumbing"),
                "budget", FeatureValue.number(2000),
                "slaHours", FeatureValue.number(168)));
        Map<String, FeatureValue> current = Map.of(
                "season", FeatureValue.string("winter"),
                "problemType", FeatureValue.string("heating"),
                "budget", FeatureValue.number(2000));
        var steps = rule.adapt(scored, current);
        var quoteStep = steps.stream()
                .filter(s -> "request-quote".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.BOOSTED, quoteStep.action());
        assertTrue(quoteStep.reason().contains("Winter"));
        assertEquals(84, quoteStep.parameters().get("slaHours"));
    }

    @Test
    void summerPlumbing_noBoost() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("plumbing"),
                "budget", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("plumbing"),
                "budget", FeatureValue.number(1000));
        var steps = rule.adapt(scored, current);
        var quoteStep = steps.stream()
                .filter(s -> "request-quote".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.RETAINED, quoteStep.action());
    }

    @Test
    void budgetDelta_scalesRatio() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("painting"),
                "budget", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("painting"),
                "budget", FeatureValue.number(1500));
        var steps = rule.adapt(scored, current);
        var step = steps.getFirst();
        assertEquals(AdaptationAction.RETAINED, step.action());
        assertEquals(1.5, step.parameters().get("budgetRatio"));
        assertTrue(step.reason().contains("50%"));
    }

    @Test
    void failedOutcome_suppressesAll() {
        var past = new PlanCbrCase("p", "s", "FAILED", 0.5,
                Map.of("budget", FeatureValue.number(1000)),
                List.of(
                        new PlanTrace("b1", "request-quote", "w1", "ok", 5, Map.of()),
                        new PlanTrace("b2", "contractor-sentinel", "w2", "ok", 3, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        var steps = rule.adapt(scored, Map.<String, FeatureValue>of("budget", FeatureValue.number(1000)));
        assertEquals(AdaptationAction.SUPPRESSED, steps.get(0).action());
        assertEquals(AdaptationAction.RETAINED, steps.get(1).action());
    }

    @Test
    void missingFeatures_retainsAll() {
        var scored = scored(Map.of());
        var steps = rule.adapt(scored, Map.of());
        assertTrue(steps.stream().allMatch(s -> s.action() == AdaptationAction.RETAINED));
    }

    @Test
    void emptyPlanTrace_returnsEmpty() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                Map.of(), List.of());
        var scored = new ScoredCbrCase<>(past, "c1", 0.8);
        assertTrue(rule.adapt(scored, Map.of()).isEmpty());
    }

    @Test
    void lowActorTrust_flagsSteps() {
        var scored = scored(Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("plumbing"),
                "budget", FeatureValue.number(1000)));
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "season", FeatureValue.string("summer"),
                "problemType", FeatureValue.string("plumbing"),
                "budget", FeatureValue.number(1000),
                "actorTrustScore", FeatureValue.number(0.2)));
        var steps = rule.adapt(scored, current);
        assertTrue(steps.stream().anyMatch(s -> s.reason() != null && s.reason().toLowerCase().contains("trust")));
    }

    @Test
    void lowDeadlineReliability_boostsWatchdog() {
        var past = new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                                   Map.of("budget", FeatureValue.number(1000)),
                                   List.of(new PlanTrace("b1", "request-quote", "w1", "ok", 5, Map.of()),
                                           new PlanTrace("b2", "watchdog-escalation", "w2", "ok", 3, Map.of())));
        var scored = new ScoredCbrCase<>(past, "c1", 0.85);
        Map<String, FeatureValue> current = new java.util.LinkedHashMap<>(Map.of(
                "budget", FeatureValue.number(1000),
                "actorDeadlineReliability", FeatureValue.number(0.3)));
        var steps = rule.adapt(scored, current);
        var watchdog = steps.stream()
                            .filter(s -> "watchdog-escalation".equals(s.capabilityName())).findFirst().orElseThrow();
        assertEquals(AdaptationAction.BOOSTED, watchdog.action());
        assertTrue(watchdog.reason().toLowerCase().contains("deadline"));
    }


    private ScoredCbrCase<PlanCbrCase> scored(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "request-quote", "w1", "ok", 5, Map.of()),
                                new PlanTrace("b2", "job-monitoring", "w2", "ok", 3, Map.of()))),
                "case-1", 0.85);
    }
}
