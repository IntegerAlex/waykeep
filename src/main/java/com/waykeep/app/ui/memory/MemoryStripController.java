package com.waykeep.app.ui.memory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import com.waykeep.app.R;
import com.waykeep.app.data.RunMemoryStore;
import com.waykeep.app.data.models.RunMemory;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.util.FileUtils;

/**
 * Wires the shared "How was it?" strip: five mood stickers plus an optional photo.
 * Every change is written through immediately — the memory exists the moment the
 * runner taps, not after some save button.
 */
public class MemoryStripController {

    private final AppCompatActivity activity;
    private final RunMemoryStore store;
    private final Track.Id trackId;
    private final ImageView[] moodViews;

    @Nullable
    private Integer selectedMood;
    @Nullable
    private String savedPhotoName;
    @Nullable
    private final Runnable onSaved;

    @Nullable
    private View strip;

    public MemoryStripController(@NonNull AppCompatActivity activity, @NonNull View strip,
                                 @NonNull Track.Id trackId, @Nullable Runnable onSaved) {
        this(activity, strip, trackId, onSaved, null);
    }

    /**
     * @param launchPhotoPicker required when the strip is shown after onCreate (dialogs) —
     *        registerForActivityResult may not be called once STARTED. The host launches
     *        its pre-registered picker and calls {@link #onPhotoPicked(Uri)} with the result.
     */
    public MemoryStripController(@NonNull AppCompatActivity activity, @NonNull View strip,
                                 @NonNull Track.Id trackId, @Nullable Runnable onSaved,
                                 @Nullable Runnable launchPhotoPicker) {
        this.activity = activity;
        this.store = new RunMemoryStore(activity);
        this.trackId = trackId;
        this.onSaved = onSaved;
        this.strip = strip;

        moodViews = new ImageView[]{
                strip.findViewById(R.id.memory_mood_0),
                strip.findViewById(R.id.memory_mood_1),
                strip.findViewById(R.id.memory_mood_2),
                strip.findViewById(R.id.memory_mood_3),
                strip.findViewById(R.id.memory_mood_4),
        };

        RunMemory existing = store.get(trackId);
        if (existing != null) {
            selectedMood = existing.mood();
            savedPhotoName = existing.photoName();
            showPhotoPreview(strip, existing.photoName());
        }
        paintMoods();

        if (launchPhotoPicker != null) {
            strip.findViewById(R.id.memory_photo).setOnClickListener(v -> launchPhotoPicker.run());
        } else {
            ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
                    activity.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                        if (uri != null) {
                            onPhotoPicked(uri);
                        }
                    });
            strip.findViewById(R.id.memory_photo).setOnClickListener(v ->
                    photoPicker.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()));
        }

        for (int mood = RunMemory.MOOD_MIN; mood <= RunMemory.MOOD_MAX; mood++) {
            int captured = mood;
            moodViews[mood].setOnClickListener(v -> {
                // Tap the selected sticker again to clear the mood.
                selectedMood = selectedMood != null && selectedMood == captured ? RunMemory.MOOD_UNSET : captured;
                paintMoods();
                writeThrough(onSaved);
            });
        }
    }

    /** Entry point for a host-provided photo picker result. */
    public void onPhotoPicked(@Nullable Uri uri) {
        if (uri != null && strip != null) {
            attachPhoto(strip, uri);
        }
    }

    private void paintMoods() {
        for (int mood = RunMemory.MOOD_MIN; mood <= RunMemory.MOOD_MAX; mood++) {
            boolean selected = selectedMood != null && selectedMood == mood;
            moodViews[mood].setAlpha(selected ? 1f : 0.45f);
            moodViews[mood].setScaleX(selected ? 1.15f : 1f);
            moodViews[mood].setScaleY(selected ? 1.15f : 1f);
        }
    }

    private void attachPhoto(View strip, Uri uri) {
        try {
            File dir = FileUtils.getPhotoDir(activity, trackId);
            String newName = "memory-" + UUID.randomUUID() + ".jpg";
            File out = new File(dir, newName);
            if (copyScaled(uri, out)) {
                if (savedPhotoName != null && !savedPhotoName.equals(newName)) {
                    new File(dir, savedPhotoName).delete();
                }
                savedPhotoName = newName;
                showPhotoPreview(strip, newName);
                writeThrough(onSaved);
            }
        } catch (IOException | RuntimeException e) {
            android.widget.Toast.makeText(activity, R.string.memory_photo_failed, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Long-edge capped at 1600px so backups stay small. */
    private boolean copyScaled(Uri uri, File target) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1600 * 2) {
            sample *= 2;
        }
        BitmapFactory.Options scaled = new BitmapFactory.Options();
        scaled.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, scaled);
        }
        if (bitmap == null) {
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        }
        bitmap.recycle();
        return true;
    }

    private void showPhotoPreview(View strip, @Nullable String photoName) {
        ImageView preview = strip.findViewById(R.id.memory_photo);
        if (photoName == null) {
            preview.setImageResource(R.drawable.ic_add_a_photo_24dp);
            preview.setPadding(pad(), pad(), pad(), pad());
            return;
        }
        File file = new File(FileUtils.getPhotoDir(activity, trackId), photoName);
        if (file.isFile()) {
            preview.setImageURI(Uri.fromFile(file));
            preview.setPadding(0, 0, 0, 0);
        }
    }

    private int pad() {
        return Math.round(10 * activity.getResources().getDisplayMetrics().density);
    }

    private void writeThrough(@Nullable Runnable onSaved) {
        Integer mood = selectedMood;
        String photoName = savedPhotoName;
        if ((mood == null || mood == RunMemory.MOOD_UNSET) && photoName == null) {
            store.delete(trackId);
        } else {
            store.save(new RunMemory(trackId, mood == null ? RunMemory.MOOD_UNSET : mood, photoName));
        }
        if (onSaved != null) {
            onSaved.run();
        }
    }
}
