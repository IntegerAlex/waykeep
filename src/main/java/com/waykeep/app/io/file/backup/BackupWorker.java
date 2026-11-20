package com.waykeep.app.io.file.backup;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.io.file.TrackFileFormat;
import com.waykeep.app.io.file.exporter.ExportUtils;
import com.waykeep.app.settings.PreferencesUtils;

/**
 * Writes one compressed KMZ of every track to the user-chosen export directory and
 * keeps only the newest two backups. KMZ is the import format's own format, so
 * recovery is the regular import (which dedups). The directory survives app
 * uninstall, which app-specific storage does not.
 */
public class BackupWorker extends Worker {

    private static final String TAG = BackupWorker.class.getSimpleName();

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean forced = getInputData().getBoolean(BackupUtils.FORCED_KEY, false);
        if (!PreferencesUtils.isAutoBackupEnabled() && !forced) {
            return Result.success();
        }

        Uri directoryUri = PreferencesUtils.getDefaultExportDirectoryUri();
        DocumentFile directory = directoryUri == null ? null : DocumentFile.fromTreeUri(getApplicationContext(), directoryUri);
        if (directory == null || !directory.canWrite()) {
            // Nothing to protect the backup with; the next recording or app start tries again.
            Log.w(TAG, "No writable export directory, skipping backup");
            return Result.success();
        }

        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(getApplicationContext());
        List<Track> tracks = contentProviderUtils.getTracks();
        if (tracks.isEmpty()) {
            return Result.success();
        }

        // Ceiling: renames and edits to existing tracks do not change the state signature,
        // so those do not trigger a fresh backup until the next recording or import.
        // Upgrade path: include per-track modified timestamps once the schema has them.
        String state = tracks.size() + ":" + tracks.stream().mapToLong(t -> t.id().id()).max().orElse(0);
        if (!forced && state.equals(PreferencesUtils.getBackupLastTrackState())) {
            Log.i(TAG, "Tracks unchanged since last backup, skipping");
            return Result.success();
        }

        try {
            writeBackup(directory, tracks);
            prune(directory);
            PreferencesUtils.setBackupLastTrackState(state);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return Result.failure();
        }
    }

    private void writeBackup(DocumentFile directory, List<Track> tracks) throws IOException {
        TrackFileFormat format = TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        DocumentFile backupFile = directory.createFile(format.getMimeType(), BackupRetention.backupFilename(timestamp));
        if (backupFile == null) {
            throw new IOException("Couldn't create backup file in " + directory.getUri());
        }

        try (OutputStream out = getApplicationContext().getContentResolver().openOutputStream(backupFile.getUri(), "wt")) {
            if (out == null || !format.createTrackExporter(getApplicationContext(), new ContentProviderUtils(getApplicationContext()))
                    .writeTrack(tracks, out)) {
                throw new IOException("Couldn't write backup to " + backupFile.getUri());
            }
        } catch (IOException e) {
            // Never leave a half-written file occupying a retention slot.
            backupFile.delete();
            throw e;
        }
    }

    private void prune(DocumentFile directory) {
        List<String> names = new java.util.ArrayList<>();
        for (DocumentFile file : directory.listFiles()) {
            names.add(file.getName() != null ? file.getName() : "");
        }
        for (String obsolete : BackupRetention.selectObsolete(names, 2)) {
            DocumentFile file = directory.findFile(obsolete);
            if (file != null && !file.delete()) {
                Log.w(TAG, "Couldn't delete obsolete backup " + obsolete);
            }
        }
    }
}
