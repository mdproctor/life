package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.asMap;
import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.str;

@ApplicationScoped
public class HomeMaintenanceDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "home-maintenance";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        var request = asMap(caseData.get("request"));
        String issueType = str(request, "issueType", "unknown");
        String severity = str(request, "severity", "");
        return "Maintenance: %s%s".formatted(
                issueType,
                severity.isEmpty() ? "" : " (" + severity + ")");
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var jobStatus = asMap(caseData.get("jobStatus"));
        String outcome = str(jobStatus, "outcome", "unknown");
        var quotes = asMap(caseData.get("quotes"));
        String contractor = str(quotes, "selectedContractor", "");
        return "Outcome %s%s".formatted(
                outcome,
                contractor.isEmpty() ? "" : " by " + contractor);
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        return caseId.toString();
    }
}
