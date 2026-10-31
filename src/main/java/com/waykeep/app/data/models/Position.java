package com.waykeep.app.data.models;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

public record Position(
        @Nullable Instant time, //TODO It may be a good idea to move time out of Position
        @Nullable Double latitude,
        @Nullable Double longitude,
        @Nullable Distance horizontalAccuracy,
        @Nullable Altitude altitude,
        @Nullable Distance verticalAccuracy,
        @Nullable Float bearing,
        @Nullable Speed speed
) {

    /** Slower than this, and below the chip's speed uncertainty, is standing still. */
    private static final double STANDING_STILL_MAX_SPEED = 0.8;

    public static Position of(@NonNull Instant time) {
        return new Position(
                time,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static Position of(@NonNull Location location, @NonNull Instant time) {
        return new Position(
                time,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? Distance.of(location.getAccuracy()) : null,
                location.hasAltitude() ? Altitude.WGS84.of(location.getAltitude()) : null,
                location.hasVerticalAccuracy() ? Distance.of(location.getVerticalAccuracyMeters()) : null,
                location.hasBearing() ? location.getBearing() : null,
                speedOf(location)
        );
    }

    /**
     * A speed below its own uncertainty is indistinguishable from standing still. Reporting it as
     * movement makes a stationary receiver accumulate GPS drift as distance.
     */
    @Nullable
    private static Speed speedOf(@NonNull Location location) {
        if (!location.hasSpeed()) {
            return null;
        }
        return speedOf(location.getSpeed(), location.hasSpeedAccuracy(), location.getSpeedAccuracyMetersPerSecond());
    }

    @VisibleForTesting
    @NonNull
    static Speed speedOf(double metersPerSecond, boolean hasSpeedAccuracy, double speedAccuracyMetersPerSecond) {
        // Below the chip's own uncertainty and below a slow stroll: standing still. The absolute
        // floor keeps a walking fix with a poor speed estimate from being zeroed.
        if (hasSpeedAccuracy && metersPerSecond < STANDING_STILL_MAX_SPEED && metersPerSecond < speedAccuracyMetersPerSecond) {
            return Speed.of(0);
        }
        return Speed.of(metersPerSecond);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public boolean hasHorizontalAccuracy() {
        return horizontalAccuracy != null;
    }

    public boolean hasAltitude() {
        return altitude != null;
    }

    public boolean hasVerticalAccuracy() {
        return verticalAccuracy != null;
    }

    public boolean hasBearing() {
        return bearing != null;
    }

    public boolean hasSpeed() {
        return speed != null;
    }

    public Location toLocation() {
        if (!hasLocation()) {
            throw new RuntimeException("Cannot convert to Location.");
        }

        Location location = new Location("");
        if (time != null) {
            location.setTime(time.toEpochMilli());
        }
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        if (hasBearing()) {
            location.setBearing(bearing);
        }
        if (hasHorizontalAccuracy()) {
            location.setAccuracy((float) horizontalAccuracy.toM());
        }
        if (hasAltitude()) {
            location.setAltitude(altitude.toM());
        }
        if (hasSpeed()) {
            location.setSpeed((float) speed.toMPS());
        }

        return location;
    }

    /**
     * Checks if a given location is a valid (i.e. physically possible) location on Earth.
     *
     * @return true if the location is a valid location.
     */
    public boolean hasValidLocation() {
        return hasLocation()
                && Math.abs(latitude) <= 90
                && Math.abs(longitude) <= 180;
    }

    public boolean fulfillsAccuracy(Distance thresholdHorizontalAccuracy) {
        return hasHorizontalAccuracy() &&
                horizontalAccuracy
                        .lessThan(thresholdHorizontalAccuracy);

    }

    public Position with(Instant time) {
        return new Position(time, latitude, longitude, horizontalAccuracy, altitude, verticalAccuracy, bearing, speed);
    }

    public Position with(Altitude altitude) {
        return new Position(time, latitude, longitude, horizontalAccuracy, altitude, verticalAccuracy, bearing, speed);
    }

    public Position with(Speed speed) {
        return new Position(time, latitude, longitude, horizontalAccuracy, altitude, verticalAccuracy, bearing, speed);
    }

    public Position withCoordinates(Position position) {
        return new Position(time, position.latitude, position.longitude, horizontalAccuracy, altitude, verticalAccuracy, bearing, speed);
    }

    //TODO Use float
    public Position withBearing(Float bearing) {
        return new Position(time, latitude, longitude, horizontalAccuracy, altitude, verticalAccuracy, bearing, speed);
    }
}
