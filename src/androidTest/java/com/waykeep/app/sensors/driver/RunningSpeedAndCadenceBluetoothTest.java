package com.waykeep.app.sensors.driver;

import static org.junit.Assert.assertEquals;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Test;

import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Speed;

public class RunningSpeedAndCadenceBluetoothTest {

    @Test
    public void parseRunningSpeedAndCadence_with_distance() {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(RunningSpeedAndCadenceBluetooth.RUNNING_SPEED_CADENCE.serviceUUID(), 0, 0);
        characteristic.setValue(new byte[]{2, 0, 5, 80, (byte) 0xFF, (byte) 0xFF, 0, 1});

        // when
        RunningSpeedAndCadenceBluetooth.Data sensor = RunningSpeedAndCadenceBluetooth.parseRunningSpeedAndCadence("sensorName", characteristic);

        // then
        assertEquals(Speed.of(5), sensor.speed());
        assertEquals(Cadence.of(80), sensor.cadence());
        assertEquals(Distance.of(6553.5 + 1677721.6), sensor.totalDistance());
    }
}