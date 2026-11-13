package com.waykeep.app.sensors;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.TrackPoint;

/** Downstream opt-in fallback for Codeberg #1570; GPS altitude remains noisy despite this filter. */
public class GpsAltitudeGainLossFallback {

    private static final int WINDOW_SIZE = 5;
    private static final double MAX_VERTICAL_ACCURACY_M = 20;
    private static final double DEADBAND_M = 3;

    private final ArrayDeque<Double> altitudes = new ArrayDeque<>(WINDOW_SIZE);
    private Double previousMedian;
    private float gainM;
    private float lossM;
    private AltitudeGainLoss estimate;

    public void add(Position position) {
        if (!position.hasAltitude() || !position.hasVerticalAccuracy()) return;

        double altitude = position.altitude().toM();
        double accuracy = position.verticalAccuracy().toM();
        if (!Double.isFinite(altitude) || !Double.isFinite(accuracy)
                || accuracy < 0 || accuracy > MAX_VERTICAL_ACCURACY_M) {
            return;
        }

        addAltitude(altitude);
    }

    public void addAltitude(double altitude) {
        if (!Double.isFinite(altitude)) return;

        if (altitudes.size() == WINDOW_SIZE) {
            altitudes.removeFirst();
        }
        altitudes.addLast(altitude);
        if (altitudes.size() < WINDOW_SIZE) return;

        List<Double> sorted = new ArrayList<>(altitudes);
        Collections.sort(sorted);
        double median = sorted.get(WINDOW_SIZE / 2);
        if (previousMedian == null) {
            previousMedian = median;
        } else {
            double change = median - previousMedian;
            if (change >= DEADBAND_M) {
                gainM += (float) change;
                previousMedian = median;
            } else if (change <= -DEADBAND_M) {
                lossM += (float) -change;
                previousMedian = median;
            }
        }

        estimate = new AltitudeGainLoss(gainM, lossM, AltitudeGainLoss.Source.GPS_ESTIMATE);
    }

    /**
     * Populates {@link AltitudeGainLoss} from {@code <ele>} on imported tracks that do not carry
     * explicit gain/loss extensions. Codeberg #555 reported gain 0 for imported GPX; #556 fixed
     * only OpenTracks' own exports by adding {@code <opentracks:gain>}/{@code <opentracks:loss>}.
     */
    public static void applyToImported(List<TrackPoint> trackPoints) {
        for (TrackPoint point : trackPoints) {
            if (point.altitudeGainLoss() != null) {
                return;
            }
        }

        GpsAltitudeGainLossFallback fallback = new GpsAltitudeGainLossFallback();
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint current = trackPoints.get(i);
            if (current.type() != TrackPoint.Type.TRACKPOINT) {
                fallback.reset();
            }
            if (current.position().hasVerticalAccuracy()) {
                fallback.add(current.position());
            } else if (current.position().hasAltitude()) {
                fallback.addAltitude(current.position().altitude().toM());
            }
            AltitudeGainLoss estimate = fallback.getEstimate();
            if (estimate != null) {
                trackPoints.set(i, current.with(estimate));
                fallback.resetAggregated();
            }
        }
    }

    @Nullable
    public AltitudeGainLoss getEstimate() {
        return estimate;
    }

    public void resetAggregated() {
        gainM = 0;
        lossM = 0;
        estimate = null;
    }

    public void reset() {
        altitudes.clear();
        previousMedian = null;
        resetAggregated();
    }

    @Nullable
    public static AltitudeGainLoss select(@Nullable AltitudeGainLoss barometer,
                                           @Nullable AltitudeGainLoss gpsEstimate,
                                           boolean fallbackEnabled) {
        if (barometer != null) return barometer;
        return fallbackEnabled ? gpsEstimate : null;
    }
}
