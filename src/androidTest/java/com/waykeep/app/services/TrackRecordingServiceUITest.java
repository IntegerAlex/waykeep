package com.waykeep.app.services;

import static org.junit.Assert.assertEquals;
import static com.waykeep.app.services.TrackRecordingService.RECORDING_DATA_UPDATE_INTERVAL;
import static com.waykeep.app.util.LiveDataTestUtils.waitForValue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.waykeep.app.TestUtil;
import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.io.file.importer.ExportImportTest;
import com.waykeep.app.io.file.importer.TrackPointAssert;
import com.waykeep.app.sensors.GpsHandler;
import com.waykeep.app.sensors.driver.GpsInternal;
import com.waykeep.app.services.handlers.TrackPointCreator;

@RunWith(AndroidJUnit4.class)
public class TrackRecordingServiceUITest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    private static final Context context = ApplicationProvider.getApplicationContext();

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }

    @Test
    public void getDataForUI_gps() throws TimeoutException {
        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();
        RecordingData recordingData;

        // given
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock("2020-02-02T02:02:02Z");

        // when
        service.startNewTrack();
        disconnectRealGps(trackPointCreator.getSensorManager().getGpsHandler());
        trackPointCreator.getSensorManager().reset();
        RecordingData initialRecordingData = waitUntilLatestTrackPointMatches(service, Instant.parse("2020-02-02T02:02:02Z"), false);

        // then
        TrackPoint initialTrackPoint = initialRecordingData.latestTrackPoint();
        assertEquals(TrackPoint.Type.TRACKPOINT, initialTrackPoint.type());
        assertEquals(Instant.parse("2020-02-02T02:02:02Z"), initialTrackPoint.getTime());


        // when
        // In this step, the data is saved (and Aggregator.resetAggregated called), but data should still be shown in the UI.
        trackPointCreator.setClock("2020-02-02T02:02:03Z");
        ExportImportTest.sendLocation(trackPointCreator, "2020-02-02T02:02:03Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 1f);
        recordingData = waitUntilLatestTrackPointMatches(service, Instant.parse("2020-02-02T02:02:03Z"), true);

        // then
        new TrackPointAssert().assertEquals(new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse("2020-02-02T02:02:03Z"),
                                3.1234567, 14.0014567, null,
                                Altitude.EGM2008.of(1013.05), null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(1, 1)
                ),
                recordingData.latestTrackPoint());
    }

    private void disconnectRealGps(GpsHandler gpsHandler) {
        if (gpsHandler.driver != null) {
            gpsHandler.driver.disconnect();
        }
        gpsHandler.onConnected(null, GpsInternal.LOCATION_PROVIDER);
    }

    private RecordingData waitUntilLatestTrackPointMatches(TrackRecordingService service, Instant expectedTime, boolean requiresLocation) {
        return waitForValue(service.getRecordingDataObservable(),
                recordingData -> {
                    TrackPoint latestTrackPoint = recordingData != null ? recordingData.latestTrackPoint() : null;
                    return latestTrackPoint != null
                            && expectedTime.equals(latestTrackPoint.getTime())
                            && (!requiresLocation || latestTrackPoint.position().hasLocation());
                },
                RECORDING_DATA_UPDATE_INTERVAL.multipliedBy(3),
                "latest track point to reach " + expectedTime + (requiresLocation ? " with location" : ""));
    }
}
