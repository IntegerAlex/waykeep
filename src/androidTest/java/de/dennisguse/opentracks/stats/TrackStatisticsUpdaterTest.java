package de.dennisguse.opentracks.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;

@RunWith(AndroidJUnit4.class)
public class TrackStatisticsUpdaterTest {

    @Deprecated //TODO SegmentStatisticsUpdater should always have data, right?
    @Test
    public void empty() {
        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        // then
        assertEquals(
                new Statistics(
                        null,
                        null,
                        Duration.ZERO,
                        Duration.ZERO,
                        Distance.ZERO,
                        Speed.ZERO,
                        null,
                        null,
                        null,
                        null
                ),
                subject.getSegmentStatisticUpdater().getStatistics()
        );
    }

    @Test
    public void startTime() {
        // given
        Instant startTime = Instant.parse("2021-10-24T23:00:00.000Z");
        TrackPoint tp = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, startTime);

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        subject.addTrackPoint(tp);

        // then
        // then
        assertEquals(
                new Statistics(
                        startTime,
                        startTime,
                        Duration.ZERO,
                        Duration.ZERO,
                        Distance.ZERO,
                        Speed.ZERO,
                        null,
                        null,
                        null,
                        null
                ),
                subject.getSegmentStatisticUpdater().getStatistics()
        );
    }

    @Test
    public void addTrackPoint_TestingTrack() {
        // given
        TestDataUtil.TrackData data = TestDataUtil.createTestingTrack(new Track.Id(1));

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();
        data.trackPoints().forEach(subject::addTrackPoint);

        // then
        Statistics result = subject.getSegmentStatisticUpdater().getStatistics();
        assertEquals(
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(13),
                        Duration.ofSeconds(12),
                        Duration.ofSeconds(12),
                        Distance.of(142.2637701034546),
                        Speed.of(11.855314175287882),
                        new AltitudeExtremities(2.5, 32.5),
                        new AltitudeGainLoss(36, 36),
                        HeartRate.of(106.833336f),
                        Power.of(405.2778f)
                ),
                result
        );
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving() {
        // given
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000));
        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000));
        tp3.setSpeed(Speed.of(5f));

        // when
        subject.addTrackPoint(tp1);
        subject.addTrackPoint(tp2);
        subject.addTrackPoint(tp3);

        // then
        assertEquals(Distance.of(1.1057428121566772), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving_and_sensor_moving() {
        // given
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000));
        tp2.setSpeed(Speed.of(5f));
        TrackPoint tp3 = createTrackPoint(0.001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000));
        tp3.setSpeed(Speed.of(5f));
        TrackPoint tp4 = createTrackPoint(0.001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000));
        tp4.setSpeed(Speed.of(5f));
        tp4.setSensorDistance(Distance.of(5f));
        TrackPoint tp5 = new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochMilli(5000));
        tp5.setSensorDistance(Distance.of(10f));

        // when
        subject.addTrackPoint(tp1);
        subject.addTrackPoint(tp2);
        subject.addTrackPoint(tp3);

        // then
        assertEquals(Distance.of(110.57427215576172), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(tp4);
        subject.addTrackPoint(tp5);

        // then
        assertEquals(Distance.of(125.57427215576172), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving_and_sensor_disconnecting() {
        // given
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000));
        tp2.setSpeed(Speed.of(5f));
        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000));
        tp3.setSensorDistance(Distance.of(5f));
        tp3.setSpeed(Speed.of(5f));
        TrackPoint tp4 = createTrackPoint(0.0005, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000));
        tp4.setSpeed(Speed.of(5f));
        TrackPoint tp5 = new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochMilli(5000));

        // when
        subject.addTrackPoint(tp1);
        subject.addTrackPoint(tp2);
        subject.addTrackPoint(tp3);

        // then
        assertEquals(Distance.of(5), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(tp4);
        subject.addTrackPoint(tp5);

        // then
        assertEquals(Distance.of(59.181396484375), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_maxSpeed_multiple_segments() {
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();
        assertEquals(Speed.of(0), subject.getSegmentStatisticUpdater().getStatistics().maxSpeed());

        subject.addTrackPoints(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1))
                        .setSpeed(Speed.of(2f)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2))
                        .setSpeed(Speed.of(2f)),
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(4))
        ));
        assertEquals(Speed.of(2), subject.getSegmentStatisticUpdater().getStatistics().maxSpeed());

        // when
        subject.addTrackPoints(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(5)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(6))
                        .setSpeed(Speed.of(1f)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(7))
                        .setSpeed(Speed.of(1f)),
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(8))
        ));

        // then
        assertEquals(Speed.of(2f), subject.getSegmentStatisticUpdater().getStatistics().maxSpeed());
    }

    @Test
    public void addTrackPoint_idle_withoutDistance() {
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        // when
        subject.addTrackPoints(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1))
                        .setSpeed(Speed.of(2f)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2))
                        .setSpeed(Speed.of(2f)),

                new TrackPoint(TrackPoint.Type.IDLE, Instant.ofEpochSecond(30)),
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(40))
                        .setHeartRate(50),
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(45))
                        .setHeartRate(50),
                createTrackPoint(0, 1, Altitude.WGS84.of(0), Instant.ofEpochSecond(50)),
                createTrackPoint(0, 2, Altitude.WGS84.of(0), Instant.ofEpochSecond(55)),

                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60))
        ));

        // then
        assertEquals(Duration.ofSeconds(35), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
    }

    @Test
    public void addTrackPoint_idle_withDistance() {
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        // when
        subject.addTrackPoints(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1))
                        .setSensorDistance(Distance.of(10)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2))
                        .setSensorDistance(Distance.of(10)),

                new TrackPoint(TrackPoint.Type.IDLE, Instant.ofEpochSecond(30))
                        .setSensorDistance(Distance.ofKilometer(1)),
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(40))
                        .setHeartRate(50),
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(45))
                        .setHeartRate(50),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(50))
                        .setSensorDistance(Distance.of(10)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(55))
                        .setSensorDistance(Distance.of(10)),

                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60))
        ));

        // then
        assertEquals(Duration.ofSeconds(40), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());

        assertEquals(Distance.of(1040), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_idle_remain_idle() {
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        // when
        subject.addTrackPoint(new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)));
        subject.addTrackPoint(
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(10))
                        .setSensorDistance(Distance.of(10)));

        subject.addTrackPoint(new TrackPoint(TrackPoint.Type.IDLE, Instant.ofEpochSecond(30)));
        // then
        assertTrue(subject.getSegmentStatisticUpdater().isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(30), subject.getSegmentStatisticUpdater().getStatistics().totalDuration());
        assertEquals(Distance.of(10), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(40))
                        .setSensorDistance(Distance.ZERO));
        // then
        assertTrue(subject.getSegmentStatisticUpdater().isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(40), subject.getSegmentStatisticUpdater().getStatistics().totalDuration());
        assertEquals(Distance.of(10), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(45))
                        .setSensorDistance(Distance.of(1)));
        // then
        assertTrue(subject.getSegmentStatisticUpdater().isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(45), subject.getSegmentStatisticUpdater().getStatistics().totalDuration());
        assertEquals(Distance.of(11), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(TrackPoint.Type.TRACKPOINT, Instant.ofEpochSecond(50))
                        .setSensorDistance(Distance.of(10)));
        // then
        assertFalse(subject.getSegmentStatisticUpdater().isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(50), subject.getSegmentStatisticUpdater().getStatistics().totalDuration());
        assertEquals(Distance.of(21), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());

        // when
        subject.addTrackPoint(new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60)));

        // then
        assertFalse(subject.getSegmentStatisticUpdater().isIdle());
        assertEquals(Duration.ofSeconds(40), subject.getSegmentStatisticUpdater().getStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(60), subject.getSegmentStatisticUpdater().getStatistics().totalDuration());
        assertEquals(Distance.of(21), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    @Test
    public void copy_constructor() {
        // given
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater();

        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000));
        tp2.setSpeed(Speed.of(5f));
        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000));
        tp3.setSpeed(Speed.of(5f));
        TrackPoint tp4 = createTrackPoint(0.0005, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000));
        tp4.setSpeed(Speed.of(5f));
        TrackPoint tp5 = new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochMilli(5000));

        subject.addTrackPoint(tp1);
        subject.addTrackPoint(tp2);
        subject.addTrackPoint(tp3);
        subject.addTrackPoint(tp4);

        // when
        TrackStatisticsUpdater copy = new TrackStatisticsUpdater(subject);
        subject.addTrackPoint(tp5);
        copy.addTrackPoint(tp5);

        // then
        assertEquals(Distance.of(55.28713929653168), subject.getSegmentStatisticUpdater().getStatistics().totalDistance());
    }

    public TrackPoint createTrackPoint(double latitude, double longitude, Altitude altitude, Instant time) {
        return new TrackPoint(TrackPoint.Type.TRACKPOINT,
                new Position(
                        time,
                        latitude,
                        longitude,
                        null,
                        altitude,
                        null,
                        null,
                        null
                ));
    }
}