package com.waykeep.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrackRecordedActivityCompactHeaderTest {

    @Test
    public void compactHeaderLineJoinsDateActivityAndDistance() {
        assertEquals("Today 07:14 · Road cycling · 5.20 km",
                TrackRecordedActivity.formatCompactHeaderLine("Today 07:14", "Road cycling", "5.20 km"));
    }

    @Test
    public void compactHeaderLineKeepsDistanceWhenActivityIsUnknown() {
        assertEquals("Today 07:14 · 5.20 km",
                TrackRecordedActivity.formatCompactHeaderLine("Today 07:14", "", "5.20 km"));
    }
}
