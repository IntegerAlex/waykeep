package com.waykeep.app.sensors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.waykeep.app.sensors.sensorData.Aggregator;

public class SensorHandlerConnectionTest {

    @Test
    public void reportsConnectionIndependentlyOfReceivedData() {
        TestHandler handler = new TestHandler();

        assertFalse(handler.isConnected());
        handler.onConnected(null, null);
        assertTrue(handler.isConnected());
        handler.onDisconnected();
        assertFalse(handler.isConnected());
    }

    private static final class TestHandler extends SensorHandler<Object, Object> {

        private TestHandler() {
            super(null);
        }

        @Override
        protected Aggregator<Object, Object> createAggregator(String sensorAddress, String sensorName) {
            return null;
        }

        @Override
        protected int getSensorPreferenceKey() {
            return 0;
        }
    }
}
