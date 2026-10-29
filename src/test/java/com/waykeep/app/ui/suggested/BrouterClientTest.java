package com.waykeep.app.ui.suggested;

import static org.junit.Assert.assertEquals;

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
}
