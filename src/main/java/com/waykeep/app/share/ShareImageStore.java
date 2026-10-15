package com.waykeep.app.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Writes share-image PNGs under the FileProvider-shared pictures dir and purges stale ones.
 */
public class ShareImageStore {

    private static final String DIR = "pictures";
    private static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;

    private ShareImageStore() {
    }

    @Nullable
    public static File save(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String prefix) {
        File dir = new File(context.getExternalFilesDir(null), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File file = new File(dir, prefix + "-" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                return null;
            }
            out.flush();
        } catch (IOException e) {
            return null;
        }
        purge(dir, file);
        return file;
    }

    @NonNull
    public static Uri uri(@NonNull Context context, @NonNull File file) {
        return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", file);
    }

    private static void purge(File dir, File keep) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (File file : files) {
            if (file.lastModified() < cutoff && !file.equals(keep) && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}
