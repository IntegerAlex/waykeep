package de.dennisguse.opentracks.sensors;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingPower;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorGPS;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.util.SystemUtils;

//TODO provide context and handler for different Managers (not passing via start(), but similar to getNow()).
//TODO Remove DriverObserver from SensorManager
public class SensorManager implements SharedPreferences.OnSharedPreferenceChangeListener, DriverObserver0 {

    private static final String TAG = SensorManager.class.getSimpleName();

    private Context context;

    private Handler handler;

    private PowerManager.WakeLock wakeLock;

    private final TrackPointCreator observer;

    private HeartRateHandler heartRateHandler;
    private TemperatureHandler temperatureHandler;
    private CyclingCadenceHandler cyclingCadenceHandler;
    private CyclingDistanceSpeedHandler cyclingDistanceSpeedHandler;
    private BluetoothConnectionManager cyclingPowerManager;
    private BluetoothConnectionManager runningSpeedAndCadenceManager;

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
        this.context = context;
        this.handler = handler;

        gpsManager = new GpsManager(observer, this);
        altitudeSumManager = new GainManager(this);
        this.heartRateHandler = new HeartRateHandler(this);
        this.temperatureHandler = new TemperatureHandler(this);
        this.cyclingCadenceHandler = new CyclingCadenceHandler(this);
        this.cyclingDistanceSpeedHandler = new CyclingDistanceSpeedHandler(this);

        BluetoothAdapter bluetoothAdapter = BluetoothUtils.getAdapter(context);
        this.cyclingPowerManager = new BluetoothConnectionManager(bluetoothAdapter, this, new BluetoothHandlerCyclingPower());
        this.runningSpeedAndCadenceManager = new BluetoothConnectionManager(bluetoothAdapter, this, new BluetoothHandlerRunningSpeedAndCadence());

        altitudeSumManager.start(context, handler);
        heartRateHandler.start(context, handler);
        temperatureHandler.start(context, handler);
        cyclingCadenceHandler.start(context, handler);
        cyclingDistanceSpeedHandler.start(context, handler);
        onSharedPreferenceChanged(null, null);

