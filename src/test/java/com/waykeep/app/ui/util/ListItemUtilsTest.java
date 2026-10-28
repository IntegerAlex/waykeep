package com.waykeep.app.ui.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The home row folds date, activity and marker count into one context line.
 * Pure string assembly, so it runs on the JVM without a device.
 */
public class ListItemUtilsTest {

    @Test
    public void buildContextLine_allParts() {
        assertEquals("Today 07:13 · Road cycling · 3 markers",
                ListItemUtils.buildContextLine("Today 07:13", "Road cycling", "3 markers"));
    }

    @Test
    public void buildContextLine_noMarkers() {
        assertEquals("Today 07:13 · Road cycling",
                ListItemUtils.buildContextLine("Today 07:13", "Road cycling", null));
    }

    @Test
    public void buildContextLine_dateOnly() {
        assertEquals("Today 07:13",
                ListItemUtils.buildContextLine("Today 07:13", "", null));
    }

    @Test
    public void buildContextLine_markersWithoutActivity() {
        assertEquals("Today 07:13 · 3 markers",
                ListItemUtils.buildContextLine("Today 07:13", null, "3 markers"));
    }
}
