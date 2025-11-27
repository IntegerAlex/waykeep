package de.dennisguse.opentracks.data;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.NoSuchElementException;

import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.util.AutoCloseableIterator;

class MarkerIterator extends AutoCloseableIterator<Marker> {

    private static final String TAG = MarkerIterator.class.getSimpleName();

    private final ContentProviderUtils contentProviderUtils;
    private Cursor cursor;

    MarkerIterator(ContentProviderUtils contentProviderUtils, Track.Id trackId, int maxCount) {
        this.contentProviderUtils = contentProviderUtils;

        cursor = contentProviderUtils.getMarkerCursor(trackId, maxCount);
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
    public Marker next() {
        if (cursor == null || !cursor.moveToNext()) {
            throw new NoSuchElementException();
        }
        return contentProviderUtils.createMarker(cursor);
    }

    @Override
    public int getPosition() {
        return cursor.getPosition();
    }

    @Override
    public boolean moveToPosition(int position) {
        return cursor.moveToPosition(position);
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