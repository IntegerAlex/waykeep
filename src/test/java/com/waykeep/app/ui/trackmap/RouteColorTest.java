package com.waykeep.app.ui.trackmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class RouteColorTest {

    @Test
    public void missingPreferenceDefaultsToCyan() {
        assertEquals(RouteColor.CYAN, RouteColor.fromPreference(null));
        assertEquals(RouteColor.CYAN, RouteColor.fromPreference(""));
    }

    @Test
    public void allSixValuesParse() {
        assertEquals(RouteColor.CYAN, RouteColor.fromPreference("CYAN"));
        assertEquals(RouteColor.GREEN, RouteColor.fromPreference("GREEN"));
        assertEquals(RouteColor.ORANGE, RouteColor.fromPreference("ORANGE"));
        assertEquals(RouteColor.MAGENTA, RouteColor.fromPreference("MAGENTA"));
        assertEquals(RouteColor.BLUE, RouteColor.fromPreference("BLUE"));
        assertEquals(RouteColor.WHITE, RouteColor.fromPreference("WHITE"));
    }

    @Test
    public void lowercaseAndMixedCaseValuesParse() {
        assertEquals(RouteColor.GREEN, RouteColor.fromPreference("green"));
        assertEquals(RouteColor.ORANGE, RouteColor.fromPreference("OrAnGe"));
    }

    @Test
    public void unknownValueFallsBackToCyan() {
        assertEquals(RouteColor.CYAN, RouteColor.fromPreference("purple"));
        assertEquals(RouteColor.CYAN, RouteColor.fromPreference("  "));
    }

    @Test
    public void everyOptionHasADistinctColorResource() {
        Set<Integer> resources = new HashSet<>();
        for (RouteColor color : RouteColor.values()) {
            assertNotEquals(0, color.colorRes());
            resources.add(color.colorRes());
        }
        assertEquals(RouteColor.values().length, resources.size());
    }
}
