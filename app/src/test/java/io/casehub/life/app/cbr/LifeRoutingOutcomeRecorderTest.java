package io.casehub.life.app.cbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.cbr.CbrConfig;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.platform.expression.JQEvaluator;
import io.casehub.platform.expression.ValidationResult;
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
import static org.mockito.Mockito.*;

class LifeRoutingOutcomeRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CbrCaseMemoryStore cbrStore;
    private CaseDefinitionRegistry registry;
    private JQEvaluator jqEvaluator;
    private LifeRoutingOutcomeRecorder.CaseTypeLookup caseTypeLookup;
    private LifeRoutingOutcomeRecorder recorder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cbrStore = mock(CbrCaseMemoryStore.class);
        registry = mock(CaseDefinitionRegistry.class);
        jqEvaluator = mock(JQEvaluator.class);
        caseTypeLookup = mock(LifeRoutingOutcomeRecorder.CaseTypeLookup.class);

        var descProvider = new io.casehub.life.app.cbr.describe
                .ContractorCoordinationDescriptionProvider();
        Instance<LifeCbrDescriptionProvider> providers = mock(Instance.class);
        when(providers.stream()).thenReturn(java.util.stream.Stream.of(descProvider));

        recorder = new LifeRoutingOutcomeRecorder(
                cbrStore, registry, jqEvaluator, caseTypeLookup, providers);
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
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));

        var contextJson = MAPPER.valueToTree(Map.of(
                "contractorRequest", Map.of("problemType", "boiler-repair")));
        when(jqEvaluator.eval(eq(".contractorRequest.problemType"), any()))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("boiler-repair"))));

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
                eq(caseId.toString()));

        PlanCbrCase stored = caseCaptor.getValue();
        assertThat(stored.outcome()).isEqualTo("SUCCESS");
        assertThat(stored.features()).containsEntry("problemType", "boiler-repair");
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
    void record_noCbrConfig_skips() {
        UUID caseId = UUID.randomUUID();
        when(caseTypeLookup.findCaseType(caseId)).thenReturn(Optional.of("contractor-coordination"));

        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(null);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));

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
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));
        when(jqEvaluator.eval(any(), any()))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("x"))));
        when(cbrStore.store(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        var contextJson = MAPPER.valueToTree(Map.of("contractorRequest", Map.of("problemType", "x")));
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
        var definition = mock(CaseDefinition.class);
        when(definition.getCbrConfig()).thenReturn(config);
        when(registry.findByName("contractor-coordination")).thenReturn(Optional.of(definition));
        when(jqEvaluator.eval(any(), any()))
                .thenReturn(ValidationResult.ok(List.of(MAPPER.valueToTree("y"))));

        var contextJson = MAPPER.valueToTree(Map.of("contractorRequest", Map.of("problemType", "y")));
        var context = new AgentRoutingContext(
                caseId, "cap", contextJson, "test-tenant", List.of());

        recorder.record(context, "w1", "b1", RoutingOutcome.GATE_REJECTED, null)
                .await().indefinitely();

        var captor = ArgumentCaptor.forClass(PlanCbrCase.class);
        verify(cbrStore).store(captor.capture(), any(), any(), any(), any(), any());
        assertThat(captor.getValue().planTrace().get(0).stepOutcome()).isEqualTo("GATE_REJECTED");
    }
}
