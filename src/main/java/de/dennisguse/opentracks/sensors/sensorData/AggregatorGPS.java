package de.dennisguse.opentracks.sensors.sensorData;

import android.location.Location;

import androidx.annotation.NonNull;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.Position;

public class AggregatorGPS extends Aggregator<Location, Position> {

    public AggregatorGPS(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(Raw<Location> current) {
        output = Position.of(current.value(), current.time());
    }

    @Override
    public void resetAggregated() {
        /*
         * GPS data is not an aggregated value, but for now we want to ensure to only save the data once.
         * The data is too large to save it more often than needed (i.e., duplicated values).
         * TODO: this behavior can be changed if TrackRecordingManager.insertTrackPoint() would strip GPS data if it was already saved. This would simplify TrackPointCreator.createCurrentTrackPoint()
         */
        output = null;
    }

    @NonNull
    @Override
    public Position getAggregatedValue(Instant now) {
        Position value = super.getAggregatedValue(now);
        if (value == null) value = Position.empty();

        return value.with(now);
    }
}
