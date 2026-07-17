package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.path.Path;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LifeRoutingOutcomeRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CbrCaseMemoryStore cbrStore;
    private LifeCbrFeatureExtractor featureExtractor;
    private LifeRoutingOutcomeRecorder.CaseTypeLookup caseTypeLookup;
    private LifeRoutingOutcomeRecorder recorder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        featureExtractor = mock(LifeCbrFeatureExtractor.class);
        caseTypeLookup = mock(LifeRoutingOutcomeRecorder.CaseTypeLookup.class);

        var descProvider = new io.casehub.life.app.cbr.describe
                .ContractorCoordinationDescriptionProvider();
        Instance<LifeCbrDescriptionProvider> providers = mock(Instance.class);
        when(providers.stream()).thenReturn(java.util.stream.Stream.of(descProvider));

        recorder = new LifeRoutingOutcomeRecorder(
                cbrStore, featureExtractor, caseTypeLookup, providers);
    }

    @Test
    void record_lifeCase_writesPlanCbrCaseWithTrace() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.of("contractor-coordination"));

        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        var contextJson = MAPPER.valueToTree(Map.of(
                "contractorRequest", Map.of("problemType", "boiler-repair")));
        when(featureExtractor.extract(eq("contractor-coordination"), eq(contextJson)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("boiler-repair")))));

        var context = new AgentRoutingContext(
                caseId, "request-quote", contextJson, "test-tenant", List.of());

        recorder.record(context, "request-quote-agent", "request-quote",
                RoutingOutcome.SUCCESS, Duration.ofSeconds(2))
                .await().indefinitely();

        var caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(
                caseCaptor.capture(),
                eq("contractor-coordination"),
                eq("agent-routing"),
                eq(new MemoryDomain("casehubio/life/contractor")),
                eq("test-tenant"),
                eq(caseId.toString()),
                eq(Path.parse("casehubio/life/contractor")));

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
        assertThat(stored.features()).containsEntry("problemType", FeatureValue.string("boiler-repair"));
        assertThat(stored.planTrace()).hasSize(1);
        assertThat(stored.planTrace().get(0).bindingName()).isEqualTo("request-quote");
        assertThat(stored.planTrace().get(0).capabilityName()).isEqualTo("request-quote");
        assertThat(stored.planTrace().get(0).workerName()).isEqualTo("request-quote-agent");
        assertThat(stored.planTrace().get(0).stepOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void record_nonLifeCase_skips() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.empty());

        var context = new AgentRoutingContext(
                caseId, "some-cap", NullNode.getInstance(), "test-tenant", List.of());

        recorder.record(context, "w1", "b1", RoutingOutcome.SUCCESS, null)
                .await().indefinitely();

        verifyNoInteractions(cbrStore);
    }

    @Test
    void record_extractionEmpty_skips() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.of("contractor-coordination"));
        when(featureExtractor.extract(eq("contractor-coordination"), any()))
                .thenReturn(Optional.empty());

        var context = new AgentRoutingContext(
                caseId, "cap", NullNode.getInstance(), "test-tenant", List.of());

        recorder.record(context, "w1", "b1", RoutingOutcome.SUCCESS, null)
                .await().indefinitely();

        verifyNoInteractions(cbrStore);
    }

    @Test
    void record_storeThrows_uniCompletes() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.of("contractor-coordination"));

        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        var contextJson = MAPPER.valueToTree(Map.of("contractorRequest", Map.of("problemType", "x")));
        when(featureExtractor.extract(eq("contractor-coordination"), eq(contextJson)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("x")))));
        when(cbrStore.store(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        var context = new AgentRoutingContext(
                caseId, "cap", contextJson, "test-tenant", List.of());

        recorder.record(context, "w1", "b1", RoutingOutcome.FAILURE, null)
                .await().indefinitely();
    }

    @Test
    void record_gateRejected_recordsOutcome() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.of("contractor-coordination"));

        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        var contextJson = MAPPER.valueToTree(Map.of("contractorRequest", Map.of("problemType", "y")));
        when(featureExtractor.extract(eq("contractor-coordination"), eq(contextJson)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("y")))));

        var context = new AgentRoutingContext(
                caseId, "cap", contextJson, "test-tenant", List.of());

        recorder.record(context, "w1", "b1", RoutingOutcome.GATE_REJECTED, null)
                .await().indefinitely();

        var captor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(captor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(captor.getValue().planTrace().get(0).stepOutcome()).isEqualTo("GATE_REJECTED");
    }
}
