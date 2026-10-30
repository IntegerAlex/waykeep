package com.waykeep.app.diagnostics;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.waykeep.app.R;
import com.waykeep.app.util.FileUtils;

/** Builds a user-requested ZIP containing only the latest local recording log. */
public final class DiagnosticsShare {

    private static final String PRIVACY_WARNING =
            "This archive contains exact GPS coordinates and may include fitness data. Share it only with a trusted recipient.";

    private DiagnosticsShare() {
    }

    @Nullable
    public static File createLatestArchive(Context context) throws IOException {
        if (!RecordingDiagnostics.flushActive()) {
            throw new IOException("Could not flush the active diagnostics session.");
        }
        return createArchive(new DiagnosticsStore(context), new File(context.getCacheDir(), "diagnostics-share"));
    }

    static File createArchive(DiagnosticsStore store, File directory) throws IOException {
        DiagnosticsStore.SessionFile latest = store.latestSession();
        if (latest == null) return null;
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create diagnostics share directory.");
        }

        File snapshot = new File(directory, "session-" + latest.sessionId() + ".jsonl");
        File archive = new File(directory, "diagnostics-" + latest.sessionId() + "-" + System.currentTimeMillis() + ".zip");
        // Old archives hold exact coordinates; do not let them accumulate in the cache.
        File[] stale = directory.listFiles((dir, name) -> name.endsWith(".zip"));
        if (stale != null) {
            for (File file : stale) Files.deleteIfExists(file.toPath());
        }
        try {
            RecordingDiagnostics.copySessionSnapshot(latest.file(), snapshot);
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                addText(zip, "manifest.json", manifest(latest.sessionId()));
                zip.putNextEntry(new ZipEntry("session.jsonl"));
                try (FileInputStream input = new FileInputStream(snapshot)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            return archive;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(archive.toPath());
            throw e;
        } finally {
            Files.deleteIfExists(snapshot.toPath());
        }
    }

    public static Intent newShareIntent(Context context, File archive) {
        Uri uri = FileProvider.getUriForFile(context, FileUtils.FILEPROVIDER, archive);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_share_subject))
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.diagnostics_share_privacy_warning))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(context.getContentResolver(), "Recording diagnostics", uri));
        return Intent.createChooser(send, context.getString(R.string.diagnostics_share_title));
    }

    private static String manifest(java.util.UUID sessionId) {
        return "{\"schema_version\":1,\"session_id\":\"" + sessionId
                + "\",\"contains_exact_gps_coordinates\":true,\"may_contain_fitness_data\":true,\"privacy_warning\":\""
                + PRIVACY_WARNING + "\"}\n";
    }

    private static void addText(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
