package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CareCoordinationDescriptionProviderTest {

    private final CareCoordinationDescriptionProvider provider = new CareCoordinationDescriptionProvider();

    @Test void caseType() { assertThat(provider.caseType()).isEqualTo("care-coordination"); }

    @Test void describeProblem_withFullContext() {
        var data = Map.<String, Object>of("careRequest", Map.of("careType", "daily-living", "patientRiskLevel", "high"));
        assertThat(provider.describeProblem(data)).contains("daily-living").contains("high");
    }

    @Test void describeProblem_withMissingFields() { assertThat(provider.describeProblem(Map.of())).isNotBlank(); }

    @Test void describeSolution_withData() {
        var data = Map.<String, Object>of("carePlan", Map.of("coordinator", "CareFirst", "hoursPerWeek", 20));
        assertThat(provider.describeSolution(data)).contains("CareFirst").contains("20");
    }

    @Test void describeSolution_withMissingFields() { assertThat(provider.describeSolution(Map.of())).isNotBlank(); }

    @Test void extractEntityId_withCoordinatorId() {
        var caseId = UUID.randomUUID();
        var data = Map.<String, Object>of("careRequest", Map.of("coordinatorId", "coord-789"));
        assertThat(provider.extractEntityId(data, caseId)).isEqualTo("coord-789");
    }

    @Test void extractEntityId_fallbackToCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
