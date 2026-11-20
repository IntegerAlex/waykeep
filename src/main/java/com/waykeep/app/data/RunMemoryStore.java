package com.waykeep.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.waykeep.app.data.models.RunMemory;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.tables.RunMemoryColumns;
import com.waykeep.app.util.FileUtils;

/**
 * Reads and writes run memories. Deliberately a direct DAO on the app database —
 * memories are private to the app and get no content-provider URI.
 */
public class RunMemoryStore {

    private final Context context;

    public RunMemoryStore(Context context) {
        this.context = context;
    }

    @Nullable
    public RunMemory get(@NonNull Track.Id trackId) {
        try (Cursor cursor = db().query(RunMemoryColumns.TABLE_NAME, null,
                RunMemoryColumns.TRACKID + " = ?", new String[]{String.valueOf(trackId.id())},
                null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    /** One memory per track: a second save replaces the first. */
    public void save(@NonNull RunMemory memory) {
        ContentValues values = new ContentValues();
        values.put(RunMemoryColumns.TRACKID, memory.trackId().id());
        values.put(RunMemoryColumns.MOOD, memory.mood());
        values.put(RunMemoryColumns.PHOTO, memory.photoName());
        db().insertWithOnConflict(RunMemoryColumns.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delete(@NonNull Track.Id trackId) {
        RunMemory memory = get(trackId);
        if (memory != null && memory.photoName() != null) {
            new File(FileUtils.getPhotoDir(context, trackId), memory.photoName()).delete();
        }
        db().delete(RunMemoryColumns.TABLE_NAME, RunMemoryColumns.TRACKID + " = ?",
                new String[]{String.valueOf(trackId.id())});
    }

    /** Memory per track id; tracks without memory are absent. */
    @NonNull
    public Map<Long, RunMemory> getAll() {
        Map<Long, RunMemory> memories = new HashMap<>();
        try (Cursor cursor = db().query(RunMemoryColumns.TABLE_NAME, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                RunMemory memory = fromCursor(cursor);
                memories.put(memory.trackId().id(), memory);
            }
        }
        return memories;
    }

    /**
     * Drops memories whose track is gone (the foreign key only cascades rows, not
     * photo files) and deletes their now-unreferenced photo files.
     */
    public void pruneOrphans() {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        try (Cursor cursor = db().query(RunMemoryColumns.TABLE_NAME, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                RunMemory memory = fromCursor(cursor);
                if (contentProviderUtils.getTrack(memory.trackId()) == null) {
                    delete(memory.trackId());
                }
            }
        }
    }

    @NonNull
    private RunMemory fromCursor(Cursor cursor) {
        return new RunMemory(
                new Track.Id(cursor.getLong(cursor.getColumnIndexOrThrow(RunMemoryColumns.TRACKID))),
                cursor.getInt(cursor.getColumnIndexOrThrow(RunMemoryColumns.MOOD)),
                cursor.getString(cursor.getColumnIndexOrThrow(RunMemoryColumns.PHOTO)));
    }

    private SQLiteDatabase db() {
        return new CustomSQLiteOpenHelper(context).getWritableDatabase();
    }
}
