package com.waykeep.app.data.statistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Percentile helper for chart scaling. Kept free of Android types so the scale decision is
 * unit-testable on the JVM.
 */
public final class Percentile {

    private Percentile() {
    }

    /**
     * Returns the value at {@code quantile} (0..1) using the nearest-rank method. Non-finite
     * values are ignored. Returns {@code fallback} when no finite value remains.
     */
    public static double of(List<Double> values, double quantile, double fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<Double> sorted = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                sorted.add(value);
            }
        }
        if (sorted.isEmpty()) {
            return fallback;
        }
        Collections.sort(sorted);
        double clamped = Math.max(0, Math.min(1, quantile));
        int index = (int) Math.round((sorted.size() - 1) * clamped);
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }
}
