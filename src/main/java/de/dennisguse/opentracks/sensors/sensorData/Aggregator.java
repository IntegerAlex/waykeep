package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;

public abstract class Aggregator<Input, Output> {

    private static final Duration MAX_SENSOR_DATE_SET_AGE = Duration.ofSeconds(5);

    protected Raw<Input> previous;

    protected Output aggregatedValue;

    private final String sensorAddress;
    private final String sensorName;

    Aggregator(String sensorAddress) {
        this(sensorAddress, null);
    }

    Aggregator(String sensorAddress, String sensorName) {
        this.sensorAddress = sensorAddress;
        this.sensorName = sensorName;
    }

    public String getSensorNameOrAddress() {
        return sensorName != null ? sensorName : sensorAddress;
    }

    public final void add(Instant now, Input current) {
        Raw next = new Raw<>(now, current);
        computeValue(next);
        previous = next;
    }

    protected abstract void computeValue(Raw<Input> current);

    /**
     * @return did we process data from a sensor.
     * NOTE: for some sensors this may require more than one measurement.
     */
    public boolean hasReceivedData() {
        return aggregatedValue != null;
    }

    @Deprecated //TODO Remove: for sensor there is no default value. Make getAggregatedValue() @Nullable
    @NonNull
    protected abstract Output getNoneValue();

    @Deprecated //TODO Removed; just implemented for refactoring. This code is bypassing core functionality of this class.
    public Output getAggregatedValue() {
        return aggregatedValue;
    }

    @NonNull
    public Output getAggregatedValue(Instant now) {
        if (!hasReceivedData()) {
            return getNoneValue();
        }
        if (isOutdated(now)) {
            resetImmediate();
        }

        return aggregatedValue;
    }

    @NonNull
    public SensorData<Output> getAggregatedValueWithSensorName(Instant now) {
        return new SensorData<>(getAggregatedValue(now), getSensorNameOrAddress());
    }

    /**
     * Reset short-term (i.e., non-aggregated) values that were directly derived from sensor data.
     */
    protected abstract void resetImmediate();

    /**
     * Reset long-term (i.e., aggregated) values (more than derived from previous SensorData) like overall distance.
     */
    public abstract void resetAggregated();

    /**
     * Is the data recent considering the current time.
     */
    private boolean isOutdated(Instant now) {
        if (previous == null) {
            return true;
        }

        return now
                .isAfter(
                        previous.time()
                                .plus(getMaxSensorAge())
                );
    }

    protected Duration getMaxSensorAge() {
        return MAX_SENSOR_DATE_SET_AGE;
    }

    @NonNull
    @Override
    public String toString() {
        return "sensorAddress=" + sensorAddress + " data=" + aggregatedValue;
    }

    protected record Raw<T>(
            @NonNull Instant time,
            @NonNull T value
    ) {
    }
}
