package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifePlanAdapterTest {

    @Test
    void dispatch_knownCaseType_delegatesToRule() {
        var rule = testRule("contractor-coordination", Set.of("request-quote"),
                (r, f) -> List.of(new AdaptedStep("b1", "request-quote", "w1",
                        "ok", 8, Map.of("slaHours", 24),
                        AdaptationAction.BOOSTED, "winter urgency")));
        var adapter = new LifePlanAdapter(List.of(rule));
        var scored = scoredCase("request-quote");
        var result = adapter.adapt("contractor-coordination", scored, Map.of());
        assertEquals(1, result.steps().size());
        assertEquals(AdaptationAction.BOOSTED, result.steps().getFirst().action());
        assertEquals("winter urgency", result.steps().getFirst().reason());
    }

    @Test
    void dispatch_unknownCaseType_retainsAll() {
        var adapter = new LifePlanAdapter(List.of());
        var scored = scoredCase("some-capability");
        var result = adapter.adapt("unknown-type", scored, Map.of());
        assertEquals(1, result.steps().size());
        assertEquals(AdaptationAction.RETAINED, result.steps().getFirst().action());
    }

    @Test
    void spiMethod_infersFromCapabilities() {
        var rule = testRule("contractor-coordination", Set.of("request-quote"),
                (r, f) -> List.of(new AdaptedStep("b1", "request-quote", "w1",
                        "ok", 8, Map.of(), AdaptationAction.BOOSTED, "test")));
        var adapter = new LifePlanAdapter(List.of(rule));
        var scored = scoredCase("request-quote");
        var result = adapter.adapt(scored, Map.of());
        assertEquals(AdaptationAction.BOOSTED, result.steps().getFirst().action());
    }

    @Test
    void spiMethod_noCapabilityMatch_retainsAll() {
        var rule = testRule("contractor-coordination", Set.of("request-quote"),
                (r, f) -> List.of());
        var adapter = new LifePlanAdapter(List.of(rule));
        var scored = scoredCase("unknown-capability");
        var result = adapter.adapt(scored, Map.of());
        assertEquals(1, result.steps().size());
        assertEquals(AdaptationAction.RETAINED, result.steps().getFirst().action());
    }

    @Test
    void duplicateCaseType_throwsAtConstruction() {
        var rule1 = testRule("same-type", Set.of("cap-a"), (r, f) -> List.of());
        var rule2 = testRule("same-type", Set.of("cap-b"), (r, f) -> List.of());
        assertThrows(IllegalStateException.class,
                () -> new LifePlanAdapter(List.of(rule1, rule2)));
    }

    @Test
    void emptyPlanTrace_returnsEmptySteps() {
        var adapter = new LifePlanAdapter(List.of());
        var scored = new ScoredCbrCase<>(
                new PlanCbrCase("p", "s", "COMPLETED", 0.9,
                        Map.of(), List.of()),
                "case-1", 0.85);
        var result = adapter.adapt("any", scored, Map.of());
        assertTrue(result.steps().isEmpty());
    }

    @Test
    void multipleRules_eachRoutedCorrectly() {
        var contractorRule = testRule("contractor-coordination", Set.of("request-quote"),
                (r, f) -> List.of(new AdaptedStep("b1", "request-quote", "w1",
                        "ok", 8, Map.of(), AdaptationAction.BOOSTED, "contractor")));
        var healthRule = testRule("care-coordination", Set.of("care-assessment"),
                (r, f) -> List.of(new AdaptedStep("b1", "care-assessment", "w1",
                        "ok", 5, Map.of(), AdaptationAction.RETAINED, "health")));
        var adapter = new LifePlanAdapter(List.of(contractorRule, healthRule));

        var contractorResult = adapter.adapt("contractor-coordination",
                scoredCase("request-quote"), Map.of());
        assertEquals("contractor", contractorResult.steps().getFirst().reason());

        var healthResult = adapter.adapt("care-coordination",
                scoredCase("care-assessment"), Map.of());
        assertEquals("health", healthResult.steps().getFirst().reason());
    }

    @Test
    void duplicateCapability_throwsAtConstruction() {
        var rule1 = testRule("type-a", Set.of("shared-cap", "cap-a"), (r, f) -> List.of());
        var rule2 = testRule("type-b", Set.of("shared-cap", "cap-b"), (r, f) -> List.of());
        assertThrows(IllegalStateException.class,
                () -> new LifePlanAdapter(List.of(rule1, rule2)));
    }

    @Test
    void noCapabilityOverlap_acrossAllRules() {
        var rules = List.of(
                new io.casehub.life.app.cbr.adapt.ContractorAdaptationRule(),
                new io.casehub.life.app.cbr.adapt.HealthAdaptationRule(),
                new io.casehub.life.app.cbr.adapt.HomeMaintenanceAdaptationRule(),
                new io.casehub.life.app.cbr.adapt.FinancialAdaptationRule(),
                new io.casehub.life.app.cbr.adapt.AppointmentCycleAdaptationRule(),
                new io.casehub.life.app.cbr.adapt.TravelPlanAdaptationRule());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var rule : rules) {
            for (var cap : rule.knownCapabilities()) {
                assertTrue(seen.add(cap),
                        "Duplicate capability '" + cap + "' in " + rule.getClass().getSimpleName());
            }
        }
    }

    // --- helpers ---

    static ScoredCbrCase<PlanCbrCase> scoredCase(String capabilityName) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9,
                        Map.of("budget", FeatureValue.number(1000)),
                        List.of(new PlanTrace("b1", capabilityName, "w1", "ok", 5, Map.of()))),
                "case-1", 0.85);
    }

    static LifeAdaptationRule testRule(String caseType, Set<String> capabilities,
            java.util.function.BiFunction<ScoredCbrCase<PlanCbrCase>,
                    Map<String, FeatureValue>, List<AdaptedStep>> fn) {
        return new LifeAdaptationRule() {
            @Override public String caseType() { return caseType; }
            @Override public Set<String> knownCapabilities() { return capabilities; }
            @Override public List<AdaptedStep> adapt(
                    ScoredCbrCase<PlanCbrCase> r, Map<String, FeatureValue> f) {
                return fn.apply(r, f);
            }
        };
    }
}
