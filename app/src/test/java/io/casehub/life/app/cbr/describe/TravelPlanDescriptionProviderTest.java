package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TravelPlanDescriptionProviderTest {

    private final TravelPlanDescriptionProvider provider = new TravelPlanDescriptionProvider();

    @Test void caseType() { assertThat(provider.caseType()).isEqualTo("travel-plan"); }

    @Test void describeProblem_withFullContext() {
        var data = Map.<String, Object>of("request", Map.of("destination", "Barcelona", "travelType", "family", "budget", 3000));
        assertThat(provider.describeProblem(data)).contains("Barcelona").contains("family").contains("3000");
    }

    @Test void describeProblem_withMissingFields() { assertThat(provider.describeProblem(Map.of())).isNotBlank(); }

    @Test void describeSolution_withData() {
        var data = Map.<String, Object>of("itinerary", Map.of("summary", "5 nights central"), "booking", Map.of("total", 2800));
        assertThat(provider.describeSolution(data)).contains("5 nights central").contains("2800");
    }

    @Test void describeSolution_withMissingFields() { assertThat(provider.describeSolution(Map.of())).isNotBlank(); }

    @Test void extractEntityId_alwaysCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
