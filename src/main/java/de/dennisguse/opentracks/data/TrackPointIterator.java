package de.dennisguse.opentracks.data;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.NoSuchElementException;

import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.util.AutoCloseableIterator;

/**
 * A lightweight wrapper around the original {@link Cursor}.
 */
class TrackPointIterator extends AutoCloseableIterator<TrackPoint> {

    private static final String TAG = TrackPointIterator.class.getSimpleName();

    private final ContentProviderUtils contentProviderUtils;
    private final Track.Id trackId;
    private final CachedTrackPointsIndexes indexes;
    private Cursor cursor;

    TrackPointIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, TrackPoint.Id startTrackPointId) {
        this.contentProviderUtils = contentProviderUtils;
        this.trackId = trackId;

        cursor = contentProviderUtils.getTrackPointCursor(trackId, startTrackPointId);
        indexes = new CachedTrackPointsIndexes(cursor);
    }

    @Override
    public boolean hasNext() {
        if (cursor == null) {
            return false;
        }
        return !cursor.isLast() && !cursor.isAfterLast();
    }

    @Override
    @NonNull
    public TrackPoint next() {
        if (cursor == null || !cursor.moveToNext()) {
            throw new NoSuchElementException();
        }
        return ContentProviderUtils.fillTrackPoint(cursor, indexes);
    }

    @Override
    public int getPosition() {
        return cursor.getPosition();
    }

    @VisibleForTesting
    public int getCount() {
        return cursor.getCount();
    }

    @Override
    public void close() {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}