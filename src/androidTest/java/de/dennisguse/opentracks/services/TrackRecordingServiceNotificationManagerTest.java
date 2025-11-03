package de.dennisguse.opentracks.services;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.settings.UnitSystem;

@RunWith(MockitoJUnitRunner.class)
public class TrackRecordingServiceNotificationManagerTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Mock
    private NotificationCompat.Builder notificationCompatBuilder;

    @Mock
    private NotificationManager notificationManager;

    @Test
    public void updateLocation_triggersAlertOnlyOnFirstInaccurateLocation() {
        TrackPoint trackPointMock = new TrackPoint(TrackPoint.Type.TRACKPOINT,
                new Position(
                        null,
                        null, null, Distance.of(999f),
                        Altitude.WGS84.of(10), null,
                        null,
                        Speed.of(0))
        );

        when(notificationCompatBuilder.setOnlyAlertOnce(anyBoolean()))
                .thenReturn(notificationCompatBuilder);

        TrackRecordingServiceNotificationManager subject = new TrackRecordingServiceNotificationManager(notificationManager, notificationCompatBuilder);
        subject.setUnitSystem(UnitSystem.METRIC);

        Track track = new Track(new Statistics(
                null,
                null,
                null,
                null,
                Distance.of(0),
                null,
                null,
                null,
                null,
                null
        ));
        RecordingData data = new RecordingData(track, trackPointMock, null, null);

        // when
        subject.updateTrackPoint(context, data, Distance.of(100));
        subject.updateTrackPoint(context, data, Distance.of(100));
        subject.updateTrackPoint(context, data, Distance.of(1000));
        subject.updateTrackPoint(context, data, Distance.of(100));

        // then
        verify(notificationCompatBuilder, times(6)).setOnlyAlertOnce(true);
        verify(notificationCompatBuilder, times(2)).setOnlyAlertOnce(false);
    }
}