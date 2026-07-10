package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FinancialReviewDescriptionProviderTest {

    private final FinancialReviewDescriptionProvider provider = new FinancialReviewDescriptionProvider();

    @Test void caseType() { assertThat(provider.caseType()).isEqualTo("financial-review"); }

    @Test void describeProblem_withFullContext() {
        var data = Map.<String, Object>of("reviewPeriod", "2026-Q2", "budgetData", Map.of("category", "household"));
        assertThat(provider.describeProblem(data)).contains("2026-Q2").contains("household");
    }

    @Test void describeProblem_withMissingFields() { assertThat(provider.describeProblem(Map.of())).isNotBlank(); }

    @Test void describeSolution_withData() {
        var data = Map.<String, Object>of("analysis", Map.of("outcome", "on-track"), "report", Map.of("summary", "all good"));
        assertThat(provider.describeSolution(data)).contains("on-track").contains("all good");
    }

    @Test void describeSolution_withMissingFields() { assertThat(provider.describeSolution(Map.of())).isNotBlank(); }

    @Test void extractEntityId_alwaysCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
