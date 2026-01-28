package de.dennisguse.opentracks.sensors.driver;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.util.PermissionRequester;

public class GpsInternal implements Driver {

    private static final String TAG = GpsInternal.class.getSimpleName();

    private static final String LOCATION_PROVIDER = LocationManager.GPS_PROVIDER;

    private final LocationListenerCompat locationListenerCompat = new LocationListenerCompat() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            driverObserver.onData(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            driverObserver.onConnect();
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            driverObserver.onDisconnect();
        }
    };

    private final DriverObserver<Location> driverObserver;

    private LocationManager locationManager;

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

        LocationRequestCompat locationRequest = new LocationRequestCompat.Builder(PreferencesUtils.getMinSamplingInterval().toMillis())
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(0)
                .build();

        if (PermissionRequester.GPS.hasPermission(context)) {
            try {
                Log.i(TAG, "Register for location updates " + context);
                LocationManagerCompat.requestLocationUpdates(locationManager, LOCATION_PROVIDER, locationRequest, handler::post, locationListenerCompat);
            } catch (SecurityException e) {
                Log.e(TAG, "Could not register location listener; permissions not granted.", e);
            }
        }
    }

    @SuppressWarnings({"MissingPermission"})
    @Override
    public void disconnect() {
        if (!isConnected()) return;

        LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat);

        locationManager = null;
        driverObserver.onDisconnect();
    }


    @Override
    public boolean isConnected() {
        return locationManager != null;
    }
}
