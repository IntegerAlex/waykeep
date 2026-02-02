package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.sensors.driver.DriverObserver;
import de.dennisguse.opentracks.sensors.driver.GpsInternal;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorGPS;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class GpsManager implements DriverObserver<Location>, GpsStatusManager.GpsStatusListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private final String TAG = GpsManager.class.getSimpleName();

    //TODO Refactor to just pass information via SensorManager.
    private TrackPointCreator trackPointCreator;

    private SensorManager observer;
    private Context context;
    private Handler handler;

    @VisibleForTesting
    public GpsInternal driver;
    private GpsStatusManager gpsStatusManager;
    private Distance thresholdHorizontalAccuracy;

    private AggregatorGPS aggregatorGPS;

    public GpsManager(TrackPointCreator trackPointCreator, SensorManager observer) {
        this.trackPointCreator = trackPointCreator;
        this.observer = observer;
    }

    public void start(@NonNull Context context, @NonNull Handler handler) {
        this.context = context;
        this.handler = handler;

        onSharedPreferenceChanged(null, null);

        gpsStatusManager = new GpsStatusManager(context, this, handler);

        this.aggregatorGPS = new AggregatorGPS("internal");

        this.driver = new GpsInternal(this);
        this.driver.connect(context, handler, "internal");
        gpsStatusManager.start();
    }

    @SuppressWarnings({"MissingPermission"})
    public void stop() {
        if (driver.isConnected()) {
            driver.disconnect();
        }
        driver = null;
        this.context = null;
        handler = null;

        gpsStatusManager.stop();
        gpsStatusManager = null;

        this.aggregatorGPS = null;
        observer = null;

        trackPointCreator = null;
    }

    //TODO Exposed for refactoring. Is this necessary?
    @Deprecated
    AggregatorGPS getAggregatorGPS() {
        return aggregatorGPS;
    }

    //TODO Exposed for refactoring. This is very ugly.
    @Deprecated
    void setAggregatorGPS() {
        this.aggregatorGPS = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boolean registerListener = false;

        if (PreferencesUtils.isKey(R.string.min_sampling_interval_key, key)) {
            registerListener = true;

            if (gpsStatusManager != null) {
                gpsStatusManager.onMinSamplingIntervalChanged(PreferencesUtils.getMinSamplingInterval());
            }
        }
        if (PreferencesUtils.isKey(R.string.recording_gps_accuracy_key, key)) {
            thresholdHorizontalAccuracy = PreferencesUtils.getThresholdHorizontalAccuracy();
        }
        if (PreferencesUtils.isKey(R.string.recording_distance_interval_key, key)) {
            if (gpsStatusManager != null) {
                Distance gpsMinDistance = PreferencesUtils.getRecordingDistanceInterval();
                gpsStatusManager.onRecordingDistanceChanged(gpsMinDistance);
            }
        }

        if (registerListener && driver != null && driver.isConnected()) {
            driver.disconnect();
            driver.connect(context, handler, "internal");
        }
    }

    @Override
    public void onGpsStatusChanged(GpsStatusValue currentStatus) {
        trackPointCreator.sendGpsStatus(currentStatus);
    }

    @Override
    public void onConnected(String sensorAddress, String sensorName) {
        gpsStatusManager.onGpsEnabled();
    }

    @Override
    public void onConnectionLost() {
        gpsStatusManager.onGpsDisabled();
    }

    @Override
    public void onDisconnected() {
        gpsStatusManager.onGpsDisabled();
    }

    @Override
    public void onDataReceived(Location location) {
        // Send each update to the status; please note that this TrackPoint is not stored.
        Position position = Position.of(location, observer.getNow());
        gpsStatusManager.onNewTrackPoint(position);

        if (!position.hasValidLocation()) {
            Log.w(TAG, "Ignore newTrackPoint. Location is invalid.");
            return;
        }

        if (!position.fulfillsAccuracy(thresholdHorizontalAccuracy)) {
            Log.d(TAG, "Ignore newTrackPoint. Poor accuracy.");
            return;
        }

        aggregatorGPS.add(observer.getNow(), position);
        observer.onChange();
    }
}
