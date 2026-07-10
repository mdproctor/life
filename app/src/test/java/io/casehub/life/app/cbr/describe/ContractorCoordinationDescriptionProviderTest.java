package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContractorCoordinationDescriptionProviderTest {

    private final ContractorCoordinationDescriptionProvider provider =
            new ContractorCoordinationDescriptionProvider();

    @Test
    void caseType() {
        assertThat(provider.caseType()).isEqualTo("contractor-coordination");
    }

    @Test
    void describeProblem_withFullContext() {
        var data = Map.<String, Object>of(
                "contractorRequest", Map.of(
                        "problemType", "boiler-repair",
                        "propertyArea", "kitchen",
                        "budget", 500));
        assertThat(provider.describeProblem(data)).contains("boiler-repair");
    }

    @Test
    void describeProblem_withMissingFields() {
        assertThat(provider.describeProblem(Map.of())).isNotBlank();
    }

    @Test
    void describeSolution_withQuoteData() {
        var data = Map.<String, Object>of(
                "quoteResponse", Map.of("quotedAmount", 450, "contractor", "PlumbCo"),
                "quoteApproval", Map.of("approved", true));
        assertThat(provider.describeSolution(data)).contains("PlumbCo");
    }

    @Test
    void describeSolution_withMissingFields() {
        assertThat(provider.describeSolution(Map.of())).isNotBlank();
    }

    @Test
    void extractEntityId_withContractorId() {
        var caseId = UUID.randomUUID();
        var data = Map.<String, Object>of(
                "contractorRequest", Map.of("contractorId", "ext-actor-123"));
        assertThat(provider.extractEntityId(data, caseId)).isEqualTo("ext-actor-123");
    }

    @Test
    void extractEntityId_fallbackToCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
