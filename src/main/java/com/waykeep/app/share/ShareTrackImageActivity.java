package com.waykeep.app.share;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.ActivityShareTrackImageBinding;
import com.waykeep.app.util.RouteSampler;

/**
 * Previews a Strava-style shareable stat card and sends it as a PNG image.
 * Rendering happens off the UI thread; the bitmap is re-rendered when the
 * user changes aspect ratio or style.
 */
public class ShareTrackImageActivity extends AppCompatActivity {

    public static final String EXTRA_TRACK_ID = "track_id";

    private static final int IMAGE_WIDTH_PX = 1080;
    private static final int HEIGHT_4_5_PX = 1350;
    private static final int HEIGHT_1_1_PX = 1080;
    private static final int HEIGHT_9_16_PX = 1920;

    private enum Aspect {
        FEED_4_5,
        SQUARE_1_1,
        STORY_9_16
    }

    private ActivityShareTrackImageBinding viewBinding;
    private ExecutorService executor;

    private Track.Id trackId;
    private Track track;
    private RouteSampler.Route route;
    private Bitmap previewBitmap;
    private Aspect aspect = Aspect.FEED_4_5;
    private TrackShareImageGenerator.Style style = TrackShareImageGenerator.Style.STATS_CARD;
    private TrackShareImageGenerator.Background background = TrackShareImageGenerator.Background.DARK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityShareTrackImageBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        executor = Executors.newSingleThreadExecutor();

        trackId = getIntent().getParcelableExtra(EXTRA_TRACK_ID);
        if (trackId == null) {
            finish();
            return;
        }

