package de.dennisguse.opentracks.sensors.sensorData;

import android.util.Log;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.sensors.UintUtils;
import de.dennisguse.opentracks.sensors.driver.CyclingCadenceBluetooth;

public class AggregatorCyclingCadence extends Aggregator<CyclingCadenceBluetooth.CrankData, Cadence> {

    private final String TAG = AggregatorCyclingCadence.class.getSimpleName();

    public AggregatorCyclingCadence(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(Raw<CyclingCadenceBluetooth.CrankData> current) {
        if (previous == null) {
            return;
        }

        float timeDiff_ms = UintUtils.diff(current.value().crankRevolutionsTime(), previous.value().crankRevolutionsTime(), UintUtils.UINT16_MAX) / 1024f * 1000;
        Duration timeDiff = Duration.ofMillis((long) timeDiff_ms);

        if (timeDiff.isZero()) {
            return;
        }
        if (timeDiff.isNegative()) {
            Log.e(TAG, "Timestamps difference is invalid: cannot compute cadence.");
            output = null;
            return;
        }

        // TODO We have to treat with overflow according to the documentation: read https://codeberg.org/OpenTracksApp/OpenTracks/pulls/953#issuecomment-6466930
        if (current.value().crankRevolutionsCount() < previous.value().crankRevolutionsCount()) {
            Log.e(TAG, "Crank revolutions count difference is invalid: cannot compute cadence.");
            output = null;
            return;
        }

        long crankDiff = UintUtils.diff(current.value().crankRevolutionsCount(), previous.value().crankRevolutionsCount(), UintUtils.UINT32_MAX);
        output = Cadence.of(crankDiff, timeDiff);
    }
}
