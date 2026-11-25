package com.waykeep.app.share;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.waykeep.app.util.RouteSampler;

import org.junit.Test;

import java.util.List;

/**
 * The share card renders the basemap full-bleed but fits the route into the area above the stats.
 * Both have to hold at once, and the whole canvas has to stay inside the tile budget or the first
 * preview spends seconds downloading tiles.
 */
public class MapTileFetcherFitTest {

    private static final int CANVAS_W = 1080;
    private static final int CANVAS_H = 1350;
    private static final int REGION_TOP = 300;
    private static final int REGION_HEIGHT = 400;
    private static final int TILE_SIZE = 256;

    private static List<RouteSampler.LatLon> route() {
        // A 2-3 km loop, roughly the size of a morning run.
        return List.of(
                new RouteSampler.LatLon(48.8584, 2.2945),
                new RouteSampler.LatLon(48.8550, 2.3010),
                new RouteSampler.LatLon(48.8510, 2.2960),
                new RouteSampler.LatLon(48.8545, 2.2890),
                new RouteSampler.LatLon(48.8584, 2.2945));
    }

    private static MapTileFetcher.Fit fit() {
        return MapTileFetcher.fit(route(), CANVAS_W, CANVAS_H, 0, REGION_TOP, CANVAS_W, REGION_HEIGHT);
    }

    @Test
    public void fullBleedFitStaysInsideTheTileBudget() {
        MapTileFetcher.Fit fit = fit();
        assertNotNull(fit);
        int tiles = canvasTiles(fit);
        assertTrue("canvas needs " + tiles + " tiles, budget is " + 25, tiles <= 25);
    }

    @Test
    public void routeStaysInsideItsRegion() {
        MapTileFetcher.Fit fit = fit();
        assertNotNull(fit);
        for (RouteSampler.LatLon point : route()) {
            double[] mercator = MapTileFetcher.mercatorPx(point.longitude(), point.latitude(), fit.zoom);
            double x = mercator[0] * fit.scale + fit.offX;
            double y = mercator[1] * fit.scale + fit.offY;
            assertTrue("x " + x + " outside canvas", x >= 0 && x <= CANVAS_W);
            assertTrue("y " + y + " outside region", y >= REGION_TOP - 1 && y <= REGION_TOP + REGION_HEIGHT + 1);
        }
    }

    @Test
    public void smallRouteStaysAtBasemapCoverageZoom() {
        // Esri World Street Map returns "Map data not yet available" placeholder tiles at
        // zoom 18/19 in some cities (seen in Pune).
        List<RouteSampler.LatLon> smallRoute = List.of(
                new RouteSampler.LatLon(18.5504, 73.9029),
                new RouteSampler.LatLon(18.5502, 73.9036),
                new RouteSampler.LatLon(18.5498, 73.9031),
                new RouteSampler.LatLon(18.5501, 73.9025),
                new RouteSampler.LatLon(18.5504, 73.9029));
        MapTileFetcher.Fit fit = MapTileFetcher.fit(smallRoute, CANVAS_W, CANVAS_H,
                0, REGION_TOP, CANVAS_W, REGION_HEIGHT);
        assertNotNull(fit);
        assertTrue("zoom " + fit.zoom + " uses placeholder basemap tiles", fit.zoom <= 17);
    }

    /** Same math the fetcher uses to decide which tiles to download. */
    private static int canvasTiles(MapTileFetcher.Fit fit) {
        int maxTile = 1 << fit.zoom;
        int left = index((0 - fit.offX) / fit.scale, maxTile);
        int right = index((CANVAS_W - fit.offX) / fit.scale, maxTile);
        int top = index((0 - fit.offY) / fit.scale, maxTile);
        int bottom = index((CANVAS_H - fit.offY) / fit.scale, maxTile);
        return (right - left + 1) * (bottom - top + 1);
    }

    private static int index(double mercatorPx, int maxTile) {
        return Math.max(0, Math.min(maxTile - 1, (int) Math.floor(mercatorPx / TILE_SIZE)));
    }
}
