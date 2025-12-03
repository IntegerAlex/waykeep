package de.dennisguse.opentracks.data;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.util.AutoCloseableIterator;

public class TrackListIterator extends AutoCloseableIterator<TrackListIterator.Item> {

    private static final String TAG = TrackListIterator.class.getSimpleName();

    private Cursor cursor;

    TrackListIterator(Cursor cursor) {
        this.cursor = cursor;
    }

    @NonNull
    @Override
    public Item get() {
        final int idIndex = cursor.getColumnIndexOrThrow(TracksColumns._ID);
        final int nameIndex = cursor.getColumnIndexOrThrow(TracksColumns.NAME);
        final int descriptionIndex = cursor.getColumnIndexOrThrow(TracksColumns.DESCRIPTION);
        final int activityTypeIndex = cursor.getColumnIndexOrThrow(TracksColumns.ACTIVITY_TYPE);
        final int activityTypeLocalizedIndex = cursor.getColumnIndexOrThrow(TracksColumns.ACTIVITY_TYPE_LOCALIZED);
        final int startTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.STARTTIME);
        final int startTimeOffsetIndex = cursor.getColumnIndexOrThrow(TracksColumns.STARTTIME_OFFSET);
        final int totalDistanceIndex = cursor.getColumnIndexOrThrow(TracksColumns.TOTALDISTANCE);
        final int totalTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.TOTALTIME);
        final int markerCountIndex = cursor.getColumnIndexOrThrow(TracksColumns.MARKER_COUNT);

        return new Item(
                new Track.Id(cursor.getLong(idIndex)),
                cursor.getString(nameIndex),
                cursor.getString(descriptionIndex),
                ActivityType.findBy(cursor.getString(activityTypeIndex)),
                cursor.getString(activityTypeLocalizedIndex),
                Instant.ofEpochMilli(cursor.getLong(startTimeIndex)),
                ZoneOffset.ofTotalSeconds(cursor.getInt(startTimeOffsetIndex)),
                Duration.ofMillis(cursor.getLong(totalTimeIndex)),
                Distance.of(cursor.getFloat(totalDistanceIndex)),
                cursor.getInt(markerCountIndex)
        );
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
    public Item next() {
        if (cursor == null || !cursor.moveToNext()) {
            throw new NoSuchElementException();
        }
        return get();
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

    public record Item(
            @NonNull
            Track.Id id,

            @NonNull
            String name,

            @NonNull
            String description,

            @NonNull
            ActivityType activityType,

            @NonNull
            String activityTypeLocalized,

            @NonNull
            Instant startTime,

            @NonNull
            ZoneOffset zoneOffset,

            @NonNull
            Duration totalTime,

            @NonNull
            Distance totalDistance,

            int markerCount

    ) {
    }
}