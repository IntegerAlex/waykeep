package com.waykeep.app.sensors.driver;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import java.util.function.Consumer;

import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.util.PermissionRequester;

public class GpsInternal implements Driver {

    private static final String TAG = GpsInternal.class.getSimpleName();

    public static final String LOCATION_PROVIDER = LocationManager.GPS_PROVIDER;

    @VisibleForTesting
    public final LocationListenerCompat locationListenerCompat = new LocationListenerCompat() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            driverObserver.onDataReceived(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            driverObserver.onConnected(null, provider);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            driverObserver.onSensorDeactivated();
        }
    };

    private final DriverObserver<Location> driverObserver;

    @VisibleForTesting
    public LocationManager locationManager;

    public GpsInternal(DriverObserver<Location> driverObserver) {
        this.driverObserver = driverObserver;
    }

    @Override
    public void connect(Context context, Handler handler, String address) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (!LocationManagerCompat.hasProvider(locationManager, LOCATION_PROVIDER)) {
            Log.e(TAG, "Device doesn't have GPS.");
            return;
        }

        if (PermissionRequester.GPS.hasPermission(context)) {
            try {
                Log.i(TAG, "Register for location updates");
                driverObserver.onConnected(null, null);
                injectAssistanceData(locationManager);
                LocationManagerCompat.requestLocationUpdates(locationManager, LOCATION_PROVIDER, createLocationRequest(), handler::post, locationListenerCompat);
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Could not register location listener; permissions not granted.", e);
            }
        }

        driverObserver.onDisconnected();
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void updateDriverParameters(Context context, Handler handler) {
        if (!isConnected()) return;

        Log.i(TAG, "Update for location updates");
        driverObserver.onConnected(null, null);
        LocationManagerCompat.requestLocationUpdates(locationManager, LOCATION_PROVIDER, createLocationRequest(), handler::post, locationListenerCompat);
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void disconnect() {
        if (!isConnected()) return;

        LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat);

        locationManager = null;
        driverObserver.onDisconnected();
    }

    @Override
    public boolean isConnected() {
        return locationManager != null;
    }

    private LocationRequestCompat createLocationRequest() {
        return new LocationRequestCompat.Builder(PreferencesUtils.getMinSamplingInterval().toMillis())
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(0)
                .build();
    }

    // #2410: warm the GNSS provider on cold starts; command names are
    // provider-specific, so each attempt remains best-effort.
    static void sendAssistanceCommands(Consumer<String> send) {
        send.accept("force_xtra_injection");
        send.accept("force_time_injection");
    }

    private void injectAssistanceData(LocationManager manager) {
        Thread thread = new Thread(() -> sendAssistanceCommands(command -> {
            boolean accepted = false;
            String errorClass = null;
            try {
                Log.i(TAG, "Requesting GNSS assistance: " + command);
                accepted = manager.sendExtraCommand(LOCATION_PROVIDER, command, null);
                if (!accepted) {
                    Log.w(TAG, "GPS provider did not accept " + command);
                }
            } catch (RuntimeException e) {
                errorClass = e.getClass().getSimpleName();
                Log.w(TAG, "GPS provider does not support " + command, e);
            }
            driverObserver.onAssistanceResult(command, accepted, errorClass);
        }), "GpsAssistanceData");
        thread.setDaemon(true);
        thread.start();
    }

}
