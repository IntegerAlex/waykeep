package de.dennisguse.opentracks.settings.bluetooth;

import android.content.Context;
import android.location.LocationManager;
import android.util.AttributeSet;

import androidx.core.location.LocationManagerCompat;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.List;

import de.dennisguse.opentracks.sensors.driver.GpsInternal;

public class BluetoothLeGpsPreference extends BluetoothLeSensorPreference {

    public BluetoothLeGpsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public BluetoothLeGpsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BluetoothLeGpsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BluetoothLeGpsPreference(Context context) {
        super(context);
    }

    @Override
    public PreferenceDialogFragmentCompat createInstance() {
        boolean hasInternalSensor = LocationManagerCompat.hasProvider((LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE), GpsInternal.LOCATION_PROVIDER);

        return BluetoothLeSensorPreferenceDialog
                .newInstance(getKey(), List.of(), hasInternalSensor);
    }


}