package com.waykeep.app.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.sensors.GpsStatusValue;
import com.waykeep.app.services.RecordingLiveStatus.LiveState;

public class RecordingLiveStatusTest {

    private static final String ACCURACY_FORMAT = "GPS accuracy ±%1$d m";

    @Test
    public void formatsOnlyKnownAccuracy() {
        assertNull(RecordingLiveStatus.formatAccuracy(null, ACCURACY_FORMAT));
        assertNull(RecordingLiveStatus.formatAccuracy(Distance.of(Double.NaN), ACCURACY_FORMAT));
        assertEquals("GPS accuracy ±12 m",
                RecordingLiveStatus.formatAccuracy(Distance.of(12.4), ACCURACY_FORMAT));
    }

    @Test
    public void warnsOnlyWhenBatteryIsKnownLowAndNotCharging() {
        assertEquals(-1, RecordingLiveStatus.warningBatteryPercent(-1, 100, false));
        assertEquals(-1, RecordingLiveStatus.warningBatteryPercent(10, 100, true));
        assertEquals(-1, RecordingLiveStatus.warningBatteryPercent(16, 100, false));
        assertEquals(-1, RecordingLiveStatus.warningBatteryPercent(151, 1000, false));
        assertEquals(15, RecordingLiveStatus.warningBatteryPercent(15, 100, false));
    }

    @Test
    public void lowBatteryWarningCanReenterAfterRecovery() {
        assertEquals(14, RecordingLiveStatus.warningBatteryPercent(14, 100, false));
        assertEquals(-1, RecordingLiveStatus.warningBatteryPercent(16, 100, false));
        assertEquals(15, RecordingLiveStatus.warningBatteryPercent(15, 100, false));
    }

    @Test
    public void liveStateComesFromServiceAndGps() {
        RecordingStatus recording = new RecordingStatus(new Track.Id(1));
        RecordingStatus paused = new RecordingStatus(new Track.Id(1), true);

        assertEquals(LiveState.RECORDING,
                RecordingLiveStatus.liveState(recording, GpsStatusValue.GPS_SIGNAL_FIX));
        assertEquals(LiveState.WAITING_FOR_GPS,
                RecordingLiveStatus.liveState(recording, GpsStatusValue.GPS_ENABLED));
        assertEquals(LiveState.WAITING_FOR_GPS,
                RecordingLiveStatus.liveState(recording, GpsStatusValue.GPS_SIGNAL_LOST));
        assertEquals(LiveState.WAITING_FOR_GPS, RecordingLiveStatus.liveState(recording, null));
        assertEquals(LiveState.PAUSED,
                RecordingLiveStatus.liveState(paused, GpsStatusValue.GPS_SIGNAL_FIX));
        assertEquals(LiveState.PAUSED, RecordingLiveStatus.liveState(paused, null));
    }
}
