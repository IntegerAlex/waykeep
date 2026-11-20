package com.waykeep.app.io.file.backup;

import android.content.Context;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.waykeep.app.settings.PreferencesUtils;

/**
 * Schedules the rolling backup: after every finished recording and on every app
 * open, as one unique one-time work. The worker skips when no track changed since
 * the last backup, so app opens are cheap — and because all data changes happen
 * inside the app, an unchanged database never needs a fresh backup. One unique
 * name also means triggers can never race each other into duplicate files.
 */
public final class BackupUtils {

    static final String FORCED_KEY = "forced";

    private static final String ONESHOT_WORK_NAME = "backup-now";

    private BackupUtils() {
    }

    /** Enqueue a backup for the moments something changed: recording finished, import ran. */
    public static void postBackup(Context context) {
        if (!PreferencesUtils.isAutoBackupEnabled() || !PreferencesUtils.isDefaultExportDirectoryUri()) {
            return;
        }
        WorkManager.getInstance(context).enqueueUniqueWork(ONESHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(BackupWorker.class).build());
    }

    /** Backup regardless of the automatic trigger settings, e.g. the Back up now button. */
    public static void postBackupNow(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(ONESHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(BackupWorker.class)
                        .setInputData(new androidx.work.Data.Builder().putBoolean(FORCED_KEY, true).build())
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build());
    }
}
