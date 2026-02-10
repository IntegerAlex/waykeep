package de.dennisguse.opentracks.sensors.driver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;
import android.util.Pair;

import java.util.List;

import de.dennisguse.opentracks.sensors.ServiceMeasurementUUID;

public class CyclingCadenceBluetooth implements BluetoothDriver.BluetoothParser<CyclingCadenceBluetooth.CrankData> {
    private static final String TAG = CyclingCadenceBluetooth.class.getSimpleName();

    public static final List<ServiceMeasurementUUID> CYCLING_CADENCE = List.of(
            CyclingPowerBluetooth.CYCLING_POWER,
            CyclingDistanceSpeedBluetooth.CYCLING_SPEED_CADENCE
    );

    @Override
    public List<ServiceMeasurementUUID> getServices() {
        return CYCLING_CADENCE;
    }

    @Override
    public CrankData parsePayload(ServiceMeasurementUUID serviceMeasurementUUID, String sensorName, BluetoothGattCharacteristic characteristic) {
        if (serviceMeasurementUUID.equals(CyclingPowerBluetooth.CYCLING_POWER)) {
            CyclingPowerBluetooth.Data data = CyclingPowerBluetooth.parseCyclingPower(characteristic);
            if (data != null && data.crank() != null) {
                return data.crank();
            }
        }

        if (serviceMeasurementUUID.equals(CyclingDistanceSpeedBluetooth.CYCLING_SPEED_CADENCE)) {
            Pair<CyclingDistanceSpeedBluetooth.WheelData, CrankData> data = CyclingDistanceSpeedBluetooth.parseCyclingCrankAndWheel(characteristic);

            if (data != null && data.second != null) {
                return data.second;
            }
        }

        Log.e(TAG, "Don't know how to decode this payload.");
        return null;
    }

    public record CrankData(
            long crankRevolutionsCount, // UINT32
            int crankRevolutionsTime // UINT16; 1/1024s
     ) {}
}
