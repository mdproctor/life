package io.casehub.life.app.cbr.describe;

import io.casehub.life.app.cbr.LifeCbrDescriptionProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.asMap;
import static io.casehub.life.app.cbr.describe.ContractorCoordinationDescriptionProvider.str;

@ApplicationScoped
public class TravelPlanDescriptionProvider implements LifeCbrDescriptionProvider {

    @Override
    public String caseType() {
        return "travel-plan";
    }

    @Override
    public String describeProblem(Map<String, Object> caseData) {
        var request = asMap(caseData.get("request"));
        String destination = str(request, "destination", "unknown");
        String travelType = str(request, "travelType", "");
        String budget = str(request, "budget", "");
        return "Travel: %s%s%s".formatted(
                destination,
                travelType.isEmpty() ? "" : " (" + travelType + ")",
                budget.isEmpty() ? "" : ", budget " + budget);
    }

    @Override
    public String describeSolution(Map<String, Object> caseData) {
        var itinerary = asMap(caseData.get("itinerary"));
        String summary = str(itinerary, "summary", "unknown");
        var booking = asMap(caseData.get("booking"));
        String total = str(booking, "total", "");
        return "Itinerary %s%s".formatted(
                summary,
                total.isEmpty() ? "" : ", total " + total);
    }

    @Override
    public String extractEntityId(Map<String, Object> caseData, UUID caseId) {
        return caseId.toString();
    }
}
