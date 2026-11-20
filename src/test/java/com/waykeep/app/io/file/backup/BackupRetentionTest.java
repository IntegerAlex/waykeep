package com.waykeep.app.io.file.backup;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Checks the rolling-backup retention: keep the newest files, mark the rest for deletion.
 */
public class BackupRetentionTest {

    private static String backup(String timestamp) {
        return "waykeep-backup-" + timestamp + ".kmz";
    }

    @Test
    public void keepsTheTwoNewestBackups() {
        List<String> obsolete = BackupRetention.selectObsolete(Arrays.asList(
                backup("20261118-070000"), backup("20261119-070000"), backup("20261120-070000")),
                2);

        assertEquals(List.of(backup("20261118-070000")), obsolete);
    }

    @Test
    public void sortsBeforeSelectingSoInputOrderDoesNotMatter() {
        List<String> obsolete = BackupRetention.selectObsolete(Arrays.asList(
                backup("20261120-070000"), backup("20261118-070000"), backup("20261119-070000")),
                2);

        assertEquals(List.of(backup("20261118-070000")), obsolete);
    }

    @Test
    public void keepsEverythingWhenThereIsRoom() {
        List<String> obsolete = BackupRetention.selectObsolete(Arrays.asList(
                backup("20261119-070000"), backup("20261120-070000")), 2);

        assertEquals(List.of(), obsolete);
    }

    @Test
    public void ignoresFilesOutsideTheBackupPrefix() {
        List<String> obsolete = BackupRetention.selectObsolete(Arrays.asList(
                backup("20261118-070000"), "2026-10-29T07:44+0530.kmz", "notes.txt"), 2);

        // The backup is the newest of its kind, so it is kept; foreign files are never touched.
        assertEquals(List.of(), obsolete);
    }

    @Test
    public void deletesAllButTheNewestWhenFlooding() {
        List<String> obsolete = BackupRetention.selectObsolete(Arrays.asList(
                backup("20261117-070000"), backup("20261118-070000"), backup("20261119-070000"),
                backup("20261120-070000"), backup("20261121-070000")), 2);

        assertEquals(List.of(backup("20261117-070000"), backup("20261118-070000"),
                backup("20261119-070000")), obsolete);
    }
}
