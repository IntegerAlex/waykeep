/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;

import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Statistical data about a {@link Track}.
 * The data in this class should be filled out by {@link TrackStatisticsUpdater}.
 *
 * @author Rodrigo Damazio
 */
//TODO Check that data ranges are valid (not less than zero etc.)
public class SegmentStatisticUpdater {

    // The min and max altitude (meters) seen on this track.
    private final ExtremityMonitor altitudeExtremities = new ExtremityMonitor();

    // The track start time.
    private Instant startTime; //TODO Should never be null!
    // The track stop time.
    private Instant stopTime; //TODO Should never be null!

    private Distance totalDistance;
    /**
     * Updated when new points are received, may be stale.
     * This statistic is only updated when a new point is added to the statistics, so it may be off.
     * If you need to calculate the proper totalTime, use startTime with the current time.
     */
    private Duration totalTime;
    // Based on when we believe the user is traveling.
    private Duration movingTime;
    // The maximum speed (meters/second) that we believe is valid.
    private Speed maxSpeed;
    private Float totalAltitudeGain_m = null;
    private Float totalAltitudeLoss_m = null;
    // The average heart rate seen on this track
    private HeartRate avgHeartRate = null;
    private Power avgPower = null;

    private boolean isIdle;

    public SegmentStatisticUpdater() {
        reset();
    }

    /**
     * Copy constructor.
     *
     * @param other another statistics data object to copy from
     */
    public SegmentStatisticUpdater(SegmentStatisticUpdater other) {
        this(other.getStatistics());
        this.isIdle = other.isIdle();
    }

    public SegmentStatisticUpdater(Statistics statistics) {
        startTime = statistics.startTime();
        stopTime = statistics.stopTime();
        totalDistance = statistics.totalDistance();
        totalTime = statistics.totalTime();
        movingTime = statistics.movingTime();
        maxSpeed = statistics.maxSpeed();
        if (statistics.altitudeExtremities() != null) {
            altitudeExtremities.set(statistics.altitudeExtremities().min_m(), statistics.altitudeExtremities().max_m());
        }
        if (statistics.altitudeGainLoss() != null) {
            totalAltitudeGain_m = statistics.altitudeGainLoss().gain_m();
            totalAltitudeLoss_m = statistics.altitudeGainLoss().loss_m();
        }
        avgHeartRate = statistics.avgHeartRate();
        avgPower = statistics.avgPower();
    }

    /**
     * Combines these statistics with those from another object.
     * This assumes that the time periods covered by each do not intersect.
     *
     * @param other another statistics data object
     */
    //TODO Should be refactored to append only [mainly due to isIdle] (NOTE: This requires to use a custom value object for AggregatedStatistics; this is anyhow recommended).
    public void merge(SegmentStatisticUpdater other) {
        if (startTime == null) {
            startTime = other.startTime;
        } else {
            startTime = startTime.isBefore(other.startTime) ? startTime : other.startTime;
        }
        if (stopTime == null) {
            stopTime = other.stopTime;
        } else {
            stopTime = stopTime.isAfter(other.stopTime) ? stopTime : other.stopTime;
        }

        isIdle = other.isIdle; //TODO This implicitly assumes append mode.

        if (avgHeartRate == null) {
            avgHeartRate = other.avgHeartRate;
        } else {
            if (other.avgHeartRate != null) {
                // Using total time as weights for the averaging.
                // Important to do this before total time is updated
                avgHeartRate = HeartRate.of(
                        (totalTime.getSeconds() * avgHeartRate.getBPM() + other.totalTime.getSeconds() * other.avgHeartRate.getBPM())
                                / (totalTime.getSeconds() + other.totalTime.getSeconds())
                );
            }
        }

        if (avgPower == null) {
            avgPower = other.avgPower;
        } else {
            if (other.avgPower != null) {
                // Using total time as weights for the averaging.
                // Important to do this before total time is updated
                avgPower = Power.of(
                        (totalTime.getSeconds() * avgPower.getW() + other.totalTime.getSeconds() * other.avgPower.getW())
                                / (totalTime.getSeconds() + other.totalTime.getSeconds())
                );
            }
        }

        totalDistance = totalDistance.plus(other.totalDistance);
        totalTime = totalTime.plus(other.totalTime);
        movingTime = movingTime.plus(other.movingTime);
        maxSpeed = Speed.max(maxSpeed, other.maxSpeed);
        if (other.altitudeExtremities.hasData()) {
            altitudeExtremities.update(other.altitudeExtremities.getMin());
            altitudeExtremities.update(other.altitudeExtremities.getMax());
        }
        if (totalAltitudeGain_m == null) {
            if (other.totalAltitudeGain_m != null) {
                totalAltitudeGain_m = other.totalAltitudeGain_m;
            }
        } else {
            if (other.totalAltitudeGain_m != null) {
                totalAltitudeGain_m += other.totalAltitudeGain_m;
            }
        }
        if (totalAltitudeLoss_m == null) {
            if (other.totalAltitudeLoss_m != null) {
                totalAltitudeLoss_m = other.totalAltitudeLoss_m;
            }
        } else {
            if (other.totalAltitudeLoss_m != null) {
                totalAltitudeLoss_m += other.totalAltitudeLoss_m;
            }
        }
    }

