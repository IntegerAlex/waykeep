package com.waykeep.app.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes run memories for the {@code memories.json} entry inside KMZ backups.
 * Memories are keyed by track UUID (track IDs change on import). Photos are an
 * array; the pre-multi-photo format (a single {@code photo} string) is still read.
 * Damaged JSON decodes to nothing so a broken backup still imports its tracks.
 */
public final class MemoriesCodec {

    private static final int VERSION = 1;
    private static final String KEY_VERSION = "version";
    private static final String KEY_MEMORIES = "memories";
    private static final String KEY_TRACK_UUID = "trackUuid";
    private static final String KEY_MOOD = "mood";
    private static final String KEY_PHOTOS = "photos";
    private static final String KEY_PHOTO = "photo"; // pre-multi-photo format

    private MemoriesCodec() {
    }

    public record Entry(@Nullable String trackUuid, int mood, @NonNull List<String> photoNames) {
    }

    public static String encode(List<Entry> memories) {
        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);
            JSONArray array = new JSONArray();
            for (Entry memory : memories) {
                if (memory.trackUuid() == null) {
                    continue;
                }
                JSONObject entry = new JSONObject();
                entry.put(KEY_TRACK_UUID, memory.trackUuid());
                if (memory.mood() >= 0) {
                    entry.put(KEY_MOOD, memory.mood());
                }
                if (!memory.photoNames().isEmpty()) {
                    JSONArray photos = new JSONArray();
                    for (String photo : memory.photoNames()) {
                        photos.put(photo);
                    }
                    entry.put(KEY_PHOTOS, photos);
                }
                array.put(entry);
            }
            root.put(KEY_MEMORIES, array);
            return root.toString();
        } catch (JSONException e) {
            // Cannot happen with our own keys; a backup without memories is still valid.
            return "{\"" + KEY_VERSION + "\":" + VERSION + ",\"" + KEY_MEMORIES + "\":[]}";
        }
    }

    public static List<Entry> decode(@Nullable String json) {
        List<Entry> memories = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return memories;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.optJSONArray(KEY_MEMORIES);
            if (array == null) {
                return memories;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String trackUuid = entry.optString(KEY_TRACK_UUID, null);
                if (trackUuid == null || trackUuid.isBlank()) {
                    continue;
                }
                memories.add(new Entry(trackUuid, entry.optInt(KEY_MOOD, -1),
                        readPhotos(entry)));
            }
        } catch (JSONException ignored) {
            // Damaged payload: tracks still import, memories are simply absent.
        }
        return memories;
    }

    private static List<String> readPhotos(JSONObject entry) {
        List<String> photoNames = new ArrayList<>();
        JSONArray photos = entry.optJSONArray(KEY_PHOTOS);
        if (photos != null) {
            for (int i = 0; i < photos.length(); i++) {
                String photo = photos.optString(i, null);
                if (photo != null && !photo.isBlank()) {
                    photoNames.add(photo);
                }
            }
            return photoNames;
        }
        // Pre-multi-photo format.
        String single = entry.optString(KEY_PHOTO, null);
        if (single != null && !single.isBlank()) {
            photoNames.add(single);
        }
        return photoNames;
    }
}
