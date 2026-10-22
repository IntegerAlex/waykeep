package com.waykeep.app.services.handlers;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.core.location.LocationCompat;
import androidx.core.location.altitude.AltitudeConverterCompat;

import java.io.IOException;

import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.TrackPoint;

/**
 * More infos regarding Android 34's <a href="https://issuetracker.google.com/issues/195660815#comment1">AltitudeConverter</a>.
 */
public class AltitudeCorrectionManager {

    private static final String TAG = AltitudeCorrectionManager.class.getSimpleName();

    // The caller is the main thread; the conversion must never block it indefinitely.
    private static final long ALTITUDE_CORRECTION_TIMEOUT_MS = 2000;

    public TrackPoint correctAltitude(Context context, TrackPoint trackPoint) {
        if (!trackPoint.position().hasLocation() || !trackPoint.position().hasAltitude()) {
            return trackPoint;
        }

        final TrackPoint[] newTrackPoint = {null}; //TODO this is kind of ugly
        // AltitudeConverterCompat uses internally a RoomDatabase that cannot be access from main thread and thus fails on version <= 34.
        Thread t = new Thread(() -> {
                try {
                    Location loc = trackPoint.position().toLocation();
                    AltitudeConverterCompat.addMslAltitudeToLocation(context, loc);
                    Double mslAltitude = LocationCompat.getMslAltitudeMeters(loc);
                    if (mslAltitude != null) {
                        newTrackPoint[0] = TrackPoint.with(trackPoint, Altitude.EGM2008.of(mslAltitude));
                    }
                } catch (IOException | RuntimeException e) {
                    // No geoid data, no network, ...: keep the uncorrected altitude.
                    Log.w(TAG, "Android's AltitudeConverterCompat failed with " + e.getMessage());
                }
            });
        t.start();

        try {
            t.join(ALTITUDE_CORRECTION_TIMEOUT_MS);  // wait for thread to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Android's AltitudeConverterCompat was interrupted");
        }

        // The conversion can fail or time out. Returning null here used to crash the callers
        // (the notification and the track data hub dereference the result).
        return newTrackPoint[0] != null ? newTrackPoint[0] : trackPoint;
    }
}