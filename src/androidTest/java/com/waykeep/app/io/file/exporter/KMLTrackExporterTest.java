package com.waykeep.app.io.file.exporter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.waykeep.app.TestUtil;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Marker;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.io.file.TrackFileFormat;
import com.waykeep.app.io.file.importer.ExportImportTest;
import com.waykeep.app.services.TrackRecordingService;
import com.waykeep.app.services.handlers.TrackPointCreator;
import com.waykeep.app.settings.PreferencesUtils;

@RunWith(JUnit4.class)
public class KMLTrackExporterTest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    private static final Context context = ApplicationProvider.getApplicationContext();

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();

        PreferencesUtils.resetPreferences(context, true);
    }

    @Test
    public void twoTrack_with_oneMarker() throws TimeoutException, IOException {
        // given
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);

        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();

        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        final Track.Id trackIdOne;
        { // first track
            trackPointCreator.setClock("2020-02-02T02:02:02Z");
            trackIdOne = service.startNewTrack();

            Position position = ExportImportTest.sendLocation(trackPointCreator, "2020-02-02T02:02:03Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 1f);
            contentProviderUtils.insertMarker(
                    new Marker(
                            null,
                            trackIdOne,
                            "Marker 1",
                            "Marker 1 desc",
                            "Marker 1 typeLocalized",
                            position,
                            null
                    ));
            service.endCurrentTrack();
        }

        final Track.Id trackIdTwo;
        {  // second track
            trackPointCreator.setClock("2022-02-02T02:02:02Z");
            trackIdTwo = service.startNewTrack();

            Position position = ExportImportTest.sendLocation(trackPointCreator, "2022-02-02T02:02:03Z", 3.1234567, 14.0014567, 10, 13, 15, 1020.25, 1f);
            contentProviderUtils.insertMarker(
                    new Marker(
                            null,
                            trackIdTwo,
                            "Marker 2",
                            "Marker 2 desc",
                            "Marker 2 typeLocalized",
                            position,
                            null
                    ));
            service.endCurrentTrack();
        }

        // when
        TrackExporter exporter = TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.createTrackExporter(context, new ContentProviderUtils(context));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        exporter.writeTrack(
                List.of(contentProviderUtils.getTrack(trackIdOne), contentProviderUtils.getTrack(trackIdTwo)),
                outputStream
        );

        // then
        try (InputStream inputStreamExpected = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(com.waykeep.app.test.R.raw.kml23_twotracks)) {
            String expectation = new String(inputStreamExpected.readAllBytes(), StandardCharsets.UTF_8);
            String expectationAdjusted = expectation
                    .replace("ece162fb-d614-4431-9d33-eb20f8ee8e1d", contentProviderUtils.getTrack(trackIdOne).uuid().toString())
                    .replace("6712f4ca-3456-4739-841a-9e62760cc447", contentProviderUtils.getTrack(trackIdTwo).uuid().toString());
            assertEquals(expectationAdjusted, outputStream.toString());
        }
    }
}