package de.dennisguse.opentracks.ui.intervals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.stats.TrackStatisticsUpdater;

@RunWith(JUnit4.class)
public class IntervalStatisticsUpdaterTest {

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_1() {
        // With 50 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        whenAndThen(50, distanceInterval);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_2() {
        // With 200 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        whenAndThen(200, distanceInterval);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_3() {
        // With 200 points and interval distance of 3000m.

        // given
        float distanceInterval = 3000f;

        // when and then
        whenAndThen(3000, distanceInterval);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_4() {
        // With 1000 points and interval distance of 3000m.

        // given
        float distanceInterval = 3000f;

        // when and then
        whenAndThen(1000, distanceInterval);
    }

    /**
     * Tests that build method compute the distance correctly comparing the result with TrackStatisticsUpdater result.
     */
    @Test
    public void testBuild_5() {
        // With 10000 points and interval distance of 1000m.

        // given
        float distanceInterval = 1000f;

        // when and then
        whenAndThen(10000, distanceInterval);
    }

    @Test
    public void testWithNoLossTrackPoints() {
        // TrackPoints with elevation gain but without elevation loss.

        // given
        float distanceInterval = 1000f;
        int numberOfPoints = 10000;

        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < numberOfPoints; i++) {
            trackPoints.add(TestDataUtil.createTrackPoint(i).setAltitudeGainLoss(null));
        }
        TrackStatisticsUpdater trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoints);

        TrackBuilder dummyTrack = new TrackBuilder(trackStatisticsUpdater.getTrackStatistics());
        dummyTrack.setId(new Track.Id(System.currentTimeMillis()));
        dummyTrack.setName("Dummy Track Without Elevation Loss");

        Pair<TrackBuilder, List<TrackPoint>> trackWithStats = new Pair<>(dummyTrack, trackPoints);

        // when and then
        whenAndThen(trackWithStats.first, trackWithStats.second, numberOfPoints, distanceInterval);
    }

    private void whenAndThen(int numberOfPoints, float distanceInterval) {
        Pair<TrackBuilder, List<TrackPoint>> trackWithStats = buildTrackWithTrackPoints(numberOfPoints);
        whenAndThen(trackWithStats.first, trackWithStats.second, numberOfPoints, distanceInterval);
    }

    private void whenAndThen(TrackBuilder track, List<TrackPoint> trackPoints, int numberOfPoints, float distanceInterval) {
        IntervalStatisticsUpdater intervalStatistics = new IntervalStatisticsUpdater(Distance.of(distanceInterval));

        intervalStatistics.addTrackPoints(trackPoints.iterator());

        List<Statistics> intervalList = intervalStatistics.getIntervalList();
        Distance totalDistance = Distance.ZERO;
        float totalTime = 0L;
        Float totalGain = null;
        Float totalLoss = null;
        for (Statistics i : intervalList) {
            totalDistance = totalDistance.plus(i.totalDistance());
            totalTime += i.totalDistance().toM() / i.getAverageSpeed().toMPS();

            if (totalGain == null) {
                totalGain = i.altitudeGainLoss() != null ? i.altitudeGainLoss().gain_m() : null;
            } else if (i.altitudeGainLoss() != null) {
                totalGain += i.altitudeGainLoss().gain_m();
            }

            if (totalLoss == null) {
                totalLoss = i.altitudeGainLoss() != null ? i.altitudeGainLoss().loss_m() : null;
            } else if (i.altitudeGainLoss() != null) {
                totalLoss += i.altitudeGainLoss().loss_m();
            }
        }

        // then
        assertEquals(track.getStatistics().totalDuration().toSeconds(), totalTime, 0.01);
        assertEquals(track.getStatistics().totalDistance().toM(), totalDistance.toM(), 0.01);
        assertEquals(intervalList.size(), (int) Math.ceil(track.getStatistics().totalDistance().toM() / distanceInterval));
        if (totalGain != null && totalLoss != null) {
            assertEquals(totalGain, numberOfPoints * TestDataUtil.ALTITUDE_GAIN, 0.1);
            assertEquals(totalLoss, numberOfPoints * TestDataUtil.ALTITUDE_LOSS, 0.1);

        } else {
            assertTrue(intervalStatistics.getIntervalList().stream().noneMatch(i -> i.altitudeGainLoss() != null));
        }

        for (int i = 0; i < intervalList.size() - 1; i++) {
            assertEquals(intervalList.get(i).totalDistance().toM(), distanceInterval, 0.001);
            totalDistance = totalDistance.minus(intervalList.get(i).totalDistance());
        }
        assertEquals(intervalList.get(intervalList.size() - 1).totalDistance().toM(), totalDistance.toM(), 0.01);
    }

    private static Pair<TrackBuilder, List<TrackPoint>> buildTrackWithTrackPoints(int numberOfPoints) {
        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < numberOfPoints; i++) {
            trackPoints.add(TestDataUtil.createTrackPoint(i));
        }
        TrackStatisticsUpdater trackStatisticsUpdater = new TrackStatisticsUpdater(trackPoints);

        return new Pair<>(new TrackBuilder(trackStatisticsUpdater.getTrackStatistics()), trackPoints);
    }
}