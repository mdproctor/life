package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class HomeMaintenanceDescriptionProviderTest {

    private final HomeMaintenanceDescriptionProvider provider = new HomeMaintenanceDescriptionProvider();

    @Test void caseType() { assertThat(provider.caseType()).isEqualTo("home-maintenance"); }

    @Test void describeProblem_withFullContext() {
        var data = Map.<String, Object>of("request", Map.of("issueType", "leaking-pipe", "severity", "high"));
        assertThat(provider.describeProblem(data)).contains("leaking-pipe").contains("high");
    }

    @Test void describeProblem_withMissingFields() { assertThat(provider.describeProblem(Map.of())).isNotBlank(); }

    @Test void describeSolution_withData() {
        var data = Map.<String, Object>of("jobStatus", Map.of("outcome", "resolved"), "quotes", Map.of("selectedContractor", "FixItCo"));
        assertThat(provider.describeSolution(data)).contains("resolved").contains("FixItCo");
    }

    @Test void describeSolution_withMissingFields() { assertThat(provider.describeSolution(Map.of())).isNotBlank(); }

    @Test void extractEntityId_alwaysCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
