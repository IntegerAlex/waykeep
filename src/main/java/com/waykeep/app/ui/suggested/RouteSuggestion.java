package com.waykeep.app.ui.suggested;

import org.osmdroid.util.GeoPoint;

import java.util.List;

/**
 * One generated loop: the route points plus its measured totals.
 */
public record RouteSuggestion(List<GeoPoint> points, double distanceM, double gainM) {
}
