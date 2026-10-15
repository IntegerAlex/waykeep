package com.waykeep.app.data;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.tables.TracksColumns;
import com.waykeep.app.util.AutoCloseableIterator;

public class TrackListIterator extends AutoCloseableIterator<TrackListIterator.Item> {

    TrackListIterator(Cursor cursor) {
        super(cursor);
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
        final int movingTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.MOVINGTIME);
        final int altitudeGainIndex = cursor.getColumnIndexOrThrow(TracksColumns.ALTITUDE_GAIN);
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
                Duration.ofMillis(cursor.getLong(movingTimeIndex)),
                Distance.of(cursor.getFloat(totalDistanceIndex)),
                cursor.isNull(altitudeGainIndex) ? null : cursor.getFloat(altitudeGainIndex),
                cursor.getInt(markerCountIndex)
        );
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
            Duration movingTime,

            @NonNull
            Distance totalDistance,

            @Nullable
            Float altitudeGain_m,

            int markerCount
    ) {
    }
}