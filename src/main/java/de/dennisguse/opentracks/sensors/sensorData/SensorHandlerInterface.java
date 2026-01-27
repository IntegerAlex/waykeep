package de.dennisguse.opentracks.sensors.sensorData;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.List;

import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;
import de.dennisguse.opentracks.sensors.driver.DriverObserver0;

public interface SensorHandlerInterface {

    List<ServiceMeasurementUUID> getServices();

    Aggregator<?, ?> createEmptySensorData(String address, String name);

    void handlePayload(DriverObserver0 observer, ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, String address, BluetoothGattCharacteristic characteristic);
}
