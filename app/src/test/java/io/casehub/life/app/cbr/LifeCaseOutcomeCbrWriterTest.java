package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LifeCaseOutcomeCbrWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CbrCaseMemoryStore cbrStore;
    private CaseDefinitionRegistry registry;
    private JQEvaluator jqEvaluator;
    private LifeCaseOutcomeCbrWriter writer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        registry = mock(CaseDefinitionRegistry.class);
        jqEvaluator = mock(JQEvaluator.class);

        var descProvider = new ContractorCoordinationDescriptionProvider();
        Instance<LifeCbrDescriptionProvider> providers = mock(Instance.class);
        when(providers.stream()).thenReturn(java.util.stream.Stream.of(descProvider));

        writer = new LifeCaseOutcomeCbrWriter(cbrStore, registry, jqEvaluator, providers);
    }

    @Test
    void onOutcome_contractorCase_writesPlanCbrCase() {
        CbrConfig config = CbrConfig.builder()
                .feature("problemType", ".contractorRequest.problemType")
                .feature("budget", ".contractorRequest.budget")
                .domain("casehubio/life/contractor")
                .caseType("contractor-coordination")
                .build();
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));

        when(jqEvaluator.eval(eq(".contractorRequest.problemType"), any(JsonNode.class)))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("boiler-repair"))));
        when(jqEvaluator.eval(eq(".contractorRequest.budget"), any(JsonNode.class)))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree(500))));

        var snapshot = Map.<String, Object>of(
                "contractorRequest", Map.of("problemType", "boiler-repair", "budget", 500, "contractorId", "ext-123"));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                snapshot, "COMPLETED", Instant.now(), Map.of());

        writer.onOutcome(event);

        var caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(
                caseCaptor.capture(),
                eq("contractor-coordination"),
                eq("ext-123"),
                eq(new MemoryDomain("casehubio/life/contractor")),
                eq("life-personal"),
                eq(event.caseId().toString()));

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("COMPLETED");
        assertThat(stored.features()).containsEntry("problemType", "boiler-repair");
        assertThat(stored.features()).containsEntry("budget", 500);
        assertThat(stored.planTrace()).isEmpty();
        assertThat(stored.problem()).contains("boiler-repair");
    }

    @Test
    void onOutcome_nonLifeCase_skips() {
        var event = new CaseOutcomeEvent(
                "unknown-type", UUID.randomUUID(),
                Map.of(), "COMPLETED", Instant.now(), Map.of());
        writer.onOutcome(event);
        verifyNoInteractions(cbrStore);
    }

    @Test
    void onOutcome_noDefinition_skips() {
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.empty());
        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                Map.of(), "COMPLETED", Instant.now(), Map.of());
        writer.onOutcome(event);
        verifyNoInteractions(cbrStore);
    }

    @Test
    void onOutcome_noCbrConfig_skips() {
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(null);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));
        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                Map.of(), "COMPLETED", Instant.now(), Map.of());
        writer.onOutcome(event);
        verifyNoInteractions(cbrStore);
    }

    @Test
    void onOutcome_lambdaExtractor_skips() {
        CbrConfig config = CbrConfig.builder()
                .featureExtractor(ctx -> Map.of())
                .domain("casehubio/life/contractor")
                .build();
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));
        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                Map.of(), "COMPLETED", Instant.now(), Map.of());
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
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));

        when(jqEvaluator.eval(any(), any(JsonNode.class)))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("boiler-repair"))));
        when(cbrStore.store(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("store failure"));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                Map.of("contractorRequest", Map.of("problemType", "boiler-repair")),
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
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));

        when(jqEvaluator.eval(any(), any(JsonNode.class)))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("roof-leak"))));

        var event = new CaseOutcomeEvent(
                "contractor-coordination", UUID.randomUUID(),
                Map.of("contractorRequest", Map.of("problemType", "roof-leak")),
                "FAULTED", Instant.now(), Map.of());

        writer.onOutcome(event);

        var caseCaptor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(caseCaptor.capture(), any(), any(), any(), any(), any());
        assertThat(caseCaptor.getValue().outcome()).isEqualTo("FAULTED");
    }
}
