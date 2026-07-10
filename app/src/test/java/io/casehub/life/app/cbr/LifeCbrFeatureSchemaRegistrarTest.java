package io.casehub.life.app.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.FeatureField;
import io.casehub.neocortex.memory.cbr.SimilaritySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LifeCbrFeatureSchemaRegistrarTest {

    @Test
    void registersAllSixSchemas() {
        var store = mock(CbrCaseMemoryStore.class);
        var registrar = new LifeCbrFeatureSchemaRegistrar(store);
        registrar.onStartup(null);

        var captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        verify(store, times(6)).registerSchema(captor.capture());

        var caseTypes = captor.getAllValues().stream()
                .map(CbrFeatureSchema::caseType).toList();
        assertThat(caseTypes).containsExactlyInAnyOrder(
                "contractor-coordination", "home-maintenance",
                "appointment-cycle", "care-coordination",
                "financial-review", "travel-plan");
    }

    @Test
    void contractorSchema_hasCorrectFields() {
        var store = mock(CbrCaseMemoryStore.class);
        var registrar = new LifeCbrFeatureSchemaRegistrar(store);
        registrar.onStartup(null);

        var captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        verify(store, atLeast(1)).registerSchema(captor.capture());

        CbrFeatureSchema contractor = captor.getAllValues().stream()
                .filter(s -> "contractor-coordination".equals(s.caseType()))
                .findFirst().orElseThrow();

        var fieldNames = contractor.fields().stream()
                .map(FeatureField::name).toList();
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "problemType", "season", "propertyArea",
                "budget", "quotedCost", "slaHours");
    }

    @Test
    void seasonField_hasCategoricalTable() {
        var store = mock(CbrCaseMemoryStore.class);
        var registrar = new LifeCbrFeatureSchemaRegistrar(store);
        registrar.onStartup(null);

        var captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        verify(store, atLeast(1)).registerSchema(captor.capture());

        CbrFeatureSchema contractor = captor.getAllValues().stream()
                .filter(s -> "contractor-coordination".equals(s.caseType()))
                .findFirst().orElseThrow();

        FeatureField.Categorical season = contractor.fields().stream()
                .filter(f -> "season".equals(f.name()))
                .map(f -> (FeatureField.Categorical) f)
                .findFirst().orElseThrow();
        assertThat(season.similaritySpec())
                .isInstanceOf(SimilaritySpec.CategoricalTable.class);
    }

    @Test
    void budgetField_hasGaussianDecay() {
        var store = mock(CbrCaseMemoryStore.class);
        var registrar = new LifeCbrFeatureSchemaRegistrar(store);
        registrar.onStartup(null);

        var captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        verify(store, atLeast(1)).registerSchema(captor.capture());

        CbrFeatureSchema contractor = captor.getAllValues().stream()
                .filter(s -> "contractor-coordination".equals(s.caseType()))
                .findFirst().orElseThrow();

        FeatureField.Numeric budget = contractor.fields().stream()
                .filter(f -> "budget".equals(f.name()))
                .map(f -> (FeatureField.Numeric) f)
                .findFirst().orElseThrow();
        assertThat(budget.similaritySpec())
                .isInstanceOf(SimilaritySpec.GaussianDecay.class);
        assertThat(budget.min()).isEqualTo(0);
        assertThat(budget.max()).isEqualTo(10_000);
    }

    @Test
    void travelSchema_hasPartySize() {
        var store = mock(CbrCaseMemoryStore.class);
        var registrar = new LifeCbrFeatureSchemaRegistrar(store);
        registrar.onStartup(null);

        var captor = ArgumentCaptor.forClass(CbrFeatureSchema.class);
        verify(store, atLeast(1)).registerSchema(captor.capture());

        CbrFeatureSchema travel = captor.getAllValues().stream()
                .filter(s -> "travel-plan".equals(s.caseType()))
                .findFirst().orElseThrow();

        var fieldNames = travel.fields().stream()
                .map(FeatureField::name).toList();
        assertThat(fieldNames).contains("partySize", "destination", "travelType");
    }
}
