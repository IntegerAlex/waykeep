package com.waykeep.app.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.TrackPoint;

public class TrackRecordingManagerTest {

    @Test
    public void connectsFirstGpsFixToProvisionalStartWithinCap() {
        assertTrue(TrackRecordingManager.connectsProvisionalStart(true, Distance.of(296)));
    }

    @Test
    public void breaksWhenFirstGpsFixIsBeyondTheCap() {
        assertFalse(TrackRecordingManager.connectsProvisionalStart(true, Distance.of(1001)));
    }

    @Test
    public void doesNotChangeNormalGapHandling() {
        assertFalse(TrackRecordingManager.connectsProvisionalStart(false, Distance.of(50)));
    }

    @Test
    public void hopWithinTheNetworkFixAccuracyIsNoise() {
        assertTrue(TrackRecordingManager.provisionalStartHopIsNoise(true, Distance.of(100), Distance.of(21.9)));
    }

    @Test
    public void hopBeyondTheNetworkFixAccuracyIsMovement() {
        assertFalse(TrackRecordingManager.provisionalStartHopIsNoise(true, Distance.of(100), Distance.of(296)));
    }

    @Test
    public void hopWithoutProvisionalStartIsNeverNoise() {
        assertFalse(TrackRecordingManager.provisionalStartHopIsNoise(false, Distance.of(100), Distance.of(5)));
    }

    @Test
    public void stationaryTrackPointIsSkipped() {
        assertTrue(TrackRecordingManager.isStationary(trackPoint(TrackPoint.Type.TRACKPOINT, Speed.of(0))));
    }

    @Test
    public void movingTrackPointIsRecorded() {
        assertFalse(TrackRecordingManager.isStationary(trackPoint(TrackPoint.Type.TRACKPOINT, Speed.of(1.4))));
    }

    @Test
    public void idleTrackPointIsNeverSkipped() {
        assertFalse(TrackRecordingManager.isStationary(trackPoint(TrackPoint.Type.IDLE, Speed.of(0))));
    }

    @Test
    public void lapStartKeepsTheLocationAndTimeOfTheLapBoundary() {
        Instant now = Instant.parse("2026-10-31T10:05:00Z");
        Position position = new Position(now, 18.5, 73.8, Distance.of(5), null, null, null, Speed.of(2.5));

        TrackPoint start = TrackRecordingManager.createLapStart(
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, position), null);

        assertEquals(TrackPoint.Type.SEGMENT_START_MANUAL, start.type());
        assertEquals(now, start.getTime());
        assertEquals(18.5, start.position().latitude(), 0);
        assertEquals(73.8, start.position().longitude(), 0);
        // Cumulative sensor data belongs to the lap that just ended.
        assertNull(start.sensorDistance());
        assertNull(start.altitudeGainLoss());
    }

    @Test
    public void lapStartFallsBackToTheLastStoredLocationWithoutAFix() {
        Instant now = Instant.parse("2026-10-31T10:05:00Z");
        TrackPoint lastStored = trackPoint(TrackPoint.Type.TRACKPOINT, Speed.of(1.4));

        TrackPoint start = TrackRecordingManager.createLapStart(
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Position.of(now)), lastStored);

        assertEquals(TrackPoint.Type.SEGMENT_START_MANUAL, start.type());
        assertEquals(now, start.getTime());
        assertEquals(18.5, start.position().latitude(), 0);
        assertEquals(73.8, start.position().longitude(), 0);
    }

    private static TrackPoint trackPoint(TrackPoint.Type type, Speed speed) {
        Position position = new Position(Instant.parse("2026-10-31T10:00:00Z"),
                18.5, 73.8, Distance.of(15), null, null, null, speed);
        return new TrackPoint(type, position);
    }
}
