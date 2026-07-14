package io.casehub.life.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CbrSuggestionsTest {

    @Test
    void empty_constant_isCorrect() {
        assertTrue(CbrSuggestions.EMPTY.isEmpty());
        assertEquals(0, CbrSuggestions.EMPTY.experienceCount());
        assertEquals(0.0, CbrSuggestions.EMPTY.historicalSuccessRate());
        assertEquals(0.0, CbrSuggestions.EMPTY.averageSimilarity());
        assertTrue(CbrSuggestions.EMPTY.featureStats().isEmpty());
    }

    @Test
    void isEmpty_withExperiences_returnsFalse() {
        var stats = new FeatureStatistics(10, 20, 15, 18, 3);
        var suggestions = new CbrSuggestions(Map.of("cost", stats), 0.8, 3, 0.75);
        assertFalse(suggestions.isEmpty());
    }

    @Test
    void isEmpty_zeroCount_returnsTrue() {
        var suggestions = new CbrSuggestions(Map.of(), 0.0, 0, 0.0);
        assertTrue(suggestions.isEmpty());
    }
}
