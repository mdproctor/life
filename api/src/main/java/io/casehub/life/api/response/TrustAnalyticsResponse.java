package io.casehub.life.api.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TrustAnalyticsResponse(
        int actorCount,
        Double avgGlobalScore,
        Map<String, Double> dimensionAverages,
        List<ActorScoreSummary> lowestScoreActors) {

    public record ActorScoreSummary(UUID actorId, String name, Double globalScore) {}
}
