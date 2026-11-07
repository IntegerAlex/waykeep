package com.waykeep.app.ui.intervals;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Power;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.data.statistics.TrackStatisticsUpdater;

public class IntervalStatisticsUpdater {
    private TrackStatisticsUpdater trackStatisticsUpdater;
    private final List<Interval> intervalList;
    private final Distance distanceInterval;
    private final RecentWindow recentWindow;
    private Interval interval, lastInterval;
    private TrackPoint.Id lastTrackPointId;

    /**
     * @param distanceInterval distance of every interval.
     */
    public IntervalStatisticsUpdater(Distance distanceInterval) {
        this(distanceInterval, distanceInterval);
    }

    public IntervalStatisticsUpdater(Distance distanceInterval, Distance recentWindowDistance) {
        this.distanceInterval = distanceInterval;
        recentWindow = new RecentWindow(recentWindowDistance);

        interval = new Interval();
        lastInterval = new Interval();
        intervalList = new ArrayList<>();
        intervalList.add(lastInterval);
    }

    /**
     * Complete intervals with the tracks points from the iterator.
     *
     * @return the last track point's id used to compute the intervals.
     */
    public TrackPoint.Id addTrackPoints(Iterator<TrackPoint> trackPointIterator) {
        boolean newIntervalAdded = false;
        TrackPoint trackPoint = null;

        while (trackPointIterator.hasNext()) {
            trackPoint = trackPointIterator.next();
            if (lastTrackPointId != null && lastTrackPointId.equals(trackPoint.id())) {
                continue;
            }
            lastTrackPointId = trackPoint.id();
            recentWindow.add(trackPoint);
            if (trackStatisticsUpdater == null) {
                trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoint);
            } else {
                trackStatisticsUpdater.addTrackPoint(trackPoint);
            }

            if (trackStatisticsUpdater.getTrackStatistics().totalDistance().plus(interval.distance).greaterOrEqualThan(distanceInterval)) {
                interval.add(trackStatisticsUpdater.getTrackStatistics(), trackPoint);

                double adjustFactor = distanceInterval.dividedBy(interval.distance);
                Interval adjustedInterval = new Interval(interval, adjustFactor);

                intervalList.set(intervalList.size() - 1, adjustedInterval);

                interval = new Interval(interval.distance.minus(adjustedInterval.distance), interval.time.minus(adjustedInterval.time));
                trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoint);

                lastInterval = new Interval(interval);
                intervalList.add(lastInterval);

                newIntervalAdded = true;
            }
        }

        if (trackStatisticsUpdater != null) {
            if (newIntervalAdded) {
                lastInterval.add(trackStatisticsUpdater.getTrackStatistics(), null);
            } else {
                lastInterval.set(trackStatisticsUpdater.getTrackStatistics());
            }
        }

        return trackPoint != null ? trackPoint.id() : null;
    }

    public List<Statistics> getIntervalList() {
        return intervalList.stream().map(Interval::toStatistics).toList();
    }

    /**
     * Return the last completed interval.
     * An interval is complete if its distance is equal to distanceInterval_m.
     *
     * @return the interval object or null if any interval is completed.
     */
    @Nullable
    public Statistics getLastInterval() {
        if (intervalList.size() == 1 && intervalList.get(0).distance.lessThan(distanceInterval)) {
            return null;
        }

        for (int i = intervalList.size() - 1; i >= 0; i--) {
            if (intervalList.get(i).distance.greaterOrEqualThan(distanceInterval)) {
                return this.intervalList.get(i).toStatistics();
            }
        }

        return null;
    }

    /**
     * Returns the average speed over the latest full distance window, or null if the track has
     * not covered that distance since its most recent segment break.
     * #2039: Time-triggered lap pace must not fall back to an older completed interval.
     */
    @Nullable
    public Speed getRecentWindowSpeed() {
        return recentWindow.getSpeed();
    }

    private static final class RecentWindow {
        private final double windowMeters;
        private final Deque<Edge> edges = new ArrayDeque<>();
        private TrackPoint lastTrackPoint;
        private double distanceMeters;

        private RecentWindow(Distance distance) {
            windowMeters = distance.toM();
        }

        private void add(TrackPoint point) {
            if (point.isSegmentManualEnd()) {
                clear();
                return;
            }
            if (point.isSegmentManualStart() || point.type() == TrackPoint.Type.SEGMENT_START_AUTOMATIC) {
                clear();
                lastTrackPoint = point;
                return;
            }

            if (lastTrackPoint != null) {
                Duration duration = Duration.between(lastTrackPoint.getTime(), point.getTime());
                if (duration.isNegative()) {
                    clear();
                } else {
                    Distance distance = point.sensorDistance();
                    if (distance == null && lastTrackPoint.position().hasLocation()
                            && point.position().hasLocation()) {
                        distance = point.distanceToPrevious(lastTrackPoint);
                    }
                    double deltaMeters = distance != null && Double.isFinite(distance.toM())
                            ? Math.max(0, distance.toM()) : 0;
                    edges.addLast(new Edge(deltaMeters, duration));
                    distanceMeters += deltaMeters;
                    trim();
                }
            }
            lastTrackPoint = point;
        }

        private void trim() {
            while (!edges.isEmpty() && distanceMeters - edges.peekFirst().distanceMeters >= windowMeters) {
                distanceMeters -= edges.removeFirst().distanceMeters;
            }
        }

        @Nullable
        private Speed getSpeed() {
            if (windowMeters <= 0 || distanceMeters < windowMeters) {
                return null;
            }

            double remainingMeters = windowMeters;
            Duration duration = Duration.ZERO;
            Iterator<Edge> iterator = edges.descendingIterator();
            while (iterator.hasNext() && remainingMeters > 0) {
                Edge edge = iterator.next();
                if (edge.distanceMeters == 0) {
                    duration = duration.plus(edge.duration);
                    continue;
                }

                double usedMeters = Math.min(edge.distanceMeters, remainingMeters);
                duration = duration.plusMillis(Math.round(edge.duration.toMillis() * usedMeters / edge.distanceMeters));
                remainingMeters -= usedMeters;
            }
            return remainingMeters > 0 || duration.isZero()
                    ? null
                    : Speed.of(Distance.of(windowMeters), duration);
        }

        private void clear() {
            edges.clear();
            lastTrackPoint = null;
            distanceMeters = 0;
        }

        private record Edge(double distanceMeters, Duration duration) { }
    }

    //TODO Could be replaced with Statistics?
    @Deprecated
    private static class Interval {
        private Distance distance = Distance.ZERO;
        private Duration time = Duration.ZERO;
        private Float gain_m;
        private Float loss_m;
        private AltitudeGainLoss.Source altitudeGainLossSource = AltitudeGainLoss.Source.UNKNOWN;
        private HeartRate avgHeartRate;
        private Power avgPower;

        public Interval() {
        }

        public Interval(Distance distance, Duration time) {
            this.distance = distance;
            this.time = time;
        }

        public Interval(Interval i, double adjustFactor) {
            distance = i.distance.multipliedBy(adjustFactor);
            time = Duration.ofMillis((long) (i.time.toMillis() * adjustFactor));
            gain_m = i.gain_m;
            loss_m = i.loss_m;
            altitudeGainLossSource = i.altitudeGainLossSource;
            avgHeartRate = i.avgHeartRate;
            avgPower = i.avgPower;
        }

        public Interval(Interval i) {
            distance = i.distance;
            time = i.time;
            gain_m = i.gain_m;
            loss_m = i.loss_m;
            altitudeGainLossSource = i.altitudeGainLossSource;
            avgHeartRate = i.avgHeartRate;
            avgPower = i.avgPower;
        }

        private void add(Statistics trackStatistics, @Nullable TrackPoint lastTrackPoint) {
            distance = distance.plus(trackStatistics.totalDistance());
            time = time.plus(trackStatistics.totalDuration());
            if (trackStatistics.altitudeGainLoss() != null) {
                gain_m = trackStatistics.altitudeGainLoss().gain_m();
                loss_m = trackStatistics.altitudeGainLoss().loss_m();
                altitudeGainLossSource = trackStatistics.altitudeGainLoss().source();
            }
            avgHeartRate = trackStatistics.avgHeartRate();
            avgPower = trackStatistics.avgPower();
            if (lastTrackPoint == null) {
                return;
            }
            if (gain_m != null && lastTrackPoint.altitudeGainLoss() != null) {
                gain_m = gain_m - lastTrackPoint.altitudeGainLoss().gain_m();
                loss_m = loss_m - lastTrackPoint.altitudeGainLoss().loss_m();
            }
        }

        private void set(Statistics trackStatistics) {
            distance = trackStatistics.totalDistance();
            time = trackStatistics.totalDuration();
            if (trackStatistics.altitudeGainLoss() != null) {
                gain_m = trackStatistics.altitudeGainLoss().gain_m();
                loss_m = trackStatistics.altitudeGainLoss().loss_m();
                altitudeGainLossSource = trackStatistics.altitudeGainLoss().source();
            }
            avgHeartRate = trackStatistics.avgHeartRate();
            avgPower = trackStatistics.avgPower();
        }

        private Statistics toStatistics() {
            return new Statistics(
                    null,
                    null,
                    time,
                    null,
                    distance,
                    null,
                    null,
                    gain_m != null ? new AltitudeGainLoss(gain_m, loss_m, altitudeGainLossSource) : null,
                    avgHeartRate,
                    avgPower
            );
        }
    }
}
