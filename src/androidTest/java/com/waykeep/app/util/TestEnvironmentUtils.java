package com.waykeep.app.util;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.services.TrackRecordingService;

public final class TestEnvironmentUtils {

    private TestEnvironmentUtils() {
    }

    public static void stopTrackRecordingService() {
        Context context = ApplicationProvider.getApplicationContext();
        context.stopService(new Intent(context, TrackRecordingService.class));
    }

    public static void deleteAllTracks() {
        Context context = ApplicationProvider.getApplicationContext();
        new ContentProviderUtils(context).deleteAllTracks(context);
    }

    public static void resetTrackRecordingServiceAndDeleteTracks() {
        stopTrackRecordingService();
        deleteAllTracks();
    }
}
