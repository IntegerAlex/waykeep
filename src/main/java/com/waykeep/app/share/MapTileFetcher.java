package com.waykeep.app.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.waykeep.app.util.RouteSampler;

/**
 * Fetches a small, tile-capped CyclOSM basemap under a route and returns a fit-to-bounds bitmap
 * plus the exact transform used, so the caller draws the route in the same projection.
 * Cache-first (30 day TTL) so repeat shares are instant and already-viewed areas work offline.
 * Never throws; returns null on any failure so the caller falls back to a plain background.
 */
public final class MapTileFetcher {

    private static final int TILE_SIZE = 256;
    private static final int CAP_TILES = 25;
    private static final int MAX_BASEMAP_ZOOM = 19;
    private static final String ATTRIBUTION = "© OpenStreetMap contributors, CyclOSM, OSM France";
    private static final long TILE_TTL_MS = 30L * 24 * 60 * 60 * 1000L;
    private static final int TILE_TIMEOUT_MS = 6000;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private MapTileFetcher() {
    }

    @NonNull
    public static String attribution() {
        return ATTRIBUTION;
    }

    @NonNull
    static String tileUrl(int z, int x, int y) {
        String[] hosts = {"a", "b", "c"};
        String host = hosts[Math.floorMod(x + y, hosts.length)];
        return "https://" + host + ".tile-cyclosm.openstreetmap.fr/cyclosm/"
                + z + "/" + x + "/" + y + ".png";
    }

    /**
     * Mercator transform mapping route pixels to canvas pixels: canvas = mercator * scale + offset.
     */
    public static class Fit {
        public final int zoom;
        public final double scale;
        public final double offX;
        public final double offY;

        Fit(int zoom, double scale, double offX, double offY) {
            this.zoom = zoom;
            this.scale = scale;
            this.offX = offX;
            this.offY = offY;
        }
    }

    public static class MapResult {
        public final Bitmap bitmap;
        public final Fit fit;

        MapResult(Bitmap bitmap, Fit fit) {
            this.bitmap = bitmap;
            this.fit = fit;
        }
    }

    private static class Bounds {
        final double minPx, minPy, maxPx, maxPy;
        final int tileMinX, tileMinY, tileMaxX, tileMaxY;

        Bounds(double minPx, double minPy, double maxPx, double maxPy,
               int tileMinX, int tileMinY, int tileMaxX, int tileMaxY) {
            this.minPx = minPx;
            this.minPy = minPy;
            this.maxPx = maxPx;
            this.maxPy = maxPy;
            this.tileMinX = tileMinX;
            this.tileMinY = tileMinY;
            this.tileMaxX = tileMaxX;
            this.tileMaxY = tileMaxY;
        }
    }

    /**
     * Computes the zoom + transform fitting the route into the whole canvas.
     * Pure math, no I/O: safe to call before deciding to fetch.
     */
    @Nullable
    public static Fit fit(@NonNull List<RouteSampler.LatLon> points, int canvasWidthPx, int canvasHeightPx) {
        return fit(points, canvasWidthPx, canvasHeightPx, 0, 0, canvasWidthPx, canvasHeightPx);
    }

    /**
     * Computes the zoom + transform fitting the route into {@code region} of a canvas. Tiles are
     * still fetched for the whole canvas, so a full-bleed basemap can extend past the region.
     * The zoom also has to keep the canvas-wide tile count inside {@link #CAP_TILES}, otherwise
     * the first preview would spend seconds fetching tiles nobody asked for.
     */
    @Nullable
    public static Fit fit(@NonNull List<RouteSampler.LatLon> points, int canvasWidthPx, int canvasHeightPx,
                          int regionLeft, int regionTop, int regionWidth, int regionHeight) {
        if (points.size() < 2 || regionWidth <= 0 || regionHeight <= 0) {
            return null;
        }
        for (int z = MAX_BASEMAP_ZOOM; z >= 1; z--) {
            Bounds b = boundsFor(points, z);
            if (b == null) {
                continue;
            }
            int count = (b.tileMaxX - b.tileMinX + 1) * (b.tileMaxY - b.tileMinY + 1);
            if (count > CAP_TILES) {
                continue;
            }
            double padFrac = 0.12;
            double bboxW = Math.max(b.maxPx - b.minPx, 1);
            double bboxH = Math.max(b.maxPy - b.minPy, 1);
            double scale = Math.min(regionWidth / (bboxW * (1 + 2 * padFrac)),
                    regionHeight / (bboxH * (1 + 2 * padFrac)));
            scale = Math.min(scale, 8.0);
            double offX = regionLeft + (regionWidth - bboxW * scale) / 2.0 - b.minPx * scale;
            double offY = regionTop + (regionHeight - bboxH * scale) / 2.0 - b.minPy * scale;
            Fit fit = new Fit(z, scale, offX, offY);
            if (tileCountForCanvas(fit, canvasWidthPx, canvasHeightPx) <= CAP_TILES) {
                return fit;
            }
        }
        return null;
    }

