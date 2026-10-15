package com.waykeep.app.sensors;

import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

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

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        if (PreferencesUtils.isKey(R.string.recording_distance_interval_key, key)) {
            if (gpsStatusManager != null) {
                Distance gpsMinDistance = PreferencesUtils.getRecordingDistanceInterval();
                gpsStatusManager.onRecordingDistanceChanged(gpsMinDistance);
            }
        }

        if (PreferencesUtils.isKey(R.string.recording_gps_accuracy_key, key)) {
            thresholdHorizontalAccuracy = PreferencesUtils.getThresholdHorizontalAccuracy();
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
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        gpsStatusManager.stop();
    }

    @Override
    public void onSensorDeactivated() {
        super.onSensorDeactivated();
        gpsStatusManager.onGpsDisabled();
    }

    @Override
    public void onDataReceived(Location location) {
        // Send each update to the status; please note that this TrackPoint is not stored.
        Position position = Position.of(location, trackPointCreator.getNow());
        gpsStatusManager.onNewTrackPoint(position);

        //TODO We could move the following check into the AggregatorGPS
        if (!position.hasValidLocation()) {
            Log.w(TAG, "Ignore newTrackPoint. Location is invalid.");
            return;
        }
        if (!position.fulfillsAccuracy(thresholdHorizontalAccuracy)) {
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
}
