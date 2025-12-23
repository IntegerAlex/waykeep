package de.dennisguse.opentracks.sensors.sensorData;

public record SensorData<T>(

        T data,

        String sensorNameOrAddress
) {
}