    private static int tileCountForCanvas(@NonNull Fit fit, int canvasWidthPx, int canvasHeightPx) {
        int maxTile = 1 << fit.zoom;
        int areas = (tileIndex((canvasWidthPx - fit.offX) / fit.scale, maxTile)
                - tileIndex((0 - fit.offX) / fit.scale, maxTile) + 1)
                * (tileIndex((canvasHeightPx - fit.offY) / fit.scale, maxTile)
                - tileIndex((0 - fit.offY) / fit.scale, maxTile) + 1);
        return Math.max(areas, 1);
    }

    @Nullable
    public static MapResult render(@NonNull Context context,
                                  @NonNull List<RouteSampler.LatLon> points,
                                  int canvasWidthPx, int canvasHeightPx) {
        return render(context, points, canvasWidthPx, canvasHeightPx, 0, 0, canvasWidthPx, canvasHeightPx);
    }

    /**
     * Fetches tiles for the fitted zoom and returns them composited into a canvas-sized bitmap.
     * Tiles cover the whole canvas even when the route is fitted into a smaller region.
     */
    @Nullable
    public static MapResult render(@NonNull Context context,
                                  @NonNull List<RouteSampler.LatLon> points,
                                  int canvasWidthPx, int canvasHeightPx,
                                  int regionLeft, int regionTop, int regionWidth, int regionHeight) {
        Fit fit = fit(points, canvasWidthPx, canvasHeightPx, regionLeft, regionTop, regionWidth, regionHeight);
        if (fit == null) {
            return null;
        }
        int maxTile = 1 << fit.zoom;

        int tileMinX = tileIndex((0 - fit.offX) / fit.scale, maxTile);
        int tileMinY = tileIndex((0 - fit.offY) / fit.scale, maxTile);
        int tileMaxX = tileIndex((canvasWidthPx - fit.offX) / fit.scale, maxTile);
        int tileMaxY = tileIndex((canvasHeightPx - fit.offY) / fit.scale, maxTile);

        int tileCols = tileMaxX - tileMinX + 1;
        int tileRows = tileMaxY - tileMinY + 1;
        List<Future<Bitmap>> futures = new ArrayList<>();
        for (int ty = tileMinY; ty <= tileMaxY; ty++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                final int fz = fit.zoom;
                final int fx = tx;
                final int fy = ty;
                futures.add(POOL.submit(() -> loadTile(context, fz, fx, fy)));
            }
        }

