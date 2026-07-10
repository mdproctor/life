package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.asMap;
import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.str;

@ApplicationScoped
public class CareCoordinationDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "care-coordination";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        var request = asMap(caseData.get("careRequest"));
        String careType = str(request, "careType", "unknown");
        String risk = str(request, "patientRiskLevel", "");
        return "Care: %s%s".formatted(
                careType,
                risk.isEmpty() ? "" : " (risk: " + risk + ")");
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var plan = asMap(caseData.get("carePlan"));
        String coordinator = str(plan, "coordinator", "unknown");
        String hours = str(plan, "hoursPerWeek", "");
        return "Coordinator %s%s".formatted(
                coordinator,
                hours.isEmpty() ? "" : ", " + hours + "h/week");
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        var request = asMap(caseData.get("careRequest"));
        String coordinatorId = str(request, "coordinatorId", "");
        return coordinatorId.isEmpty() ? caseId.toString() : coordinatorId;
    }
}
