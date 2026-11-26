package com.waykeep.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrackListProgressCopyTest {

    @Test
    public void goalLineNamesDistancesInsteadOfAPercentage() {
        String[] lines = TrackListActivity.formatInsightLines(
                "%1$s of %2$s", "4-week average: %1$s/week",
                "3.2 km", "20.0 km", "12.4 km");

        assertEquals("3.2 km of 20.0 km", lines[0]);
    }

    @Test
    public void averageLineUsesTheFullFourWeekWording() {
        String[] lines = TrackListActivity.formatInsightLines(
                "%1$s of %2$s", "4-week average: %1$s/week",
                "3.2 km", "20.0 km", "12.4 km");

        assertEquals("4-week average: 12.4 km/week", lines[1]);
    }
}
