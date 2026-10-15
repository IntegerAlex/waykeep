package com.waykeep.app.data;

import androidx.annotation.NonNull;

import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.util.AutoCloseableIterator;

public class TrackPointIterator extends AutoCloseableIterator<TrackPoint> {

    TrackPointIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, TrackPoint.Id startTrackPointId) {
        super(contentProviderUtils.getTrackPointCursor(trackId, startTrackPointId));
    }

    @Override
    @NonNull
    public TrackPoint get() {
        return ContentProviderUtils.fillTrackPoint(cursor);
    }
}