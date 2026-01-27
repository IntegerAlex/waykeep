package de.dennisguse.opentracks.sensors;

import static de.dennisguse.opentracks.sensors.BluetoothHandlerBarometricPressure.ENVIRONMENTAL_SENSING_SERVICE;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;
import de.dennisguse.opentracks.sensors.sensorData.SensorHandlerInterface;

public class BluetoothHandlerTemperature implements SensorHandlerInterface {
    public static final ServiceMeasurementUUID TEMPERATURE = new ServiceMeasurementUUID(
            ENVIRONMENTAL_SENSING_SERVICE,
            new UUID(0x2A6E00001000L, 0x800000805f9b34fbL)
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return List.of(TEMPERATURE);
    }

    @Override
    public Aggregator<?, ?> createEmptySensorData(String address, String name) {
        return new AggregatorTemperature(address, name);
    }

    @Override
    public void handlePayload(DriverObserver0 observer, ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, String address, BluetoothGattCharacteristic characteristic) {
        Temperature value = parseEnvironmentalSensing(characteristic);
        if (value == null) return;

        observer.onChange(value);
    }

    /**
     * Decoding: org.bluetooth.characteristic.temperature.xml
     */
    public static Temperature parseEnvironmentalSensing(BluetoothGattCharacteristic characteristic) {
        byte[] raw = characteristic.getValue();

        if (raw.length < 2) {
            return null;
        }

        Integer pressure = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        return Temperature.ofCentiCelsius(pressure);
    }
}
