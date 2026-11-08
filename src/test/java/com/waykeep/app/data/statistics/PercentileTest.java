package com.waykeep.app.data.statistics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PercentileTest {

    @Test
    public void returnsTheNearestRankAtTheQuantile() {
        List<Double> values = Arrays.asList(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d);

        assertEquals(1d, Percentile.of(values, 0.0, -1), 0.0001);
        assertEquals(10d, Percentile.of(values, 1.0, -1), 0.0001);
        assertEquals(6d, Percentile.of(values, 0.5, -1), 0.0001);
    }

    @Test
    public void ignoresNonFiniteValues() {
        List<Double> values = Arrays.asList(1d, Double.NaN, 3d, Double.POSITIVE_INFINITY, 5d);

        assertEquals(1d, Percentile.of(values, 0.0, -1), 0.0001);
        assertEquals(5d, Percentile.of(values, 1.0, -1), 0.0001);
    }

    @Test
    public void emptyOrAllInvalidUsesFallback() {
        assertEquals(42d, Percentile.of(Collections.emptyList(), 0.5, 42), 0.0001);
        assertEquals(42d, Percentile.of(Arrays.asList(Double.NaN, null), 0.5, 42), 0.0001);
    }
}
