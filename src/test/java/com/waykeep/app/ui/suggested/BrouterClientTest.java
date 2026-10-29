package com.waykeep.app.ui.suggested;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * The distance pills promise a length the road network may not allow.
 * The screen auto-selects the closest measured result instead of the first one back.
 */
public class BrouterClientTest {

    @Test
    public void bestMatchIndex_picksClosestToDesired() {
        assertEquals(0, BrouterClient.bestMatchIndex(List.of(8300.0, 8500.0, 13000.0), 3000.0));
        assertEquals(1, BrouterClient.bestMatchIndex(List.of(8300.0, 8500.0, 13000.0), 10000.0));
        assertEquals(1, BrouterClient.bestMatchIndex(List.of(2100.0, 4800.0, 9000.0), 5000.0));
    }

    @Test
    public void bestMatchIndex_singleOption() {
        assertEquals(0, BrouterClient.bestMatchIndex(List.of(8300.0), 3000.0));
    }

    @Test
    public void bestMatchIndex_emptyList() {
        assertEquals(-1, BrouterClient.bestMatchIndex(List.of(), 3000.0));
    }

    @Test
    public void distanceTolerance_isHard500mBoundary() {
        assertEquals(true, BrouterClient.isWithinDistanceTolerance(3500, 3000));
        assertEquals(true, BrouterClient.isWithinDistanceTolerance(2500, 3000));
        assertEquals(false, BrouterClient.isWithinDistanceTolerance(3501, 3000));
        assertEquals(false, BrouterClient.isWithinDistanceTolerance(2499, 3000));
    }

    @Test
    public void adjustedRoundtripRadius_scalesFromMeasuredRoute() {
        assertEquals(750.0 * 3000.0 / 8300.0,
                BrouterClient.adjustRoundtripRadius(750, 3000, 8300), 0.001);
        assertEquals(100.0, BrouterClient.adjustRoundtripRadius(500, 3000, 30000), 0.0);
    }

    @Test
    public void initialRoundtripRadius_allowsSmallLoops() {
        assertEquals(750.0, BrouterClient.initialRoundtripRadius(3000), 0.0);
        assertEquals(100.0, BrouterClient.initialRoundtripRadius(100), 0.0);
    }

    @Test
    public void requestUrl_usesBroutersCaseSensitiveRoundTripParameter() {
        String url = BrouterClient.buildRequestUrl(77.2, 28.6, 750, 30, "trekking");
        assertTrue(url.contains("&roundTripDistance=750"));
        assertFalse(url.contains("&roundtripDistance="));
    }
}
