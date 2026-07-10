package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ContractorCoordinationDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "contractor-coordination";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        var request = asMap(caseData.get("contractorRequest"));
        String problemType = str(request, "problemType", "unknown");
        String area = str(request, "propertyArea", "");
        String budget = str(request, "budget", "");
        return "Contractor: %s%s%s".formatted(
                problemType,
                area.isEmpty() ? "" : " in " + area,
                budget.isEmpty() ? "" : ", budget " + budget);
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var quote = asMap(caseData.get("quoteResponse"));
        String contractor = str(quote, "contractor", "unknown");
        String amount = str(quote, "quotedAmount", "");
        return "Contractor %s%s".formatted(
                contractor,
                amount.isEmpty() ? "" : " at " + amount);
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        var request = asMap(caseData.get("contractorRequest"));
        String contractorId = str(request, "contractorId", "");
        return contractorId.isEmpty() ? caseId.toString() : contractorId;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object obj) {
        return obj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static String str(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : fallback;
    }
}
