package com.waykeep.app.chart;

import com.waykeep.app.data.TrackDataHub;

/**
 * Interface for communication between activities that use {@link TrackDataHub} and their fragments that need thi data hub.
 */
public interface TrackDataHubInterface {
    TrackDataHub getTrackDataHub();
}
