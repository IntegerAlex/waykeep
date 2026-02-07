package de.dennisguse.opentracks.publicapi;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.dennisguse.opentracks.data.ContentProviderUtils;

@RunWith(AndroidJUnit4.class)
public class DataProviderTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Ignore(value = "Compatibility to v2 is not implemented.") //TODO
    @Test
    public void supportsVersion2() {
        ContentResolver resolver = context.getContentResolver();

        //Tracks
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/tracks"),
                new String[]{
                        "_id",
                        "name",
                        "description",
                        "category",
                        "activity_type_localized",
                        "starttime",
                        "stoptime",
                        "totaltime",
                        "movingtime",
                        "totaldistance",
                        "maxspeed",
                        "avgspeed",
                        "avgmovingspeed",
                        "minelevation",
                        "maxelevation",
                        "elevationgain",
                        "elevationloss"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }

        //TrackPoints
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/trackpoints"),
                new String[]{
                        "_id",
                        "trackid",
                        "latitude",
                        "longitude",
                        "time",
                        "type",
                        "latitude",
                        "speed"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }

        //Markers
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/markers"),
                new String[]{
                        "_id",
                        "name",
                        "description",
                        "category",
                        "trackid",
                        "longitude",
                        "latitude",
                        "photoUrl"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }
    }

    @Test
    public void supportsVersion3() {
        ContentResolver resolver = context.getContentResolver();

        //Tracks
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/tracks"),
                new String[]{
                        "_id",
                        "name",
                        "description",
                        "activity_type",
                        "activity_type_localized",
                        "time_start",
                        "time_stop",
                        "duration_total",
                        "duration_moving",
                        "distance",
                        "speed_max",
                        "altitude_min",
                        "altitude_max",
                        "altitude_gain",
                        "altitude_loss"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }

        //TrackPoints
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/trackpoints"),
                new String[]{
                        "_id",
                        "trackid",
                        "latitude",
                        "longitude",
                        "time",
                        "type",
                        "latitude",
                        "speed"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }

        //Markers
        try (Cursor cursor = resolver.query(
                Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/markers"),
                new String[]{
                        "_id",
                        "name",
                        "description",
                        "marker_type_localized",
                        "trackid",
                        "longitude",
                        "latitude",
                        "photoUrl"
                },
                null,
                null,
                null
        )) {
            Assert.assertNotNull(cursor);
        }
    }
}
