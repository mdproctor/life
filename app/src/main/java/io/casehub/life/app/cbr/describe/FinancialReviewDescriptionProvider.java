package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.asMap;
import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.str;

@ApplicationScoped
public class FinancialReviewDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "financial-review";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        String period = str(caseData, "reviewPeriod", "unknown");
        var budget = asMap(caseData.get("budgetData"));
        String category = str(budget, "category", "");
        return "Financial review: %s%s".formatted(
                period,
                category.isEmpty() ? "" : " (" + category + ")");
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var analysis = asMap(caseData.get("analysis"));
        String outcome = str(analysis, "outcome", "unknown");
        var report = asMap(caseData.get("report"));
        String summary = str(report, "summary", "");
        return "Review %s%s".formatted(
                outcome,
                summary.isEmpty() ? "" : ": " + summary);
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        return caseId.toString();
    }
}
