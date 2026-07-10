package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.asMap;
import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.str;

@ApplicationScoped
public class AppointmentCycleDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "appointment-cycle";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        String appointmentType = str(caseData, "appointmentType", "unknown");
        var provider = asMap(caseData.get("provider"));
        String providerName = str(provider, "name", "");
        return "Appointment: %s%s".formatted(
                appointmentType,
                providerName.isEmpty() ? "" : " with " + providerName);
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var visitNotes = asMap(caseData.get("visitNotes"));
        String outcome = str(visitNotes, "outcome", "unknown");
        var booking = asMap(caseData.get("booking"));
        String providerName = str(booking, "provider", "");
        return "Visit %s%s".formatted(
                outcome,
                providerName.isEmpty() ? "" : " at " + providerName);
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        var booking = asMap(caseData.get("booking"));
        String providerId = str(booking, "providerId", "");
        if (!providerId.isEmpty()) return providerId;
        var provider = asMap(caseData.get("provider"));
        String id = str(provider, "id", "");
        return id.isEmpty() ? caseId.toString() : id;
    }
}
