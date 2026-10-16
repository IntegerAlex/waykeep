package com.waykeep.app.ui.trackmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackPointIterator;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;

/**
 * Renders a lightweight route thumbnail (no basemap) for a recorded track and caches it.
 * <p>
 * Reads track points on a background executor, draws the polyline onto a {@link Bitmap}, and
 * posts it back on the main thread. Results are cached in an {@link LruCache} keyed by
 * trackId + requested size, so scrolling the feed does not re-query SQLite.
 */
public class TrackThumbnailLoader {

    /** Target number of points used to draw the route (downsampled). */
    private static final int MAX_POINTS = 300;

    private final ContentProviderUtils contentProviderUtils;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;
    private final int strokeColor;
    private final int strokeWidthPx;
    private final int paddingPx;

    public interface Callback {
        void onThumbnail(@Nullable Bitmap bitmap);
    }

    public TrackThumbnailLoader(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.contentProviderUtils = new ContentProviderUtils(appContext);
        this.strokeColor = ContextCompat.getColor(appContext, R.color.opentracks);

        float density = appContext.getResources().getDisplayMetrics().density;
        this.strokeWidthPx = Math.max(2, Math.round(3 * density));
        this.paddingPx = Math.round(12 * density);

        int maxSize = Math.min(4 * 1024 * 1024, (int) (Runtime.getRuntime().maxMemory() / 16));
        this.cache = new LruCache<>(maxSize) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
    }

    public void load(@NonNull Track.Id trackId, int widthPx, int heightPx, @NonNull Callback callback) {
        if (widthPx <= 0 || heightPx <= 0) {
            callback.onThumbnail(null);
            return;
        }

        String key = trackId.id() + ":" + widthPx + "x" + heightPx;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            callback.onThumbnail(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = render(trackId, widthPx, heightPx);
            if (bitmap != null) {
                cache.put(key, bitmap);
            }
            MAIN_HANDLER.post(() -> callback.onThumbnail(bitmap));
        });
    }

    public void evictAll() {
        cache.evictAll();
    }

    private Bitmap render(Track.Id trackId, int widthPx, int heightPx) {
        List<double[]> points = new ArrayList<>();
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        try (TrackPointIterator iterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            int total = iterator.getCount();
            int samplingFrequency = 1 + total / MAX_POINTS;
            int index = 0;
            while (iterator.hasNext()) {
                TrackPoint trackPoint = iterator.next();
                if (index++ % samplingFrequency != 0) {
                    continue;
                }
                if (!trackPoint.position().hasLocation()) {
                    continue;
                }
                double lat = trackPoint.position().latitude();
                double lon = trackPoint.position().longitude();
                points.add(new double[]{lat, lon});
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
                minLon = Math.min(minLon, lon);
                maxLon = Math.max(maxLon, lon);
            }
        } catch (RuntimeException e) {
            return null;
        }

        if (points.size() < 2) {
            return null;
        }

        // Degrees -> roughly meters so the route is not distorted by longitude convergence.
        double midLatRad = Math.toRadians((minLat + maxLat) / 2.0);
        double lonScale = Math.cos(midLatRad);

        double routeWidth = (maxLon - minLon) * lonScale;
        double routeHeight = maxLat - minLat;

        double scaleX = (widthPx - 2.0 * paddingPx) / Math.max(routeWidth, 1e-9);
        double scaleY = (heightPx - 2.0 * paddingPx) / Math.max(routeHeight, 1e-9);
        double scale = Math.min(scaleX, scaleY);

        float offsetX = (float) ((widthPx - routeWidth * scale) / 2.0);
        float offsetY = (float) ((heightPx - routeHeight * scale) / 2.0);

        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
            double lat = points.get(i)[0];
            double lon = points.get(i)[1];
            float x = (float) (offsetX + (lon - minLon) * lonScale * scale);
            float y = (float) (offsetY + (maxLat - lat) * scale);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidthPx);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(strokeColor);
        canvas.drawPath(path, paint);

        // Start marker
        double startLat = points.get(0)[0];
        double startLon = points.get(0)[1];
        float startX = (float) (offsetX + (startLon - minLon) * lonScale * scale);
        float startY = (float) (offsetY + (maxLat - startLat) * scale);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(strokeColor);
        canvas.drawCircle(startX, startY, strokeWidthPx, dot);

        return bitmap;
    }

    /**
     * Convenience size helper: the feed hero is ~168dp tall and as wide as the screen.
     */
    public static int heroHeightPx(@NonNull Context context) {
        return Math.round(168 * context.getResources().getDisplayMetrics().density);
    }
}
