package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.settings.PreferencesUtils;


public abstract class SensorHandler<T> {

    private final SensorManager sensorManager;

    private Context context;
    private Handler handler;
    private Driver driver;

    private Aggregator<T, ?> aggregator;

    protected final DriverObserver<T> driverObserver = new DriverObserver<>() {
        @Override
        public void onConnect() {
            SensorHandler.this.aggregator = createAggregator();
        }

        @Override
        public void onDisconnect() {
            SensorHandler.this.aggregator.resetAggregated();
        }

        @Override
        public void onData(T value) {
            SensorHandler.this.aggregator.add(sensorManager.getNow(), value);
            sensorManager.onChange();
        }

        @Override
        public void onRemove() {
            SensorHandler.this.aggregator = null;
        }
    };

    protected SensorHandler(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    //TODO Do we really need start and stop?
    void start(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
    }

    void stop() {
        this.context = null;
        this.handler = null;

        disconnect();
    }

    void connect() {
        disconnect();

        String address = PreferencesUtils.getString(getSensorPreferenceKey(), PreferencesUtils.getBluetoothSensorAddressNone());

        switch (PreferencesUtils.getSensorType(address)) {
            case NONE -> {
                return;
            }
            case INTERNAL -> {
                driver = createDriverInternal();
            }
            case REMOTE -> {
                driver = createDriverBluetooth(address);
            }
            default -> throw new UnsupportedOperationException();
        }

        driver.connect(context, handler, address);
    }

    void disconnect() {
        if (driver == null) return;

        driver.disconnect();
        driver = null;
    }

    @NonNull
    protected Driver createDriverInternal() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected Driver createDriverBluetooth(String address) {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected abstract Aggregator<T, ?> createAggregator();

    protected abstract int getSensorPreferenceKey();

    protected Context getContext() {
        return context;
    }

    //TODO Try to keep aggregator private.
    Aggregator<T, ?> getAggregator() {
        return aggregator;
    }

    //TODO REMOVE
    @VisibleForTesting
    public void setAggregator(Aggregator<T, ?> aggregator) {
        this.aggregator = aggregator;
    }
}
