package de.dennisguse.opentracks.services;

import static org.junit.Assert.assertEquals;
import static de.dennisguse.opentracks.services.TrackRecordingService.RECORDING_DATA_UPDATE_INTERVAL;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.core.location.LocationManagerCompat;
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

import de.dennisguse.opentracks.TestUtil;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.io.file.importer.ExportImportTest;
import de.dennisguse.opentracks.io.file.importer.TrackPointAssert;
import de.dennisguse.opentracks.sensors.driver.GpsInternal;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;

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
    public void getDataForUI_gps() throws TimeoutException, InterruptedException {
        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();
        RecordingData recordingData;

        // given
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock("2020-02-02T02:02:02Z");

        // when
        service.startNewTrack();
        //We do not want the real GPS to interfere.
        GpsInternal gpsInternal = (GpsInternal)trackPointCreator.getSensorManager().getGpsHandler().driver;
        LocationManagerCompat.removeUpdates(gpsInternal.locationManager, gpsInternal.locationListenerCompat);
        Thread.sleep(RECORDING_DATA_UPDATE_INTERVAL.toMillis());

        // then
        assertEquals(new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of( Instant.parse("2020-02-02T02:02:02Z")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                service.getRecordingDataObservable().getValue().latestTrackPoint());


        // when
        // In this step, the data is saved (and Aggregator.resetAggregated called), but data should still be shown in the UI.
        trackPointCreator.setClock("2020-02-02T02:02:03Z");
        ExportImportTest.sendLocation(trackPointCreator, "2020-02-02T02:02:03Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 1f);

        // then
        Thread.sleep(RECORDING_DATA_UPDATE_INTERVAL.toMillis());
        recordingData = service.getRecordingDataObservable().getValue();
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
}