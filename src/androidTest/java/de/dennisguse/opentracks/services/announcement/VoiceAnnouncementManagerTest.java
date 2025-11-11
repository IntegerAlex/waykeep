package de.dennisguse.opentracks.services.announcement;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.stats.SegmentStatisticUpdater;

@RunWith(AndroidJUnit4.class)
public class VoiceAnnouncementManagerTest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }

    @AfterClass
    public static void finalTearDown() {
        if (Looper.myLooper() != null) Looper.myLooper().quit();
    }


    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void calculateNextTaskDistance() throws TimeoutException {
        // given
        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();

        VoiceAnnouncementManager voiceAnnouncementManager = new VoiceAnnouncementManager(service);
        voiceAnnouncementManager.setFrequency(Distance.ofKilometer(5));

        // when
        SegmentStatisticUpdater statistics = new SegmentStatisticUpdater();
        statistics.setTotalDistance(Distance.ofKilometer(13));
        voiceAnnouncementManager.start(statistics.getStatistics());
        assertEquals(Distance.of(15000), voiceAnnouncementManager.getNextTotalDistance());

        statistics.setTotalDistance(Distance.of(15100));
        voiceAnnouncementManager.start(statistics.getStatistics());
        assertEquals(Distance.of(20000), voiceAnnouncementManager.getNextTotalDistance());
    }

    @Test
    public void calculateNextTotalTime() throws TimeoutException {
        // given
        TrackRecordingService service = ((TrackRecordingService.Binder) mServiceRule.bindService(new Intent(context, TrackRecordingService.class)))
                .getService();

        VoiceAnnouncementManager voiceAnnouncementManager = new VoiceAnnouncementManager(service);
        voiceAnnouncementManager.setFrequency(Duration.ofSeconds(5));

        Statistics first = new Statistics(
                null,
                null,
                Duration.ofSeconds(91),
                Duration.ZERO,
                Distance.ZERO,
                null,
                null,
                null,
                null,
                null
        );

        Statistics second = new Statistics(
                null,
                null,
                Duration.ofSeconds(95),
                Duration.ZERO,
                Distance.ZERO,
                null,
                null,
                null,
                null,
                null
        );

        // when
        voiceAnnouncementManager.start(first);
        assertEquals(Duration.ofSeconds(95), voiceAnnouncementManager.getNextTotalTime());

        voiceAnnouncementManager.start(second);
        assertEquals(Duration.ofSeconds(100), voiceAnnouncementManager.getNextTotalTime());
    }

}