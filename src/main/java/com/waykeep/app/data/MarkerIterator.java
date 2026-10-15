package com.waykeep.app.data;

import android.database.Cursor;

import androidx.annotation.NonNull;

import com.waykeep.app.data.models.Marker;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.util.AutoCloseableIterator;

public class MarkerIterator extends AutoCloseableIterator<Marker> {

    private final ContentProviderUtils contentProviderUtils;

    MarkerIterator(ContentProviderUtils contentProviderUtils, Cursor cursor) {
        super(cursor);
        this.contentProviderUtils = contentProviderUtils;
    }

    MarkerIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, int maxCount) {
        super(contentProviderUtils.getMarkerCursor(trackId, maxCount));
        this.contentProviderUtils = contentProviderUtils;
    }

    @NonNull
    @Override
    public Marker get() {
        return contentProviderUtils.createMarker(cursor);
    }
}