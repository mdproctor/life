package io.casehub.life.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureStatisticsTest {

    @Test
    void compute_oddSampleCount_nearestRankPercentiles() {
        var stats = FeatureStatistics.compute(new double[]{100, 200, 300, 400, 500});
        assertEquals(100.0, stats.min());
        assertEquals(500.0, stats.max());
        assertEquals(300.0, stats.median());
        assertEquals(400.0, stats.p75());
        assertEquals(5, stats.sampleCount());
    }

    @Test
    void compute_evenSampleCount_nearestRankPercentiles() {
        var stats = FeatureStatistics.compute(new double[]{100, 200, 300, 400});
        assertEquals(100.0, stats.min());
        assertEquals(400.0, stats.max());
        assertEquals(200.0, stats.median());
        assertEquals(300.0, stats.p75());
        assertEquals(4, stats.sampleCount());
    }

    @Test
    void compute_singleElement() {
        var stats = FeatureStatistics.compute(new double[]{42.0});
        assertEquals(42.0, stats.min());
        assertEquals(42.0, stats.max());
        assertEquals(42.0, stats.median());
        assertEquals(42.0, stats.p75());
        assertEquals(1, stats.sampleCount());
    }

    @Test
    void compute_allEqualValues() {
        var stats = FeatureStatistics.compute(new double[]{7.0, 7.0, 7.0});
        assertEquals(7.0, stats.min());
        assertEquals(7.0, stats.max());
        assertEquals(7.0, stats.median());
        assertEquals(7.0, stats.p75());
    }

    @Test
    void compute_twoElements() {
        var stats = FeatureStatistics.compute(new double[]{10, 20});
        assertEquals(10.0, stats.min());
        assertEquals(20.0, stats.max());
        assertEquals(10.0, stats.median());
        assertEquals(20.0, stats.p75());
    }

    @Test
    void compute_unsortedInput_sortsInternally() {
        var stats = FeatureStatistics.compute(new double[]{500, 100, 300, 200, 400});
        assertEquals(100.0, stats.min());
        assertEquals(500.0, stats.max());
        assertEquals(300.0, stats.median());
        assertEquals(400.0, stats.p75());
    }
}
