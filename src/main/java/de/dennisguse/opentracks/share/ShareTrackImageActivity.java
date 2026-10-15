package de.dennisguse.opentracks.share;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.databinding.ActivityShareTrackImageBinding;
import de.dennisguse.opentracks.util.RouteSampler;

/**
 * Previews a Strava-style shareable stat card and sends it as a PNG image.
 * Rendering happens off the UI thread; the bitmap is re-rendered when the
 * user changes aspect ratio or style.
 */
public class ShareTrackImageActivity extends AppCompatActivity {

    public static final String EXTRA_TRACK_ID = "track_id";

    private static final int IMAGE_WIDTH_PX = 1080;
    private static final int HEIGHT_4_5_PX = 1350;
    private static final int HEIGHT_9_16_PX = 1920;

    private ActivityShareTrackImageBinding viewBinding;
    private ExecutorService executor;

    private Track.Id trackId;
    private Track track;
    private RouteSampler.Route route;
    private Bitmap previewBitmap;
    private boolean aspectStory;
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

        viewBinding.shareImageAspectFeed.setOnClickListener(v -> {
            aspectStory = false;
            updateToggleStates();
            renderPreview();
        });
        viewBinding.shareImageAspectStory.setOnClickListener(v -> {
            aspectStory = true;
            updateToggleStates();
            renderPreview();
        });
        viewBinding.shareImageStyleStats.setOnClickListener(v -> {
            style = TrackShareImageGenerator.Style.STATS_CARD;
            updateToggleStates();
            renderPreview();
        });
        viewBinding.shareImageStyleRoute.setOnClickListener(v -> {
            style = TrackShareImageGenerator.Style.ROUTE_ONLY;
            updateToggleStates();
            renderPreview();
        });
        viewBinding.shareImageBgDark.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.DARK));
        viewBinding.shareImageBgLight.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.LIGHT));
        viewBinding.shareImageBgTransparent.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.TRANSPARENT));
        viewBinding.shareImageBgMap.setOnClickListener(v -> setBackground(TrackShareImageGenerator.Background.MAP_TINT));
        viewBinding.shareImageShareButton.setOnClickListener(v -> shareImage());
        background = TrackShareImageGenerator.loadBackground(this);
        updateToggleStates();

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

    private void setBackground(TrackShareImageGenerator.Background selected) {
        background = selected;
        TrackShareImageGenerator.saveBackground(this, selected);
        updateToggleStates();
        renderPreview();
    }

    private void updateToggleStates() {
        viewBinding.shareImageAspectFeed.setSelected(!aspectStory);
        viewBinding.shareImageAspectStory.setSelected(aspectStory);
        viewBinding.shareImageStyleStats.setSelected(style == TrackShareImageGenerator.Style.STATS_CARD);
        viewBinding.shareImageStyleRoute.setSelected(style == TrackShareImageGenerator.Style.ROUTE_ONLY);
        viewBinding.shareImageBgDark.setSelected(background == TrackShareImageGenerator.Background.DARK);
        viewBinding.shareImageBgLight.setSelected(background == TrackShareImageGenerator.Background.LIGHT);
        viewBinding.shareImageBgTransparent.setSelected(background == TrackShareImageGenerator.Background.TRANSPARENT);
        viewBinding.shareImageBgMap.setSelected(background == TrackShareImageGenerator.Background.MAP_TINT);
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
        boolean selectedStory = aspectStory;
        TrackShareImageGenerator.Background selectedBackground = background;
        executor.execute(() -> {
            Bitmap rendered;
            try {
                rendered = TrackShareImageGenerator.render(this, track, route, selectedStyle, selectedBackground,
                        IMAGE_WIDTH_PX, selectedStory ? HEIGHT_9_16_PX : HEIGHT_4_5_PX);
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

    private void shareImage() {
        if (track == null || route == null) {
            return;
        }
        viewBinding.shareImageProgress.setVisibility(View.VISIBLE);
        TrackShareImageGenerator.Style selectedStyle = style;
        boolean selectedStory = aspectStory;
        TrackShareImageGenerator.Background selectedBackground = background;
        executor.execute(() -> {
            Bitmap bitmap;
            try {
                bitmap = TrackShareImageGenerator.render(this, track, route, selectedStyle, selectedBackground,
                        IMAGE_WIDTH_PX, selectedStory ? HEIGHT_9_16_PX : HEIGHT_4_5_PX);
            } catch (RuntimeException e) {
                reportShareError();
                return;
            }
            File file = ShareImageStore.save(this, bitmap, "share-track-" + trackId.id());
            bitmap.recycle();
            if (file == null) {
                reportShareError();
                return;
            }
            Uri uri = ShareImageStore.uri(this, file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                viewBinding.shareImageProgress.setVisibility(View.GONE);
                Intent intent = new Intent(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_image_subject))
                        .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_image_body_track, track.name()));
                startActivity(Intent.createChooser(intent, null));
            });
        });
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
