package io.casehub.life.app.cbr;

import io.casehub.api.spi.routing.RetrievedExperience;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LifeCbrExperienceFormatterTest {

    private final LifeCbrExperienceFormatter formatter = new LifeCbrExperienceFormatter();

    @Test
    void format_emptyList_returnsNull() {
        assertNull(formatter.format(List.of()));
    }

    @Test
    void format_nullList_returnsNull() {
        assertNull(formatter.format(null));
    }

    @Test
    void format_singleExperience_includesProblemSolutionOutcome() {
        var exp = new RetrievedExperience("A problem", "A solution", "COMPLETED",
                0.9, 0.85, Map.of(), List.of(), Map.of());
        String result = formatter.format(List.of(exp));
        assertNotNull(result);
        assertTrue(result.contains("A problem"));
        assertTrue(result.contains("A solution"));
        assertTrue(result.contains("COMPLETED"));
        assertTrue(result.contains("0.85"));
    }

    @Test
    void format_multipleExperiences_sortedBySimilarityDescending() {
        var low = new RetrievedExperience("Low", "sol", "COMPLETED", null,
                0.5, Map.of(), List.of(), Map.of());
        var high = new RetrievedExperience("High", "sol", "COMPLETED", null,
                0.9, Map.of(), List.of(), Map.of());
        String result = formatter.format(List.of(low, high));
        assertTrue(result.indexOf("High") < result.indexOf("Low"));
    }

    @Test
    void format_featureSimilarities_rendered() {
        var exp = new RetrievedExperience("prob", "sol", "COMPLETED", null,
                0.8, Map.of("cost", 500), List.of(),
                Map.of("cost", 0.95, "type", 1.0));
        String result = formatter.format(List.of(exp));
        assertTrue(result.contains("cost"));
        assertTrue(result.contains("0.95"));
    }

    @Test
    void format_emptyFeatureSimilarities_lineOmitted() {
        var exp = new RetrievedExperience("prob", "sol", "COMPLETED", null,
                0.8, Map.of("cost", 500), List.of(), Map.of());
        String result = formatter.format(List.of(exp));
        assertFalse(result.contains("Most similar on"));
    }

    @Test
    void format_cappedAtMaxExperiences() {
        var experiences = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new RetrievedExperience("p" + i, "s", "COMPLETED", null,
                        0.5 + i * 0.01, Map.of(), List.of(), Map.of()))
                .toList();
        String result = formatter.format(experiences);
        long count = result.lines().filter(l -> l.startsWith("## Similar Case")).count();
        assertEquals(5, count);
    }

    @Test
    void format_features_rendered() {
        var exp = new RetrievedExperience("prob", "sol", "COMPLETED", null,
                0.8, Map.of("cost", 500, "type", "repair"), List.of(), Map.of());
        String result = formatter.format(List.of(exp));
        assertTrue(result.contains("Key features:"));
        assertTrue(result.contains("cost=500"));
    }

    @Test
    void format_emptyFeatures_lineOmitted() {
        var exp = new RetrievedExperience("prob", "sol", "COMPLETED", null,
                0.8, Map.of(), List.of(), Map.of());
        String result = formatter.format(List.of(exp));
        assertFalse(result.contains("Key features:"));
    }
}