        Bitmap grid = Bitmap.createBitmap(tileCols * TILE_SIZE, tileRows * TILE_SIZE, Bitmap.Config.ARGB_8888);
        Canvas gridCanvas = new Canvas(grid);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        int idx = 0;
        for (int ty = tileMinY; ty <= tileMaxY; ty++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                Bitmap tile = null;
                try {
                    tile = futures.get(idx).get();
                } catch (Exception ignored) {
                    tile = null;
                }
                idx++;
                int dx = (tx - tileMinX) * TILE_SIZE;
                int dy = (ty - tileMinY) * TILE_SIZE;
                if (tile != null) {
                    gridCanvas.drawBitmap(tile, dx, dy, paint);
                } else {
                    android.graphics.Paint fill = new android.graphics.Paint();
                    fill.setColor(0xFF2A2A2A);
                    gridCanvas.drawRect(dx, dy, dx + TILE_SIZE, dy + TILE_SIZE, fill);
                }
            }
        }

        Bitmap output = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Rect src = new Rect(0, 0, grid.getWidth(), grid.getHeight());
        RectF dst = new RectF(
                (float) (tileMinX * TILE_SIZE * fit.scale + fit.offX),
                (float) (tileMinY * TILE_SIZE * fit.scale + fit.offY),
                (float) ((tileMaxX + 1) * TILE_SIZE * fit.scale + fit.offX),
                (float) ((tileMaxY + 1) * TILE_SIZE * fit.scale + fit.offY));
        canvas.drawBitmap(grid, src, dst, paint);
        grid.recycle();

        return new MapResult(output, fit);
    }

    private static int tileIndex(double mercatorPx, int maxTile) {
        return Math.max(0, Math.min(maxTile - 1, (int) Math.floor(mercatorPx / TILE_SIZE)));
    }

    /**
     * Projects lon/lat to mercator pixels at a zoom (matches the Fit transform).
     */
    public static double[] mercatorPx(double lon, double lat, int zoom) {
        double x = (lon + 180.0) / 360.0;
        double latRad = Math.toRadians(lat);
        double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0;
        double tiles = 1 << zoom;
        return new double[]{x * tiles * TILE_SIZE, y * tiles * TILE_SIZE};
    }

    @Nullable
    private static Bounds boundsFor(List<RouteSampler.LatLon> points, int z) {
        double minPx = Double.MAX_VALUE, minPy = Double.MAX_VALUE;
        double maxPx = -Double.MAX_VALUE, maxPy = -Double.MAX_VALUE;
        int maxTile = 1 << z;
        for (RouteSampler.LatLon p : points) {
            double[] px = mercatorPx(p.longitude(), p.latitude(), z);
            minPx = Math.min(minPx, px[0]);
            minPy = Math.min(minPy, px[1]);
            maxPx = Math.max(maxPx, px[0]);
            maxPy = Math.max(maxPy, px[1]);
        }
        if (minPx == Double.MAX_VALUE) {
            return null;
        }
        int tMinX = Math.max(0, (int) Math.floor(minPx / TILE_SIZE));
        int tMinY = Math.max(0, (int) Math.floor(minPy / TILE_SIZE));
        int tMaxX = Math.min(maxTile - 1, (int) Math.floor(maxPx / TILE_SIZE));
        int tMaxY = Math.min(maxTile - 1, (int) Math.floor(maxPy / TILE_SIZE));
        if (tMaxX < tMinX || tMaxY < tMinY) {
            return null;
        }
        return new Bounds(minPx, minPy, maxPx, maxPy, tMinX, tMinY, tMaxX, tMaxY);
    }

    @Nullable
    private static Bitmap loadTile(Context context, int z, int x, int y) {
        File cache = cacheFile(context, z, x, y);
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < TILE_TTL_MS) {
            try (FileInputStream in = new FileInputStream(cache)) {
                Bitmap cached = BitmapFactory.decodeStream(in);
                if (cached != null) {
                    return cached;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            URL url = new URL(tileUrl(z, x, y));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TILE_TIMEOUT_MS);
            conn.setReadTimeout(TILE_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", context.getPackageName());
            conn.connect();
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                return null;
            }
            Bitmap downloaded;
            try (java.io.InputStream in = conn.getInputStream()) {
                downloaded = BitmapFactory.decodeStream(in);
            }
            conn.disconnect();
            if (downloaded != null) {
                writeCache(cache, downloaded);
                return downloaded;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static File cacheFile(Context context, int z, int x, int y) {
        File xdir = new File(new File(new File(new File(context.getCacheDir(), "share-map-tiles"),
                "cyclosm"), String.valueOf(z)), String.valueOf(x));
        if (!xdir.exists()) {
            xdir.mkdirs();
        }
        return new File(xdir, y + ".png");
    }

    private static void writeCache(File cache, Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(cache)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        }
    }
}