        viewBinding.shareImageAspectFeed.setOnClickListener(v -> setAspect(Aspect.FEED_4_5));
        viewBinding.shareImageAspectSquare.setOnClickListener(v -> setAspect(Aspect.SQUARE_1_1));
        viewBinding.shareImageAspectStory.setOnClickListener(v -> setAspect(Aspect.STORY_9_16));
        viewBinding.shareImageStyleStats.setOnClickListener(v -> setStyle(TrackShareImageGenerator.Style.STATS_CARD));
        viewBinding.shareImageStyleRoute.setOnClickListener(v -> setStyle(TrackShareImageGenerator.Style.ROUTE_ONLY));
        viewBinding.shareImageBgDark.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.DARK));
        viewBinding.shareImageBgLight.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.LIGHT));
        viewBinding.shareImageBgTransparent.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.TRANSPARENT));
        viewBinding.shareImageBgMap.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.MAP_TINT));
        viewBinding.shareImageShareButton.setOnClickListener(v -> shareImage());
        viewBinding.shareImageDownloadButton.setOnClickListener(v -> downloadImage());
        background = TrackShareImageGenerator.loadBackground(this);
        syncToggleGroups();

        loadTrackAsync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        viewBinding = null;
    }

    private void setAspect(Aspect selected) {
        aspect = selected;
        renderPreview();
    }

    private void setStyle(TrackShareImageGenerator.Style selected) {
        style = selected;
        renderPreview();
    }

    private static int heightFor(Aspect aspect) {
        return switch (aspect) {
            case SQUARE_1_1 -> HEIGHT_1_1_PX;
            case STORY_9_16 -> HEIGHT_9_16_PX;
            case FEED_4_5 -> HEIGHT_4_5_PX;
        };
    }

    private void setBackground(TrackShareImageGenerator.Background selected) {
        background = selected;
        TrackShareImageGenerator.saveBackground(this, selected);
        renderPreview();
    }

    /**
     * Checks the persisted/initial state in the toggle groups (single-selection + required
     * already keeps exactly one checked; this only aligns them on first load).
     */
    private void syncToggleGroups() {
        viewBinding.shareImageAspectGroup.check(aspect == Aspect.SQUARE_1_1 ? R.id.share_image_aspect_square
                : aspect == Aspect.STORY_9_16 ? R.id.share_image_aspect_story : R.id.share_image_aspect_feed);
        viewBinding.shareImageStyleGroup.check(style == TrackShareImageGenerator.Style.ROUTE_ONLY
                ? R.id.share_image_style_route : R.id.share_image_style_stats);
        viewBinding.shareImageBgGroup.check(background == TrackShareImageGenerator.Background.LIGHT ? R.id.share_image_bg_light
                : background == TrackShareImageGenerator.Background.TRANSPARENT ? R.id.share_image_bg_transparent
                : background == TrackShareImageGenerator.Background.MAP_TINT ? R.id.share_image_bg_map : R.id.share_image_bg_dark);
    }

    private void loadTrackAsync() {
        viewBinding.shareImageProgress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            ContentProviderUtils contentProviderUtils = new ContentProviderUtils(this);
            Track loaded = contentProviderUtils.getTrack(trackId);
            RouteSampler.Route sampled = loaded == null
                    ? new RouteSampler.Route(java.util.List.of())
                    : RouteSampler.sample(contentProviderUtils, trackId, 1500);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewBinding.shareImageProgress.setVisibility(View.GONE);
                if (loaded == null) {
                    Toast.makeText(this, R.string.share_image_error, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                track = loaded;
                route = sampled;
                renderPreview();
            });
        });
    }

    private void renderPreview() {
        if (track == null || route == null) {
            return;
        }
        viewBinding.shareImageProgress.setVisibility(View.VISIBLE);
        TrackShareImageGenerator.Style selectedStyle = style;
        Aspect selectedAspect = aspect;
        TrackShareImageGenerator.Background selectedBackground = background;
        executor.execute(() -> {
            Bitmap rendered;
            try {
                rendered = TrackShareImageGenerator.render(this, track, route, selectedStyle, selectedBackground,
                        IMAGE_WIDTH_PX, heightFor(selectedAspect));
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        viewBinding.shareImageProgress.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.share_image_error, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    rendered.recycle();
                    return;
                }
                if (previewBitmap != null) {
                    previewBitmap.recycle();
                }
                previewBitmap = rendered;
                viewBinding.shareImagePreview.setImageBitmap(previewBitmap);
                viewBinding.shareImageProgress.setVisibility(View.GONE);
            });
        });
    }

    private interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    /**
     * Renders the current selection off the UI thread and delivers the bitmap on it.
     * The receiver owns the bitmap (must recycle); it is recycled here if dead.
     */
    private void renderForExport(BitmapCallback callback) {
        if (track == null || route == null) {
            return;
        }
        viewBinding.shareImageProgress.setVisibility(View.VISIBLE);
        TrackShareImageGenerator.Style selectedStyle = style;
        Aspect selectedAspect = aspect;
        TrackShareImageGenerator.Background selectedBackground = background;
        executor.execute(() -> {
            Bitmap bitmap;
            try {
                bitmap = TrackShareImageGenerator.render(this, track, route, selectedStyle, selectedBackground,
                        IMAGE_WIDTH_PX, heightFor(selectedAspect));
            } catch (RuntimeException e) {
                reportShareError();
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    bitmap.recycle();
                    return;
                }
                viewBinding.shareImageProgress.setVisibility(View.GONE);
                callback.onBitmap(bitmap);
            });
        });
    }

    private void shareImage() {
        renderForExport(bitmap -> {
            File file = ShareImageStore.save(this, bitmap, "share-track-" + trackId.id());
            bitmap.recycle();
            if (file == null) {
                reportShareError();
                return;
            }
            Uri uri = ShareImageStore.uri(this, file);
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_image_subject))
                    .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_image_body_track, track.name()));
            startActivity(Intent.createChooser(intent, null));
        });
    }

    /**
     * Saves the current card to the gallery (Pictures/OpenTracks on Android 10+,
     * app pictures dir with a media scan below that). Needs no extra permission.
     */
    private void downloadImage() {
        renderForExport(bitmap -> executor.execute(() -> {
            boolean saved = saveToGallery(bitmap);
            bitmap.recycle();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(this, saved ? R.string.share_image_saved : R.string.share_image_error,
                        Toast.LENGTH_LONG).show();
            });
        }));
    }

    private boolean saveToGallery(Bitmap bitmap) {
        String name = "opentracks-" + trackId.id() + "-" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenTracks");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                return out != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                return false;
            }
        }
        File file = ShareImageStore.save(this, bitmap, "share-track-" + trackId.id());
        if (file == null) {
            return false;
        }
        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
        return true;
    }

    private void reportShareError() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                viewBinding.shareImageProgress.setVisibility(View.GONE);
                Toast.makeText(this, R.string.share_image_error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
