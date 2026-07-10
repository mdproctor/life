package io.casehub.life.app.cbr.describe;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AppointmentCycleDescriptionProviderTest {

    private final AppointmentCycleDescriptionProvider provider = new AppointmentCycleDescriptionProvider();

    @Test void caseType() { assertThat(provider.caseType()).isEqualTo("appointment-cycle"); }

    @Test void describeProblem_withFullContext() {
        var data = Map.<String, Object>of("appointmentType", "dental-checkup", "provider", Map.of("name", "Dr Smith"));
        assertThat(provider.describeProblem(data)).contains("dental-checkup").contains("Dr Smith");
    }

    @Test void describeProblem_withMissingFields() { assertThat(provider.describeProblem(Map.of())).isNotBlank(); }

    @Test void describeSolution_withData() {
        var data = Map.<String, Object>of("visitNotes", Map.of("outcome", "satisfactory"), "booking", Map.of("provider", "City Dental"));
        assertThat(provider.describeSolution(data)).contains("satisfactory").contains("City Dental");
    }

    @Test void describeSolution_withMissingFields() { assertThat(provider.describeSolution(Map.of())).isNotBlank(); }

    @Test void extractEntityId_withProviderId() {
        var caseId = UUID.randomUUID();
        var data = Map.<String, Object>of("booking", Map.of("providerId", "prov-456"));
        assertThat(provider.extractEntityId(data, caseId)).isEqualTo("prov-456");
    }

    @Test void extractEntityId_fallbackToCaseId() {
        var caseId = UUID.randomUUID();
        assertThat(provider.extractEntityId(Map.of(), caseId)).isEqualTo(caseId.toString());
    }
}
