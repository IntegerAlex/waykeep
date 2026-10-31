package com.waykeep.app.sensors;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.location.GnssStatusCompat;
import androidx.core.location.LocationManagerCompat;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.sensors.driver.Driver;
import com.waykeep.app.sensors.driver.GpsInternal;
import com.waykeep.app.sensors.driver.SensorType;
import com.waykeep.app.sensors.sensorData.AggregatorGPS;
import com.waykeep.app.services.handlers.TrackPointCreator;
import com.waykeep.app.settings.PreferencesUtils;

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class GpsHandler extends SensorHandler<Location, Position> {

    private final String TAG = GpsHandler.class.getSimpleName();

    //TODO Refactor to just pass information via SensorManager.
    private TrackPointCreator trackPointCreator;

    private GpsStatusManager gpsStatusManager;
    private Distance thresholdHorizontalAccuracy;

    /** Satellite callbacks arrive at ~1 Hz; only changes are logged, and not more often than this. */
    @VisibleForTesting
    public static final long GNSS_STATUS_MIN_INTERVAL_MS = 5_000;

    @Nullable
    private GnssStatusCompat.Callback gnssStatusCallback;
    @Nullable
    private LocationManager gnssStatusLocationManager;
    private long gnssConnectElapsedMs = -1;
    private long lastGnssLogElapsedMs = -1;
    private int lastSatellitesVisible = -1;
    private int lastSatellitesUsedInFix = -1;
    private boolean hasFix;

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        if (PreferencesUtils.isKey(R.string.recording_gps_accuracy_key, key)) {
            thresholdHorizontalAccuracy = PreferencesUtils.getThresholdHorizontalAccuracy();
            if (gpsStatusManager != null) {
                gpsStatusManager.onAccuracyThresholdChanged(thresholdHorizontalAccuracy);
            }
        }

        if (PreferencesUtils.isKey(R.string.min_sampling_interval_key, key)) {
            if (gpsStatusManager != null) {
                gpsStatusManager.onMinSamplingIntervalChanged(PreferencesUtils.getMinSamplingInterval());
            }

            if (driver != null) {
                driver.updateDriverParameters(getContext(), sensorManager.getHandler());
            }
        }
    };

    public GpsHandler(SensorManager sensorManager, TrackPointCreator trackPointCreator) {
        super(sensorManager);
        this.trackPointCreator = trackPointCreator;
        gpsStatusManager = new GpsStatusManager(sensorManager.getContext(), trackPointCreator::sendGpsStatus, sensorManager.getHandler());

        sharedPreferenceChangeListener.onSharedPreferenceChanged(null, null);
    }

    @Override
    public void onConnected(String sensorAddress, String sensorName) {
        super.onConnected(sensorAddress, sensorName);
        gpsStatusManager.onGpsEnabled();
        gpsStatusManager.start();
        registerGnssStatus();
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        gpsStatusManager.stop();
        unregisterGnssStatus();
    }

    @Override
    public void onAssistanceResult(String command, boolean accepted, @Nullable String errorClass) {
        trackPointCreator.sendGnssAssistance(command, accepted, errorClass);
    }

    @Override
    public void onSensorDeactivated() {
        super.onSensorDeactivated();
        gpsStatusManager.onGpsDisabled();
    }

    @Override
    public void onDataReceived(Location location) {
        hasFix = true;
        // Send each update to the status; please note that this TrackPoint is not stored.
        Position position = Position.of(location, trackPointCreator.getNow());
        gpsStatusManager.onNewTrackPoint(position);

        //TODO We could move the following check into the AggregatorGPS
        boolean validLocation = position.hasValidLocation();
        boolean accurate = validLocation && position.fulfillsAccuracy(thresholdHorizontalAccuracy);
        String decision = !validLocation ? "invalid_location" : accurate ? "accepted" : "poor_accuracy";
        trackPointCreator.sendRawGpsFix(position, location.getProvider(), location.getTime(),
                location.getElapsedRealtimeNanos(), thresholdHorizontalAccuracy, decision);
        if (!validLocation) {
            Log.w(TAG, "Ignore newTrackPoint. Location is invalid.");
            return;
        }
        if (!accurate) {
            Log.d(TAG, "Ignore newTrackPoint. Poor accuracy.");
            return;
        }

        super.onDataReceived(location);
    }

    @NonNull
    @Override
    protected Driver createDriverInternal() {
        return new GpsInternal(this);
    }

    @NonNull
    @Override
    protected AggregatorGPS createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorGPS(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_gps_key;
    }

    @Override
    protected String getSensorPreferenceDefaultValue() {
        return SensorType.INTERNAL.getPreferenceValue();
    }

    /**
     * Satellite visibility is the missing fact behind "waiting for GPS": it separates a chip that
     * sees nothing (indoors) from one that sees satellites but has no fix yet (cold start).
     * Best effort; an unsupported device simply keeps the generic status.
     */
    private void registerGnssStatus() {
        if (gnssStatusCallback != null) {
            return;
        }
        LocationManager locationManager = (LocationManager) getContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return;
        }

        gnssStatusLocationManager = locationManager;
        gnssConnectElapsedMs = SystemClock.elapsedRealtime();
        lastGnssLogElapsedMs = gnssConnectElapsedMs;
        lastSatellitesVisible = -1;
        lastSatellitesUsedInFix = -1;
        hasFix = false;

        gnssStatusCallback = new GnssStatusCompat.Callback() {
            @Override
            public void onStarted() {
                Log.i(TAG, "GNSS status: started");
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                Log.i(TAG, "GNSS status: first fix in " + ttffMillis + " ms");
                trackPointCreator.sendGnssFirstFix(ttffMillis);
            }

            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatusCompat status) {
                // Only the acquisition window matters: once fixes arrive, the 1 Hz gps_fix
                // events carry the accuracy and the journal has a 4 MiB cap.
                if (hasFix) {
                    return;
                }
                int visible = status.getSatelliteCount();
                int usedInFix = 0;
                double maxSnr = Double.NaN;
                for (int i = 0; i < visible; i++) {
                    if (status.usedInFix(i)) {
                        usedInFix++;
                    }
                    double cn0 = status.getCn0DbHz(i);
                    if (!Double.isNaN(cn0) && (Double.isNaN(maxSnr) || cn0 > maxSnr)) {
                        maxSnr = cn0;
                    }
                }

                long now = SystemClock.elapsedRealtime();
                if (!shouldLogGnssStatus(lastSatellitesVisible, lastSatellitesUsedInFix, visible, usedInFix, now - lastGnssLogElapsedMs)) {
                    return;
                }
                lastSatellitesVisible = visible;
                lastSatellitesUsedInFix = usedInFix;
                lastGnssLogElapsedMs = now;
                trackPointCreator.sendGnssStatus(visible, usedInFix,
                        Double.isNaN(maxSnr) ? null : maxSnr, now - gnssConnectElapsedMs);
            }
        };

        Handler handler = sensorManager.getHandler();
        boolean registered = LocationManagerCompat.registerGnssStatusCallback(locationManager, handler::post, gnssStatusCallback);
        Log.i(TAG, "GNSS status registered=" + registered);
        if (!registered) {
            Log.w(TAG, "GNSS status is not available on this device");
            gnssStatusCallback = null;
            gnssStatusLocationManager = null;
        }
    }

    private void unregisterGnssStatus() {
        if (gnssStatusCallback == null) {
            return;
        }
        LocationManagerCompat.unregisterGnssStatusCallback(gnssStatusLocationManager, gnssStatusCallback);
        gnssStatusCallback = null;
        gnssStatusLocationManager = null;
        lastSatellitesVisible = -1;
        lastSatellitesUsedInFix = -1;
    }

    @VisibleForTesting
    static boolean shouldLogGnssStatus(int lastVisible, int lastUsedInFix, int visible, int usedInFix, long sinceLastLogMs) {
        if (lastVisible < 0) {
            return true;
        }
        if (visible == lastVisible && usedInFix == lastUsedInFix) {
            return false;
        }
        return sinceLastLogMs >= GNSS_STATUS_MIN_INTERVAL_MS;
    }
}
