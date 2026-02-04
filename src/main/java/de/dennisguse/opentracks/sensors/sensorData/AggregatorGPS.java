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
        aggregatedValue = Position.of(current.value(), current.time());
    }

    @Override
    protected void resetImmediate() {
        aggregatedValue = Position.empty();
    }

    @Override
    public void resetAggregated() {
        /*
         * GPS data is not an aggregated value, but for now we want to ensure to only save the data once.
         * The data is too large to save it more often than needed (i.e., duplicated values).
         * TODO: this behavior can be changed if TrackRecordingManager.insertTrackPoint() would strip GPS data if it was already saved. This would simplify TrackPointCreator.createCurrentTrackPoint()
         */
        aggregatedValue = Position.empty();
    }

    @NonNull
    @Override
    protected Position getNoneValue() {
        return Position.empty();
    }

    @NonNull
    @Override
    public Position getAggregatedValue(Instant now) {
        return super.getAggregatedValue(now)
                .with(now);
    }
}
