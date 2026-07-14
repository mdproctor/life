package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.life.api.CbrSuggestions;
import io.casehub.life.api.LifeCaseType;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LifeCbrSuggestionServiceTest {

    private LifeCbrFeatureExtractor featureExtractor;
    private CbrCaseMemoryStore cbrStore;
    private LifeCbrSuggestionService service;

    @BeforeEach
    void setup() {
        featureExtractor = mock(LifeCbrFeatureExtractor.class);
        cbrStore = mock(CbrCaseMemoryStore.class);
        service = new LifeCbrSuggestionService(featureExtractor, cbrStore, new ObjectMapper());
    }

    @Test
    void suggest_noExtraction_returnsEmpty() {
        when(featureExtractor.extract(eq("travel-plan"), any())).thenReturn(Optional.empty());
        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void suggest_fewerThanTwoResults_returnsEmpty() {
        var config = mockConfig();
        when(featureExtractor.extract(eq("travel-plan"), any()))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("budget", FeatureValue.number(2000)))));
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(scoredCase(2000, "COMPLETED", 0.8)));
        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void suggest_multipleResults_computesStatistics() {
        var config = mockConfig();
        when(featureExtractor.extract(eq("travel-plan"), any()))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("budget", FeatureValue.number(2000)))));
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(
                        scoredCase(1500, "COMPLETED", 0.9),
                        scoredCase(2000, "COMPLETED", 0.85),
                        scoredCase(2500, "FAULTED", 0.7)));

        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertFalse(result.isEmpty());
        assertEquals(3, result.experienceCount());
        assertEquals(2.0 / 3.0, result.historicalSuccessRate(), 0.001);

        var budgetStats = result.featureStats().get("budget");
        assertNotNull(budgetStats);
        assertEquals(1500.0, budgetStats.min());
        assertEquals(2500.0, budgetStats.max());
        assertEquals(3, budgetStats.sampleCount());
    }

    @Test
    void suggest_categoricalFeaturesExcluded() {
        var config = mockConfig();
        when(featureExtractor.extract(eq("travel-plan"), any()))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("budget", FeatureValue.number(2000)))));
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(
                        scoredCaseWithStringFeature("Barcelona", 1500, 0.9),
                        scoredCaseWithStringFeature("Madrid", 2000, 0.8)));

        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertNull(result.featureStats().get("destination"));
        assertNotNull(result.featureStats().get("budget"));
    }

    @Test
    void suggest_exceptionReturnsEmpty() {
        when(featureExtractor.extract(any(), any())).thenThrow(new RuntimeException("Qdrant down"));
        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void suggest_averageSimilarity_computed() {
        var config = mockConfig();
        when(featureExtractor.extract(eq("travel-plan"), any()))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("budget", FeatureValue.number(2000)))));
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(
                        scoredCase(1500, "COMPLETED", 0.9),
                        scoredCase(2000, "COMPLETED", 0.7)));

        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertEquals(0.8, result.averageSimilarity(), 0.001);
    }

    @Test
    void suggest_allCompleted_successRateIsOne() {
        var config = mockConfig();
        when(featureExtractor.extract(eq("travel-plan"), any()))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("budget", FeatureValue.number(2000)))));
        when(cbrStore.retrieveSimilar(any(), eq(PlanCbrCase.class)))
                .thenReturn(List.of(
                        scoredCase(1500, "COMPLETED", 0.9),
                        scoredCase(2000, "COMPLETED", 0.8)));

        var result = service.suggest(LifeCaseType.TRAVEL_PLAN, Map.of());
        assertEquals(1.0, result.historicalSuccessRate(), 0.001);
    }

    private CbrConfig mockConfig() {
        var config = mock(CbrConfig.class);
        when(config.topK()).thenReturn(5);
        when(config.minSimilarity()).thenReturn(0.3);
        when(config.vectorWeight()).thenReturn(0.0);
        when(config.domain()).thenReturn("casehubio/life/travel");
        when(config.weights()).thenReturn(Map.of("budget", 1.5));
        return config;
    }

    private ScoredCbrCase<PlanCbrCase> scoredCase(double budget, String outcome, double score) {
        var cbrCase = new PlanCbrCase("problem", "solution", outcome, null,
                Map.of("budget", FeatureValue.number(budget)), List.of());
        return new ScoredCbrCase<>(cbrCase, score);
    }

    private ScoredCbrCase<PlanCbrCase> scoredCaseWithStringFeature(String dest, double budget, double score) {
        var cbrCase = new PlanCbrCase("problem", "solution", "COMPLETED", null,
                Map.of("destination", FeatureValue.string(dest),
                       "budget", FeatureValue.number(budget)),
                List.of());
        return new ScoredCbrCase<>(cbrCase, score);
    }
}
