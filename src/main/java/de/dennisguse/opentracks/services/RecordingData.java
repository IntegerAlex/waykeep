package de.dennisguse.opentracks.services;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;

/**
 * {@link Track} and {@link TrackPoint} must be immutable (i.e., their content does not change).
 */
public record RecordingData(
        Track track,
        TrackPoint latestTrackPoint,
        SensorDataSet sensorDataSet,
        Statistics currentSegment) {

    public static final RecordingData NOT_RECORDING = new RecordingData(null, null, null, null);

    public String getTrackCategory() {
        if (track == null) {
            return "";
        }
        return track.getActivityTypeLocalized();
    }

    public Statistics trackStatistics() {
        return track.getStatistics();
    }
}
