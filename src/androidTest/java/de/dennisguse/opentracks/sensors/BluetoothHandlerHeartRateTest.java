package de.dennisguse.opentracks.sensors;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Test;

import de.dennisguse.opentracks.data.models.HeartRate;

public class BluetoothHandlerHeartRateTest {

    @Test
    public void parseHeartRate_uint8() {
        // given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(BluetoothHandlerHeartRate.HEARTRATE.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0x02, 0x3C});

        // when
        HeartRate heartRate = BluetoothHandlerHeartRate.parseHeartRate(characteristic);

        // then
        assertEquals(HeartRate.of(60), heartRate);
    }

    @Test
    public void parseHeartRate_uint16() {
        // given
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(BluetoothHandlerHeartRate.HEARTRATE.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{0x01, 0x01, 0x01});

        // when
        HeartRate heartRate = BluetoothHandlerHeartRate.parseHeartRate(characteristic);

        // then
        assertEquals(HeartRate.of(257), heartRate);
    }
}