    public boolean isInitialized() {
        return startTime != null;
    }

    public void reset() {
        startTime = null;
        stopTime = null;

        totalTime = Duration.ofSeconds(0);
        movingTime = Duration.ofSeconds(0);
        totalDistance = Distance.of(0);
        maxSpeed = Speed.zero();
        totalAltitudeGain_m = null;
        totalAltitudeLoss_m = null;

        isIdle = false;
    }

    public void reset(Instant startTime) {
        reset();
        setStartTime(startTime);
    }

    public Statistics getStatistics() {
        return new Statistics(
                startTime,
                stopTime,
                totalTime,
                movingTime,

                totalDistance,
                getMaxSpeed(),
                altitudeExtremities.hasData() ? new AltitudeExtremities(altitudeExtremities.getMin(), altitudeExtremities.getMax()) : null,
                totalAltitudeGain_m != null && totalAltitudeLoss_m != null ? new AltitudeGainLoss(totalAltitudeGain_m, totalAltitudeLoss_m) : null,
                avgHeartRate,
                avgPower
        );
    }

    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Should only be called on start.
     */
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        setStopTime(startTime);
    }

    public Instant getStopTime() {
        return stopTime;
    }

    public void setStopTime(Instant stopTime) {
        if (stopTime.isBefore(startTime)) {
            // Time must be monotonically increasing, but we might have events at the same point in time (BLE and GPS)
            throw new RuntimeException("stopTime cannot be less than startTime: " + startTime + " " + stopTime);
        }
        this.stopTime = stopTime;
    }

    public Distance getTotalDistance() {
        return totalDistance;
    }

    @VisibleForTesting
    public void setTotalDistance(Distance totalDistance_m) {
        this.totalDistance = totalDistance_m;
    }

    public void addTotalDistance(Distance distance_m) {
        totalDistance = totalDistance.plus(distance_m);
    }

    public Duration getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(Duration totalTime) {
        this.totalTime = totalTime;
    }

    public Duration getMovingTime() {
        return movingTime;
    }

    public void setMovingTime(Duration movingTime) {
        this.movingTime = movingTime;
    }

    public void addMovingTime(TrackPoint trackPoint, TrackPoint lastTrackPoint) {
        addMovingTime(Duration.between(lastTrackPoint.getTime(), trackPoint.getTime()));
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void addMovingTime(Duration time) {
        if (time.isNegative()) {
            throw new RuntimeException("Moving time cannot be negative");
        }
        movingTime = movingTime.plus(time);
    }

    public boolean isIdle() {
        return isIdle;
    }

    public void setIdle(boolean idle) {
        isIdle = idle;
    }

    @Nullable
    public HeartRate getAverageHeartRate() {
        return avgHeartRate;
    }

    @Nullable
    public Power getAveragePower() {
        return avgPower;
    }

    /**
     * Gets the average speed.
     * This calculation only takes into account the displacement until the last point that was accounted for in statistics.
     */
    public Speed getAverageSpeed() {
        if (totalTime.isZero()) {
            return Speed.of(0);
        }
        return Speed.of(totalDistance.toM() / totalTime.getSeconds());
    }

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingTime);
    }

    public Speed getMaxSpeed() {
        return Speed.max(maxSpeed, getAverageMovingSpeed());
    }

    public void setMaxSpeed(Speed maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getMinAltitude() {
        return altitudeExtremities.getMin();
    }

    public void setMinAltitude(double altitude_m) {
        altitudeExtremities.setMin(altitude_m);
    }

    /**
     * Gets the maximum altitude.
     * This is calculated from the smoothed altitude, so this can actually be less than the current altitude.
     */
    public double getMaxAltitude() {
        return altitudeExtremities.getMax();
    }

    public void setMaxAltitude(double altitude_m) {
        altitudeExtremities.setMax(altitude_m);
    }

    public void updateAltitudeExtremities(Altitude altitude) {
        if (altitude != null) {
            altitudeExtremities.update(altitude.toM());
        }
    }

    public void setAverageHeartRate(HeartRate heartRate) {
        if (heartRate != null) {
            avgHeartRate = heartRate;
        }
    }

    public void setAveragePower(Power power) {
        if (power != null) {
            avgPower = power;
        }
    }

    @VisibleForTesting
    @Deprecated
    @Nullable
    public Float getTotalAltitudeGain() {
        return totalAltitudeGain_m;
    }

    @VisibleForTesting
    @Deprecated
    public void setTotalAltitudeGain(Float totalAltitudeGain_m) {
        this.totalAltitudeGain_m = totalAltitudeGain_m;
    }

    @VisibleForTesting
    @Deprecated
    @Nullable
    public Float getTotalAltitudeLoss() {
        return totalAltitudeLoss_m;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void addTotalAltitudeGainLoss(AltitudeGainLoss altitudeGainLoss) {
        if (totalAltitudeGain_m == null) {
            totalAltitudeGain_m = 0f;
        }
        totalAltitudeGain_m += altitudeGainLoss.gain_m();

        if (totalAltitudeLoss_m == null) {
            totalAltitudeLoss_m = 0f;
        }
        totalAltitudeLoss_m += altitudeGainLoss.loss_m();
    }

    @NonNull
    @Override
    public String toString() {
        return "SegmentStatisticUpdater{" + getStatistics() + "}";
    }
}