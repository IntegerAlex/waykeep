package com.waykeep.app.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.waykeep.app.data.models.Distance;

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
}