        gpsManager.start(context, handler);
    }

    public void stop() {
        wakeLock = SystemUtils.releaseWakeLock(wakeLock);

        heartRateHandler.stop();
        heartRateHandler = null;

        temperatureHandler.stop();
        temperatureHandler = null;

        cyclingCadenceHandler.stop();
        cyclingCadenceHandler = null;

        cyclingDistanceSpeedHandler.stop();
        cyclingDistanceSpeedHandler = null;

        cyclingPowerManager.disconnect();
        cyclingPowerManager = null;

        runningSpeedAndCadenceManager.disconnect();
        runningSpeedAndCadenceManager = null;

        altitudeSumManager.stop();
        altitudeSumManager = null;

        gpsManager.stop();
        gpsManager = null;

        context = null;
        handler = null;

        clear();
    }

    public void reset() {
        if (gpsManager == null || altitudeSumManager == null) {
            Log.d(TAG, "No recording running and no reset necessary.");
            return;
        }

        Log.i(TAG, "Resetting data");

        if (heartRateHandler.getAggregator() != null) heartRateHandler.getAggregator().resetAggregated();
        if (temperatureHandler.getAggregator() != null) temperatureHandler.getAggregator().resetAggregated();
        if (cyclingCadenceHandler.getAggregator() != null) cyclingCadenceHandler.getAggregator().resetAggregated();
        if (cyclingDistanceSpeedHandler.getAggregator() != null) cyclingDistanceSpeedHandler.getAggregator().resetAggregated();
        if (cyclingPower != null) cyclingPower.resetAggregated();
        if (runningDistanceSpeedCadence != null) runningDistanceSpeedCadence.resetAggregated();
        if (altitudeSumManager.getAggregator() != null) altitudeSumManager.getAggregator().resetAggregated();
        if (gpsManager.getAggregatorGPS() != null) gpsManager.getAggregatorGPS().resetAggregated();
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

    private synchronized void connect(BluetoothConnectionManager connectionManager, String address) {
        connectionManager.connect(context, handler, address);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (gpsManager != null) {
            gpsManager.onSharedPreferenceChanged(sharedPreferences, key);

            if (PreferencesUtils.isKey(altitudeSumManager.getSensorPreferenceKey(), key)) {
                altitudeSumManager.connect();
            }

            if (PreferencesUtils.isKey(heartRateHandler.getSensorPreferenceKey(), key)) {
                heartRateHandler.connect();
            }

            if (PreferencesUtils.isKey(temperatureHandler.getSensorPreferenceKey(), key)) {
                temperatureHandler.connect();
            }

            if (PreferencesUtils.isKey(cyclingCadenceHandler.getSensorPreferenceKey(), key)) {
                cyclingCadenceHandler.connect();
            }

            if (PreferencesUtils.isKey(cyclingDistanceSpeedHandler.getSensorPreferenceKey(), key)) {
                cyclingDistanceSpeedHandler.connect();
            }

            if (PreferencesUtils.isKey(R.string.settings_sensor_bluetooth_cycling_power_key, key)) {
                String address = PreferencesUtils.getBluetoothCyclingPowerSensorAddress();

                connect(cyclingPowerManager, address);
            }

            if (PreferencesUtils.isKey(R.string.settings_sensor_bluetooth_running_speed_and_cadence_key, key)) {
                String address = PreferencesUtils.getBluetoothRunningSpeedAndCadenceAddress();

                connect(runningSpeedAndCadenceManager, address);
            }
        }
    }

    //TODO Inlined from SensorDataAggregator; Aggregators will be moved into Drivers.
    @Deprecated
    public void onConnect(Aggregator<?, ?> aggregator) {
        add(aggregator);
    }

    @Deprecated
    public void onDisconnect(Aggregator<?, ?> aggregator) {
        add(aggregator);
    }

    @Deprecated
    public void onRemove(Aggregator<?, ?> aggregator) {
        remove(aggregator);
    }

    public Instant getNow() {
        return observer.getNow();
    }

    @VisibleForTesting
    public AggregatorCyclingPower cyclingPower;

    @VisibleForTesting
    public AggregatorRunning runningDistanceSpeedCadence;

    @VisibleForTesting
    public SensorData<HeartRate> getHeartRate(Instant now) {
        if (heartRateHandler.getAggregator() != null) {
            SensorData<HeartRate> value = (SensorData<HeartRate>) heartRateHandler.getAggregator().getAggregatedValueWithSensorName(now);
            //TODO Move this check into HeartRateHandler
            if (value.data().isValid()) return value;
        }

        return null;
    }

    private SensorData<Cadence> getCadence(Instant now) {
        if (cyclingCadenceHandler.getAggregator() != null) {
            return (SensorData<Cadence>) cyclingCadenceHandler.getAggregator().getAggregatedValueWithSensorName(now);
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData() && runningDistanceSpeedCadence.getAggregatedValue(now).cadence() != null) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue().cadence(), runningDistanceSpeedCadence.getSensorNameOrAddress());
        }

        return null;
    }

    private SensorData<Distance> getDistance(Instant now) {
        if (cyclingDistanceSpeedHandler.getAggregator() != null && cyclingDistanceSpeedHandler.getAggregator().hasReceivedData()) {
            return new SensorData<>(((AggregatorCyclingDistanceSpeed)cyclingDistanceSpeedHandler.getAggregator()).getAggregatedValue(now).distanceOverall(), cyclingDistanceSpeedHandler.getAggregator().getSensorNameOrAddress());
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData()) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue(now).distance(), runningDistanceSpeedCadence.getSensorNameOrAddress());

        }

        return null;
    }

    //TOOD simplify?
    private SensorData<Speed> getSpeed(Instant now) {

        if (cyclingDistanceSpeedHandler.getAggregator() != null && cyclingDistanceSpeedHandler.getAggregator().hasReceivedData() && ((AggregatorCyclingDistanceSpeed)cyclingDistanceSpeedHandler.getAggregator()).getAggregatedValue(now).speed() != null) {
            return new SensorData<>(((AggregatorCyclingDistanceSpeed)cyclingDistanceSpeedHandler.getAggregator()).getAggregatedValue(now).speed(), cyclingDistanceSpeedHandler.getAggregator().getSensorNameOrAddress());
        }

        if (runningDistanceSpeedCadence != null && runningDistanceSpeedCadence.hasReceivedData() && runningDistanceSpeedCadence.getAggregatedValue(now).speed() != null) {
            return new SensorData<>(runningDistanceSpeedCadence.getAggregatedValue().speed(), runningDistanceSpeedCadence.getSensorNameOrAddress());
        }

        return null;
    }

    //TODO Remove
    @VisibleForTesting
    @Deprecated
    public void add(@NonNull Aggregator<?, ?> data) {
        if (data instanceof AggregatorHeartRate) {
            heartRateHandler.setAggregator((AggregatorHeartRate) data);
            return;
        }
        if (data instanceof AggregatorTemperature) {
            temperatureHandler.setAggregator((AggregatorTemperature) data);
            return;
        }
        if (data instanceof AggregatorCyclingCadence) {
            cyclingCadenceHandler.setAggregator((AggregatorCyclingCadence) data);
            return;
        }
        if (data instanceof AggregatorCyclingDistanceSpeed) {
            cyclingDistanceSpeedHandler.setAggregator((AggregatorCyclingDistanceSpeed) data);
            return;
        }
        set(data, data);
    }

    @Deprecated
    public void onChange(Record data) {
        update(data);
        onChange();
    }

    public void onChange() {
        observer.onChange();
    }

    //TODO Refactor generic "fun" here: go explicit.
    @Deprecated
    private void update(@NonNull Record value) {
        Instant now = getNow();
        if (value instanceof BluetoothHandlerRunningSpeedAndCadence.Data) {
            this.runningDistanceSpeedCadence.add(now, (BluetoothHandlerRunningSpeedAndCadence.Data) value);

            return;
        }
        if (value instanceof BluetoothHandlerCyclingPower.Data) {
            this.cyclingPower.add(now, (BluetoothHandlerCyclingPower.Data) value);
            return;
        }

        throw new UnsupportedOperationException(value.getClass().getCanonicalName());
    }

    @Deprecated
    public void remove(@NonNull Aggregator<?, ?> type) {
        set(type, null);
    }

    // TODO inline with stop()?
    @VisibleForTesting
    public void clear() {
        Log.i(TAG, "Removing all aggregators");
        this.cyclingPower = null;
        this.runningDistanceSpeedCadence = null;

        //TODO This is to ExportImportTest alive; refactor test.
        if (this.altitudeSumManager != null) {
            this.altitudeSumManager.setAggregator(null);
        }
        if (this.heartRateHandler != null) {
            this.heartRateHandler.setAggregator(null);
        }
        if (this.temperatureHandler != null) {
            this.temperatureHandler.setAggregator(null);
        }
        if (this.cyclingCadenceHandler != null) {
            this.cyclingCadenceHandler.setAggregator(null);
        }
        if (cyclingDistanceSpeedHandler != null) {
            cyclingDistanceSpeedHandler.setAggregator(null);
        }
        if (this.gpsManager != null) {
            this.gpsManager.setAggregatorGPS();
        }
    }

    public SensorDataSet getSensorDataSet(Instant now) {
        AggregatorGPS gps = gpsManager.getAggregatorGPS();
        AggregatorBarometer barometer = (AggregatorBarometer) altitudeSumManager.getAggregator();

        return new SensorDataSet(
                gps != null && gps.hasReceivedData() ? gps.getAggregatedValueWithSensorName(now) : new SensorData<>(Position.of(now), ""),
                getSpeed(now),
                getDistance(now),
                getHeartRate(now),
                temperatureHandler.getAggregator() != null && temperatureHandler.getAggregator().hasReceivedData() ? (SensorData<Temperature>) temperatureHandler.getAggregator().getAggregatedValueWithSensorName(now) : null,
                getCadence(now),
                cyclingPower != null && cyclingPower.hasReceivedData() ? cyclingPower.getAggregatedValueWithSensorName(now) : null,
                barometer != null && barometer.hasReceivedData() ? barometer.getAggregatedValueWithSensorName(now) : null
        );
    }

    //TODO Refactor generic "fun" here: go explicit.
    private void set(@NonNull Aggregator<?, ?> type, @Nullable Aggregator<?, ?> sensorData) {
        Log.i(TAG, "Setting aggregator " + type.getClass().getCanonicalName() + " to " + sensorData);

        if (type instanceof AggregatorCyclingPower) {
            cyclingPower = (AggregatorCyclingPower) sensorData;
            return;
        }
        if (type instanceof AggregatorRunning) {
            runningDistanceSpeedCadence = (AggregatorRunning) sensorData;
            return;
        }

        throw new UnsupportedOperationException(type.getClass().getCanonicalName());
    }
}
