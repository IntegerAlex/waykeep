package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingPower;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorGPS;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;
import de.dennisguse.opentracks.sensors.sensorData.Raw;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.util.SystemUtils;

public class SensorManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = SensorManager.class.getSimpleName();

    private PowerManager.WakeLock wakeLock;

    private final TrackPointCreator observer;

    private final SensorDataChangedObserver listener = new SensorDataChangedObserver() {

        @Override
        public void onConnect(Aggregator<?, ?> aggregator) {
            add(aggregator);
        }

        @Override
        public void onChange(Raw<?> data) {
            update(data);
            observer.onChange();
        }

        @Override
        public void onDisconnect(Aggregator<?, ?> aggregator) {
            add(aggregator);
        }

        @Override
        public void onRemove(Aggregator<?, ?> aggregator) {
            remove(aggregator);
        }

        @Override
        public Instant getNow() {
            return observer.createNow();
        }
    };

    private BluetoothRemoteSensorManager bluetoothSensorManager;

    private GainManager altitudeSumManager;

    private GpsManager gpsManager;

    public SensorManager(TrackPointCreator observer) {
        this.observer = observer;
    }

    public void start(Context context, Handler handler) {
        if (gpsManager != null) {
            throw new RuntimeException("SensorManager cannot be started twice; stop first.");
        }

        wakeLock = SystemUtils.acquireWakeLock(context, wakeLock);

        gpsManager = new GpsManager(observer, listener);
        altitudeSumManager = new GainManager(listener);
        bluetoothSensorManager = new BluetoothRemoteSensorManager(context, handler, listener);

        onSharedPreferenceChanged(null, null);

        gpsManager.start(context, handler);
        altitudeSumManager.start(context, handler);
        bluetoothSensorManager.start();
    }

    public void stop(Context context) {
        wakeLock = SystemUtils.releaseWakeLock(wakeLock);

        bluetoothSensorManager.stop();
        bluetoothSensorManager = null;

        altitudeSumManager.stop();
        altitudeSumManager = null;

        gpsManager.stop();
        gpsManager = null;

        clear();
    }

    public void reset() {
        if (bluetoothSensorManager == null || altitudeSumManager == null) {
            Log.d(TAG, "No recording running and no reset necessary.");
            return;
        }

        Log.i(TAG, "Resetting data");

        if (heartRate != null) heartRate.resetAggregated();
        if (temperature != null) temperature.resetAggregated();
        if (cyclingCadence != null) cyclingCadence.resetAggregated();
        if (cyclingDistanceSpeed != null) cyclingDistanceSpeed.resetAggregated();
        if (cyclingPower != null) cyclingPower.resetAggregated();
        if (runningDistanceSpeedCadence != null) runningDistanceSpeedCadence.resetAggregated();
        if (barometer != null) barometer.resetAggregated();
        if (gps != null) gps.resetAggregated();
    }

    @VisibleForTesting
    public void onChanged(Raw<?> data) {
        listener.onChange(data);
    }

    public GpsManager getGpsManager() {
        return gpsManager;
    }

    @Deprecated
    @VisibleForTesting
    public GainManager getAltitudeSumManager() {
        return altitudeSumManager;
    }

    @Deprecated
    @VisibleForTesting
    public void setAltitudeSumManager(GainManager altitudeSumManager) {
        this.altitudeSumManager = altitudeSumManager;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (gpsManager != null) {
            gpsManager.onSharedPreferenceChanged(sharedPreferences, key);
            bluetoothSensorManager.onSharedPreferenceChanged(sharedPreferences, key);
        }
    }

    public interface SensorDataChangedObserver {

        void onConnect(Aggregator<?, ?> sensorData);
        void onChange(Raw<?> sensorData);

        void onDisconnect(Aggregator<?, ?> sensorData);

        void onRemove(Aggregator<?, ?> sensorData);

        Instant getNow();
    }


    //TODO Inline from SensorDataAggregator; Aggregators will be moved into Drivers.
    @VisibleForTesting
    public AggregatorHeartRate heartRate;

    @VisibleForTesting
    public AggregatorTemperature temperature;

    @VisibleForTesting
    public AggregatorCyclingCadence cyclingCadence;

    @VisibleForTesting
    public AggregatorCyclingDistanceSpeed cyclingDistanceSpeed;

    @VisibleForTesting
    public AggregatorCyclingPower cyclingPower;

    @VisibleForTesting
    public AggregatorRunning runningDistanceSpeedCadence;

    @VisibleForTesting
    public AggregatorBarometer barometer;

    public AggregatorGPS gps;

    @VisibleForTesting
    public SensorData<HeartRate> getHeartRate(Instant now) {
        if (heartRate != null) {
            SensorData<HeartRate> value = heartRate.getAggregatedValueWithSensorName(now);
            if (value.data().isValid()) return value;
        }

        return null;
    }

    private SensorData<Cadence> getCadence(Instant now) {
        if (cyclingCadence != null) {
            return cyclingCadence.getAggregatedValueWithSensorName(now);
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData() && runningDistanceSpeedCadence.getAggregatedValue(now).cadence() != null) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue().cadence(), runningDistanceSpeedCadence.getSensorNameOrAddress());
        }

        return null;
    }

    private SensorData<Distance> getDistance(Instant now) {
        if (cyclingDistanceSpeed != null && cyclingDistanceSpeed.hasReceivedData()) {
            return new SensorData<>(cyclingDistanceSpeed.getAggregatedValue(now).distanceOverall(), cyclingDistanceSpeed.getSensorNameOrAddress());
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData()) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue(now).distance(), runningDistanceSpeedCadence.getSensorNameOrAddress());

        }

        return null;
    }

    //TOOD simplify?
    private SensorData<Speed> getSpeed(Instant now) {

        if (cyclingDistanceSpeed != null && cyclingDistanceSpeed.hasReceivedData() && cyclingDistanceSpeed.getAggregatedValue(now).speed() != null) {
            return new SensorData<>(cyclingDistanceSpeed.getAggregatedValue(now).speed(), cyclingDistanceSpeed.getSensorNameOrAddress());
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData() && runningDistanceSpeedCadence.getAggregatedValue(now).speed() != null) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue().speed(), runningDistanceSpeedCadence.getSensorNameOrAddress());
        }

        return null;
    }

    public void add(@NonNull Aggregator<?, ?> data) {
        set(data, data);
    }

    //TODO Refactor generic "fun" here: go explicit.
    public void update(@NonNull Raw<?> data) {
        Object value = data.value();

        if (value instanceof HeartRate) {
            this.heartRate.add((Raw<HeartRate>) data);
            return;
        }

        if (value instanceof Temperature) {
            this.temperature.add((Raw<Temperature>) data);
            return;
        }

        if (value instanceof BluetoothHandlerCyclingCadence.CrankData) {
            this.cyclingCadence.add((Raw<BluetoothHandlerCyclingCadence.CrankData>) data);
            return;
        }
        if (value instanceof BluetoothHandlerCyclingDistanceSpeed.WheelData) {
            this.cyclingDistanceSpeed.setWheelCircumference(PreferencesUtils.getWheelCircumference()); //TODO Fetch once and then listen for changes.
            this.cyclingDistanceSpeed.add((Raw<BluetoothHandlerCyclingDistanceSpeed.WheelData>) data);
            return;
        }
        if (value instanceof BluetoothHandlerRunningSpeedAndCadence.Data) {
            this.runningDistanceSpeedCadence.add((Raw<BluetoothHandlerRunningSpeedAndCadence.Data>) data);

            return;
        }
        if (value instanceof BluetoothHandlerCyclingPower.Data) {
            this.cyclingPower.add((Raw<BluetoothHandlerCyclingPower.Data>) data);
            return;
        }
        if (value instanceof AtmosphericPressure) {
            this.barometer.add((Raw<AtmosphericPressure>) data);
            return;
        }
        if (value instanceof Position) {
            this.gps.add((Raw<Position>) data);
            return;
        }

        throw new UnsupportedOperationException(data.getClass().getCanonicalName() + " " + data.value().getClass().getCanonicalName());
    }

    public void remove(@NonNull Aggregator<?, ?> type) {
        set(type, null);
    }

    // TODO inline with stop()?
    @VisibleForTesting
    public void clear() {
        Log.i(TAG, "Removing all aggregators");
        this.heartRate = null;
        this.temperature = null;
        this.cyclingCadence = null;
        this.cyclingDistanceSpeed = null;
        this.cyclingPower = null;
        this.runningDistanceSpeedCadence = null;
        this.barometer = null;
        this.gps = null;
    }

    public SensorDataSet getSensorDataSet(Instant now) {
        return new SensorDataSet(
                gps != null && gps.hasReceivedData() ? gps.getAggregatedValueWithSensorName(now) : new SensorData<>(Position.of(now), ""),
                getSpeed(now),
                getDistance(now),
                getHeartRate(now),
                temperature != null && temperature.hasReceivedData() ? temperature.getAggregatedValueWithSensorName(now) : null,
                getCadence(now),
                cyclingPower != null && cyclingPower.hasReceivedData() ? cyclingPower.getAggregatedValueWithSensorName(now) : null,
                barometer != null && barometer.hasReceivedData() ? barometer.getAggregatedValueWithSensorName(now) : null
        );
    }

    //TODO Refactor generic "fun" here: go explicit.
    private void set(@NonNull Aggregator<?, ?> type, @Nullable Aggregator<?, ?> sensorData) {
        Log.i(TAG, "Setting aggregator " + type.getClass().getCanonicalName() + " to " + sensorData);

        if (type instanceof AggregatorHeartRate) {
            heartRate = (AggregatorHeartRate) sensorData;
            return;
        }
        if (type instanceof AggregatorTemperature) {
            temperature = (AggregatorTemperature) sensorData;
            return;
        }
        if (type instanceof AggregatorCyclingCadence) {
            cyclingCadence = (AggregatorCyclingCadence) sensorData;
            return;
        }
        if (type instanceof AggregatorCyclingDistanceSpeed) {
            cyclingDistanceSpeed = (AggregatorCyclingDistanceSpeed) sensorData;
            return;
        }
        if (type instanceof AggregatorCyclingPower) {
            cyclingPower = (AggregatorCyclingPower) sensorData;
            return;
        }
        if (type instanceof AggregatorRunning) {
            runningDistanceSpeedCadence = (AggregatorRunning) sensorData;
            return;
        }
        if (type instanceof AggregatorBarometer) {
            barometer = (AggregatorBarometer) sensorData;
            return;
        }
        if (type instanceof AggregatorGPS) {
            gps = (AggregatorGPS) sensorData;
            return;
        }

        throw new UnsupportedOperationException(type.getClass().getCanonicalName());
    }
}
