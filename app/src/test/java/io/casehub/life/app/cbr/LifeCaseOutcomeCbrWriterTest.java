package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.path.Path;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
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

class LifeCaseOutcomeCbrWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CbrCaseMemoryStore cbrStore;
    private LifeCbrFeatureExtractor featureExtractor;
    private LifeCaseOutcomeCbrWriter writer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        featureExtractor = mock(LifeCbrFeatureExtractor.class);

        var descProvider = new ContractorCoordinationDescriptionProvider();
        Instance<LifeCbrDescriptionProvider> providers = mock(Instance.class);
        when(providers.stream()).thenReturn(java.util.stream.Stream.of(descProvider));

        writer = new LifeCaseOutcomeCbrWriter(cbrStore, featureExtractor, providers);
    }

    @Test
    void onOutcome_contractorCase_writesPlanCbrCase() {
        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .feature("budget", ".contractorRequest.budget")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        when(featureExtractor.extract(eq("contractor-coordination"), any(JsonNode.class)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("boiler-repair"),
                                       "budget", FeatureValue.number(500)))));

        var snapshot = Map.<String, Object>of(
                "contractorRequest", Map.of("problemType", "boiler-repair", "budget", 500, "contractorId", "ext-123"));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", "test-tenant", UUID.randomUUID(),
                snapshot, "COMPLETED", Instant.now(), Map.of());

        writer.onOutcome(event);

        var caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(
                caseCaptor.capture(),
                eq("contractor-coordination"),
                eq("ext-123"),
                eq(new MemoryDomain("casehubio/life/contractor")),
                eq("life-personal"),
                eq(event.caseId().toString()),
                eq(Path.parse("casehubio/life/contractor")));

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("COMPLETED");
        assertThat(stored.features()).containsEntry("problemType", FeatureValue.string("boiler-repair"));
        assertThat(stored.features()).containsEntry("budget", FeatureValue.number(500));
        assertThat(stored.planTrace()).isEmpty();
        assertThat(stored.problem()).contains("boiler-repair");
    }

    @Test
    void onOutcome_nonLifeCase_skips() {
        var event = new CaseOutcomeEvent(
                "unknown-type", "test-tenant", UUID.randomUUID(),
                Map.of(), "COMPLETED", Instant.now(), Map.of());
        writer.onOutcome(event);
        verifyNoInteractions(cbrStore);
    }

    @Test
    void onOutcome_extractionEmpty_skips() {
        when(featureExtractor.extract(eq("contractor-coordination"), any(JsonNode.class)))
                .thenReturn(Optional.empty());
        var event = new CaseOutcomeEvent(
                "contractor-coordination", "test-tenant", UUID.randomUUID(),
                Map.of("contractorRequest", Map.of("problemType", "test", "contractorId", "ext-1")),
                "COMPLETED", Instant.now(), Map.of());
        writer.onOutcome(event);
        verifyNoInteractions(cbrStore);
    }

    @Test
    void onOutcome_storeThrows_doesNotPropagate() {
        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        when(featureExtractor.extract(eq("contractor-coordination"), any(JsonNode.class)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("boiler-repair")))));
        when(cbrStore.store(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("store failure"));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", "test-tenant", UUID.randomUUID(),
                Map.of("contractorRequest", Map.of("problemType", "boiler-repair", "contractorId", "ext-1")),
                "COMPLETED", Instant.now(), Map.of());

        writer.onOutcome(event);
    }

    @Test
    void onOutcome_faultedCase_recordsFaultedOutcome() {
        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        when(featureExtractor.extract(eq("contractor-coordination"), any(JsonNode.class)))
                .thenReturn(Optional.of(new LifeCbrFeatureExtractor.ExtractionResult(
                        config, Map.of("problemType", FeatureValue.string("roof-leak")))));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", "test-tenant", UUID.randomUUID(),
                Map.of("contractorRequest", Map.of("problemType", "roof-leak", "contractorId", "ext-1")),
                "FAULTED", Instant.now(), Map.of());

        writer.onOutcome(event);

        var caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), any(), any(), any(), any(), any(), any());
        assertThat(caseCaptor.getValue().outcome()).isEqualTo("FAULTED");
    }
}
