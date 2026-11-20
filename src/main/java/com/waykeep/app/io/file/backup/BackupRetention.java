package com.waykeep.app.io.file.backup;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rolling-backup retention: timestamped backup names sort chronologically, so the
 * newest files stay and the rest are marked for deletion. Files outside the backup
 * prefix are never selected — user exports live in the same folder.
 */
public final class BackupRetention {

    public static final String PREFIX = "waykeep-backup-";
    public static final String EXTENSION = ".kmz";

    private BackupRetention() {
    }

    /**
     * @param filenames file names (not paths) in the backup directory, any order
     * @param keep      how many newest backups to keep
     * @return the subset to delete, oldest first
     */
    @NonNull
    public static List<String> selectObsolete(@NonNull List<String> filenames, int keep) {
        List<String> backups = new ArrayList<>();
        for (String name : filenames) {
            if (name.startsWith(PREFIX) && name.endsWith(EXTENSION)) {
                backups.add(name);
            }
        }
        Collections.sort(backups);
        int deleteCount = Math.max(backups.size() - keep, 0);
        return backups.subList(0, deleteCount);
    }

    /** Backup file name for a point in time, e.g. {@code waykeep-backup-20261120-121534.kmz}. */
    @NonNull
    public static String backupFilename(@NonNull String timestamp) {
        return PREFIX + timestamp + EXTENSION;
    }
}
