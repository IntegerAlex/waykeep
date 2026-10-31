package com.waykeep.app.sensors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GpsHandlerTest {

    @Test
    public void logsFirstSatelliteStatus() {
        assertTrue(GpsHandler.shouldLogGnssStatus(-1, -1, 0, 0, 0));
    }

    @Test
    public void skipsUnchangedSatelliteStatus() {
        assertFalse(GpsHandler.shouldLogGnssStatus(12, 4, 12, 4, 60_000));
    }

    @Test
    public void skipsChangedSatelliteStatusWithinThrottleWindow() {
        assertFalse(GpsHandler.shouldLogGnssStatus(0, 0, 12, 0, 1_000));
    }

    @Test
    public void logsChangedSatelliteStatusAfterThrottleWindow() {
        assertTrue(GpsHandler.shouldLogGnssStatus(0, 0, 12, 0, GpsHandler.GNSS_STATUS_MIN_INTERVAL_MS));
    }
}
