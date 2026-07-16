package io.casehub.life.app.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.life.api.CbrSuggestions;
import io.casehub.life.api.FeatureStatistics;
import io.casehub.life.api.LifeCaseType;
import io.casehub.life.api.request.CreateLifeCaseRequest;
import io.casehub.life.app.cbr.LifeCbrRetrievalResult;
import io.casehub.life.app.cbr.LifeCbrSuggestionService;
import io.casehub.life.app.cbr.LifePlanAdapter;
import io.casehub.life.app.cbr.LifeTrustFeatureEnricher;
import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.CbrAdaptationRecorded;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeCaseServiceCbrTest {

    @Mock LifeCbrSuggestionService cbrSuggestionService;
    @Mock LifeTrustFeatureEnricher trustFeatureEnricher;
    @Mock LifePlanAdapter planAdapter;
    @Mock CaseHubRuntime caseHubRuntime;
    @Mock @SuppressWarnings("rawtypes") Event adaptationEvent;
    @Mock Instance<LifeTypedCaseHub> caseHubs;
    @Mock LifeTypedCaseHub mockCaseHub;
    @Captor ArgumentCaptor<CbrAdaptationRecorded> eventCaptor;
    @Captor ArgumentCaptor<Map<String, Object>> contextCaptor;

    ObjectMapper objectMapper = new ObjectMapper();
    LifeCaseService service;
    UUID engineCaseId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        service = spy(new LifeCaseService());

        setField("cbrSuggestionService", cbrSuggestionService);
        setField("caseHubRuntime", caseHubRuntime);
        setField("objectMapper", objectMapper);
        setField("planAdapter", planAdapter);
        setField("trustFeatureEnricher", trustFeatureEnricher);
        setField("adaptationEvent", adaptationEvent);
        setField("caseHubs", caseHubs);

        when(mockCaseHub.lifeCaseType()).thenReturn(LifeCaseType.CONTRACTOR_COORDINATION);
        when(caseHubs.stream()).thenAnswer(inv -> java.util.stream.Stream.of(mockCaseHub));
        when(mockCaseHub.startCase(any()))
                .thenReturn(CompletableFuture.completedFuture(engineCaseId).minimalCompletionStage());

        doReturn(new HashMap<>(Map.of("lifeCaseType", "contractor-coordination")))
                .when(service).prepareAndTrack(any(), any());
        doNothing().when(service).persistCaseId(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_withCbrCases_writesCbrCalibrationAndAdaptedPlan() {
        var retrieval = retrievalWithCases();
        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(retrieval);
        when(trustFeatureEnricher.enrich(any(), any())).thenReturn(retrieval.currentFeatures());
        when(planAdapter.adapt(eq("contractor-coordination"), any(), any()))
                .thenReturn(adaptedPlanWithSteps());

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(mockCaseHub).startCase(contextCaptor.capture());
        Map<String, Object> ctx = contextCaptor.getValue();
        assertTrue(ctx.containsKey("cbrCalibration"), "should contain cbrCalibration");
        assertTrue(ctx.containsKey("adaptedPlan"), "should contain adaptedPlan");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_emptyAdaptation_writesCalibrationButNotAdaptedPlan() {
        var retrieval = retrievalWithCases();
        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(retrieval);
        when(trustFeatureEnricher.enrich(any(), any())).thenReturn(retrieval.currentFeatures());
        when(planAdapter.adapt(eq("contractor-coordination"), any(), any()))
                .thenReturn(new AdaptedPlan(List.of()));

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(mockCaseHub).startCase(contextCaptor.capture());
        Map<String, Object> ctx = contextCaptor.getValue();
        assertTrue(ctx.containsKey("cbrCalibration"), "should contain cbrCalibration");
        assertFalse(ctx.containsKey("adaptedPlan"), "should not contain adaptedPlan when steps empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_emptyRetrieval_skipsCbrEntirely() {
        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(LifeCbrRetrievalResult.EMPTY);

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(mockCaseHub).startCase(contextCaptor.capture());
        Map<String, Object> ctx = contextCaptor.getValue();
        assertFalse(ctx.containsKey("cbrCalibration"));
        assertFalse(ctx.containsKey("adaptedPlan"));
        verify(planAdapter, never()).adapt(anyString(), any(), any());
        verify(trustFeatureEnricher, never()).enrich(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_firesAdaptationEventWithCorrectTrace() {
        var retrieval = retrievalWithCases();
        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(retrieval);
        when(trustFeatureEnricher.enrich(any(), any())).thenReturn(retrieval.currentFeatures());
        when(planAdapter.adapt(eq("contractor-coordination"), any(), any()))
                .thenReturn(adaptedPlanWithSteps());

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(adaptationEvent).fire(eventCaptor.capture());
        CbrAdaptationRecorded recorded = eventCaptor.getValue();
        assertNotNull(recorded.trace());
        assertEquals("source-case-1", recorded.trace().sourceCaseId());
        assertEquals(0.85, recorded.trace().sourceScore(), 0.001);
        assertFalse(recorded.trace().steps().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_callsEnricherWithCurrentFeaturesAndContext() {
        Map<String, FeatureValue> currentFeatures = Map.of("budget", FeatureValue.number(2000));
        Map<String, FeatureValue> enrichedFeatures = new LinkedHashMap<>(currentFeatures);
        enrichedFeatures.put("actorTrustScore", FeatureValue.number(0.8));

        var retrieval = new LifeCbrRetrievalResult(
                new CbrSuggestions(Map.of("budget", FeatureStatistics.compute(new double[]{1500, 2000})),
                        1.0, 2, 0.85),
                List.of(scoredCase(currentFeatures)),
                currentFeatures);

        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(retrieval);
        when(trustFeatureEnricher.enrich(eq(currentFeatures), any())).thenReturn(enrichedFeatures);
        when(planAdapter.adapt(eq("contractor-coordination"), any(), eq(enrichedFeatures)))
                .thenReturn(adaptedPlanWithSteps());

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(planAdapter).adapt(eq("contractor-coordination"), any(), eq(enrichedFeatures));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCase_adaptationTraceRecordsEnrichedFeatures() {
        Map<String, FeatureValue> currentFeatures = Map.of("budget", FeatureValue.number(2000));
        Map<String, FeatureValue> enrichedFeatures = new LinkedHashMap<>(currentFeatures);
        enrichedFeatures.put("actorTrustScore", FeatureValue.number(0.75));

        var retrieval = new LifeCbrRetrievalResult(
                CbrSuggestions.EMPTY,
                List.of(scoredCase(currentFeatures)),
                currentFeatures);

        when(cbrSuggestionService.retrieveForAdaptation(eq(LifeCaseType.CONTRACTOR_COORDINATION), any()))
                .thenReturn(retrieval);
        when(trustFeatureEnricher.enrich(eq(currentFeatures), any())).thenReturn(enrichedFeatures);
        when(planAdapter.adapt(anyString(), any(), any())).thenReturn(new AdaptedPlan(List.of()));

        var request = new CreateLifeCaseRequest(
                LifeCaseType.CONTRACTOR_COORDINATION, Map.of("description", "test"));
        service.startCase(request);

        verify(adaptationEvent).fire(eventCaptor.capture());
        Map<String, FeatureValue> traceFeatures = eventCaptor.getValue().trace().currentFeatures();
        assertTrue(traceFeatures.containsKey("actorTrustScore"));
        assertEquals(0.75, ((FeatureValue.NumberVal) traceFeatures.get("actorTrustScore")).value());
    }

    private LifeCbrRetrievalResult retrievalWithCases() {
        Map<String, FeatureValue> features = Map.of("budget", FeatureValue.number(2000));
        return new LifeCbrRetrievalResult(
                new CbrSuggestions(
                        Map.of("budget", FeatureStatistics.compute(new double[]{1500, 2000, 2500})),
                        0.67, 3, 0.82),
                List.of(scoredCase(features), scoredCase(features)),
                features);
    }

    private ScoredCbrCase<PlanCbrCase> scoredCase(Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(
                new PlanCbrCase("problem", "solution", "COMPLETED", 0.9, features,
                        List.of(new PlanTrace("b1", "request-quote", "w1", "ok", 5, Map.of()))),
                "source-case-1", 0.85);
    }

    private AdaptedPlan adaptedPlanWithSteps() {
        return new AdaptedPlan(List.of(
                new AdaptedStep("b1", "request-quote", "w1", "ok", 5,
                        Map.of(), AdaptationAction.RETAINED, null)));
    }

    private void setField(String name, Object value) throws Exception {
        var field = LifeCaseService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
