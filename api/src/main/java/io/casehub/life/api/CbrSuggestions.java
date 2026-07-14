package io.casehub.life.api;

import java.util.Map;

public record CbrSuggestions(
        Map<String, FeatureStatistics> featureStats,
        double historicalSuccessRate,
        int experienceCount,
        double averageSimilarity) {

    public static final CbrSuggestions EMPTY = new CbrSuggestions(Map.of(), 0.0, 0, 0.0);

    public boolean isEmpty() {
        return experienceCount == 0;
    }
}
