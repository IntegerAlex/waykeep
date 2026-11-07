package com.waykeep.app.io.file.importer;

/**
 * A coordinate read from a GPX track or route, without timing information.
 */
public record RoutePoint(double latitude, double longitude) {
}
