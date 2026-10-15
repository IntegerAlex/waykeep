package com.waykeep.app.ui.trackmap;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;

/**
 * Owns the osmdroid tile cache location (internal cache dir: never auto-backed-up)
 * and reports/clears its size. All I/O must run off the UI thread.
 */
public final class MapTileCache {

    private MapTileCache() {
    }

    @NonNull
    public static File dir(@NonNull Context context) {
        return new File(new File(context.getCacheDir(), "osmdroid"), "tiles");
    }

    @WorkerThread
    public static long sizeBytes(@NonNull File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            size += file.isDirectory() ? sizeBytes(file) : file.length();
        }
        return size;
    }

    @WorkerThread
    public static void clear(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                clear(file);
            }
            file.delete();
        }
    }
}
