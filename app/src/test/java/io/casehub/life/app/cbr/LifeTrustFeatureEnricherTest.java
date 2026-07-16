package io.casehub.life.app.cbr;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeTrustFeatureEnricherTest {

    @Mock
    TrustGateService trustGateService;

    LifeTrustFeatureEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new LifeTrustFeatureEnricher(trustGateService);
    }

    @Test
    void noExternalActorId_returnsOriginalFeatures() {
        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(1000));
        Map<String, Object> context = Map.of("description", "test");

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertSame(features, result);
        verify(trustGateService, never()).currentScore(anyString());
    }

    @Test
    void actorWithAllScores_enrichesAllDimensions() {
        UUID actorUuid = UUID.randomUUID();
        String actorId = "life-actor:" + actorUuid;

        when(trustGateService.currentScore(actorId)).thenReturn(OptionalDouble.of(0.85));
        when(trustGateService.dimensionScore(actorId, "deadline-reliability"))
                .thenReturn(OptionalDouble.of(0.9));
        when(trustGateService.dimensionScore(actorId, "cost-accuracy"))
                .thenReturn(OptionalDouble.of(0.7));
        when(trustGateService.dimensionScore(actorId, "factual-accuracy"))
                .thenReturn(OptionalDouble.of(0.6));
        when(trustGateService.dimensionScore(actorId, "proactive-alerting"))
                .thenReturn(OptionalDouble.of(0.8));

        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(1000));
        Map<String, Object> context = Map.of("externalActorId", actorUuid.toString());

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertEquals(6, result.size());
        assertEquals(1000.0, ((FeatureValue.NumberVal) result.get("budget")).value());
        assertEquals(0.85, ((FeatureValue.NumberVal) result.get("actorTrustScore")).value());
        assertEquals(0.9, ((FeatureValue.NumberVal) result.get("actorDeadlineReliability")).value());
        assertEquals(0.7, ((FeatureValue.NumberVal) result.get("actorCostAccuracy")).value());
        assertEquals(0.6, ((FeatureValue.NumberVal) result.get("actorFactualAccuracy")).value());
        assertEquals(0.8, ((FeatureValue.NumberVal) result.get("actorProactiveAlerting")).value());
    }

    @Test
    void actorWithPartialScores_enrichesOnlyPresent() {
        UUID actorUuid = UUID.randomUUID();
        String actorId = "life-actor:" + actorUuid;

        when(trustGateService.currentScore(actorId)).thenReturn(OptionalDouble.of(0.6));
        when(trustGateService.dimensionScore(actorId, "deadline-reliability"))
                .thenReturn(OptionalDouble.of(0.4));
        when(trustGateService.dimensionScore(actorId, "cost-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "factual-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "proactive-alerting"))
                .thenReturn(OptionalDouble.empty());

        Map<String, FeatureValue> features = Map.of("season", FeatureValue.string("winter"));
        Map<String, Object> context = Map.of("externalActorId", actorUuid.toString());

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertEquals(3, result.size());
        assertTrue(result.containsKey("season"));
        assertTrue(result.containsKey("actorTrustScore"));
        assertTrue(result.containsKey("actorDeadlineReliability"));
        assertFalse(result.containsKey("actorCostAccuracy"));
        assertFalse(result.containsKey("actorFactualAccuracy"));
    }

    @Test
    void actorWithNoScores_returnsOriginalFeatures() {
        UUID actorUuid = UUID.randomUUID();
        String actorId = "life-actor:" + actorUuid;

        when(trustGateService.currentScore(actorId)).thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "deadline-reliability"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "cost-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "factual-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "proactive-alerting"))
                .thenReturn(OptionalDouble.empty());

        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(500));
        Map<String, Object> context = Map.of("externalActorId", actorUuid.toString());

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertSame(features, result);
    }

    @Test
    void preservesOriginalFeatures() {
        UUID actorUuid = UUID.randomUUID();
        String actorId = "life-actor:" + actorUuid;

        when(trustGateService.currentScore(actorId)).thenReturn(OptionalDouble.of(0.5));
        when(trustGateService.dimensionScore(actorId, "deadline-reliability"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "cost-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "factual-accuracy"))
                .thenReturn(OptionalDouble.empty());
        when(trustGateService.dimensionScore(actorId, "proactive-alerting"))
                .thenReturn(OptionalDouble.empty());

        Map<String, FeatureValue> features = new LinkedHashMap<>();
        features.put("budget", FeatureValue.number(2000));
        features.put("season", FeatureValue.string("summer"));

        Map<String, Object> context = Map.of("externalActorId", actorUuid.toString());

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertEquals(2000.0, ((FeatureValue.NumberVal) result.get("budget")).value());
        assertEquals("summer", ((FeatureValue.StringVal) result.get("season")).value());
        assertEquals(0.5, ((FeatureValue.NumberVal) result.get("actorTrustScore")).value());
    }

    @Test
    void nullExternalActorId_returnsOriginalFeatures() {
        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(1000));
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("externalActorId", null);

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertSame(features, result);
    }

    @Test
    void malformedExternalActorId_returnsOriginalFeatures() {
        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(1000));
        Map<String, Object>       context  = Map.of("externalActorId", "not-a-uuid");

        Map<String, FeatureValue> result = enricher.enrich(features, context);

        assertSame(features, result);
        verify(trustGateService, never()).currentScore(anyString());
    }

}
