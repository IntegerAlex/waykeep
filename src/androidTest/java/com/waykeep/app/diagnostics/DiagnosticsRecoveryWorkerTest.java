package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.io.file.importer.DiagnosticsRecoveryWorker;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class DiagnosticsRecoveryWorkerTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void retryAfterRecoveryInsertDoesNotDuplicateCopy() throws Exception {
        ContentProviderUtils tracks = new ContentProviderUtils(context);
        UUID sourceUuid = UUID.randomUUID();
        UUID recoveryUuid = UUID.randomUUID();
        Track.Id originalId = tracks.insertTrack(new Track(
                null, sourceUuid, "Original", "", "Unknown", ActivityType.UNKNOWN,
                ZoneOffset.UTC, Statistics.DEFAULT));
        File log = File.createTempFile("recovery-worker-", ".jsonl", context.getCacheDir());
        UUID sessionId = UUID.randomUUID();
        Files.writeString(log.toPath(), ""
                + "{\"schema_version\":1,\"session_id\":\"" + sessionId + "\",\"recovery_id\":\"" + recoveryUuid
                + "\",\"source_track_uuid\":\"" + sourceUuid + "\",\"track_id\":" + originalId.id()
                + ",\"sequence\":0,\"event\":\"session_start\"}\n"
                + "{\"schema_version\":1,\"session_id\":\"" + sessionId + "\",\"recovery_id\":\"" + recoveryUuid
                + "\",\"source_track_uuid\":\"" + sourceUuid + "\",\"track_id\":" + originalId.id()
                + ",\"sequence\":1,\"event\":\"gps_fix\",\"data\":{\"fix_time\":\"2026-10-30T01:44:00Z\",\"latitude\":18.5,\"longitude\":73.8,\"horizontal_accuracy_m\":8}}\n"
                + "{\"schema_version\":1,\"session_id\":\"" + sessionId + "\",\"recovery_id\":\"" + recoveryUuid
                + "\",\"source_track_uuid\":\"" + sourceUuid + "\",\"track_id\":" + originalId.id()
                + ",\"sequence\":2,\"event\":\"gps_fix\",\"data\":{\"fix_time\":\"2026-10-30T01:44:05Z\",\"latitude\":18.5001,\"longitude\":73.8001,\"horizontal_accuracy_m\":7}}\n");
        DiagnosticsRecovery.RecoveryCandidate candidate = new DiagnosticsRecovery.RecoveryCandidate(
                sessionId, originalId, recoveryUuid, sourceUuid, log);

        try {
            WorkRequest first = DiagnosticsRecovery.recover(context, candidate);
            assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(first));
            Track recovered = tracks.getTrack(recoveryUuid);
            assertNotNull(recovered);
            assertNotEquals(originalId, recovered.id());
            assertEquals(sourceUuid, tracks.getTrack(originalId).uuid());

            WorkRequest retry = DiagnosticsRecovery.recover(context, candidate);
            assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(retry));
            assertEquals(recovered.id(), tracks.getTrack(recoveryUuid).id());
        } finally {
            Track recovered = tracks.getTrack(recoveryUuid);
            List<Track.Id> cleanup = recovered == null ? List.of(originalId) : List.of(originalId, recovered.id());
            tracks.deleteTracks(context, cleanup);
            Files.deleteIfExists(log.toPath());
        }
    }

    private WorkInfo.State awaitWork(WorkRequest request) throws Exception {
        WorkInfo info = WorkManager.getInstance(context)
                .getWorkInfoById(request.getId())
                .get(60, TimeUnit.SECONDS);
        assertNotNull(info);
        return info.getState();
    }
}
