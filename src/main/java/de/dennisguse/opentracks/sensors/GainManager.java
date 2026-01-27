package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.sensors.driver.BarometerInternal;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure sensor (i.e., barometer).
 */
//TODO Rename class
public class GainManager implements DriverObserver<AtmosphericPressure> {

    private static final String TAG = GainManager.class.getSimpleName();


    private final SensorManager sensorManager;

    private final SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> connect();

    private Context context;
    private Handler handler;
    private Driver driver;

    private AggregatorBarometer aggregatorBarometer;

    public GainManager(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    public void start(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    public void stop() {
        Log.d(TAG, "Stop");
        this.context = null;
        this.handler = null;
        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        disconnect();
    }

    @Deprecated
    public AggregatorBarometer getAggregatorBarometer() {
        return aggregatorBarometer;
    }

    @Deprecated
    public void setAggregatorBarometer(AggregatorBarometer aggregatorBarometer) {
        this.aggregatorBarometer = aggregatorBarometer;
    }

    private void connect() {
        disconnect();

        String address = PreferencesUtils.getBarometerSensorAddress();
        switch (PreferencesUtils.getSensorType(address)) {
            case NONE -> {
                return;
            }
            case INTERNAL -> driver = new BarometerInternal(this);
            case REMOTE -> driver =
                    new BluetoothConnectionManager(
                            BluetoothUtils.getAdapter(context),
                            new DriverObserver0() {
                                @Override
                                public void onConnect(Aggregator<?, ?> emptySensorData) {
                                    GainManager.this.onConnect();
                                }

                                @Override
                                public void onDisconnect(Aggregator<?, ?> emptySensorData) {
                                    GainManager.this.onDisconnect();
                                }

                                @Override
                                public void onRemove(Aggregator<?, ?> emptySensorData) {
                                    GainManager.this.onRemove();
                                }

                                @Override
                                public void onChange(Record value) {
                                    onData((AtmosphericPressure) value);
                                }
                            },
                            new BluetoothHandlerBarometricPressure()
                    );
            default -> throw new RuntimeException("Not implemented");
        }

        driver.connect(context, handler, address);
    }

    private void disconnect() {
        if (driver == null) return;

        driver.disconnect();
        driver = null;
    }

    @Override
    public void onConnect() {
        this.aggregatorBarometer = new AggregatorBarometer("", null);
    }

    @Override
    public void onDisconnect() {
        this.aggregatorBarometer.resetAggregated();
    }

    @Override
    public void onData(AtmosphericPressure value) {
        this.aggregatorBarometer.add(sensorManager.getNow(), value);
    }

    @Override
    public void onRemove() {
        this.aggregatorBarometer = null;
    }
}
