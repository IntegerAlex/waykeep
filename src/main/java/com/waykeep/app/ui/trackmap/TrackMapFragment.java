package com.waykeep.app.ui.trackmap;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.R;
import com.waykeep.app.chart.TrackDataHubInterface;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.MarkerIterator;
import com.waykeep.app.data.TrackDataHub;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.databinding.FragmentTrackMapBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.ui.markers.MarkerBottomSheetFragment;

/**
 * A fragment that shows the track route on an offline-capable OSM map (osmdroid).
 * <p>
 * Receives sampled track points through {@link TrackDataHub} (same pattern as ChartFragment):
 * points arrive on the hub's background thread, are batched, and applied to the map on the UI
 * thread. Online OSM tiles are fetched when network is available; previously downloaded tiles
 * are served from the disk cache when offline.
 * <p>
 * Markers are loaded off the UI thread (with sampled photo thumbnails) and open the marker
 * detail screen on tap. Location follow is user-controlled: any manual pan disables it, the
 * action button re-enables it.
 */
public class TrackMapFragment extends Fragment implements TrackDataHub.Listener {

    private static final String STATE_CENTER_LAT = "center_lat";
    private static final String STATE_CENTER_LON = "center_lon";
    private static final String STATE_ZOOM = "zoom";
    private static final String STATE_FOLLOW = "follow";
    private static final String STATE_ORIENTATION = "orientation";

    private static final int MARKER_PHOTO_SIZE_PX = 96;

    private static boolean osmdroidConfigured = false;

    public static TrackMapFragment newInstance() {
        return new TrackMapFragment();
    }

    private static final String KEY_PLANNED_ROUTE = "plannedRoute";

    public static TrackMapFragment newInstance(ArrayList<GeoPoint> plannedRoute) {
        TrackMapFragment fragment = new TrackMapFragment();
        if (plannedRoute != null && !plannedRoute.isEmpty()) {
            Bundle args = new Bundle();
            args.putParcelableArrayList(KEY_PLANNED_ROUTE, plannedRoute);
            fragment.setArguments(args);
        }
        return fragment;
    }

    private TrackDataHub trackDataHub;
    private ContentProviderUtils contentProviderUtils;
    private ExecutorService markerExecutor;

    private FragmentTrackMapBinding viewBinding;
    private Polyline routeLine;
    private Polyline routeCasing;
    private MyLocationNewOverlay locationOverlay;
    private final List<Marker> markerOverlays = new ArrayList<>();
    private Marker startPin;
    private Marker endPin;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final List<GeoPoint> pendingPoints = new ArrayList<>();
    private GeoPoint lastRoutePoint;
    private Track.Id markersTrackId;
    private boolean zoomedToRoute = false;
    private boolean followEnabled = true;
    private boolean suppressScrollCallback = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensureConfigured(requireContext().getApplicationContext());
        contentProviderUtils = new ContentProviderUtils(requireContext().getApplicationContext());
        markerExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        markerExecutor.shutdownNow();
        contentProviderUtils = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewBinding = FragmentTrackMapBinding.inflate(inflater, container, false);

        // Uber-like minimal style: Esri Light/Dark Gray Canvas. Flat fills plus road outlines,
        // no POI clutter. Served keyless (verified HTTP 200); attribution is legally required.
        viewBinding.trackMapView.setTileSource(esriTileSource(isNightMode()));
        viewBinding.trackMapView.setTilesScaledToDpi(true);
        viewBinding.trackMapView.setMultiTouchControls(true);
        // Explicit 48dp controls below replace the fading built-ins (discoverability + touch target size).
        viewBinding.trackMapView.setBuiltInZoomControls(false);
        // The MapView lives in a ViewPager2 page whose view is detached from the window on page
        // eviction. osmdroid would then detach all overlays itself (nulling their internals) while
        // TrackDataHub callbacks may still be in flight -> crash in Polyline.setPoints.
        // Disable that and let onDestroyView() below be the single owner of onDetach().
        viewBinding.trackMapView.setDestroyMode(false);

        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(viewBinding.trackMapView);
        scaleBarOverlay.setAlignBottom(true);
        scaleBarOverlay.setAlignRight(false);
        viewBinding.trackMapView.getOverlays().add(scaleBarOverlay);

        CopyrightOverlay copyrightOverlay = new CopyrightOverlay(requireContext());
        copyrightOverlay.setAlignBottom(false);
        copyrightOverlay.setAlignRight(false);
        viewBinding.trackMapView.getOverlays().add(copyrightOverlay);

        if (savedInstanceState != null) {
            viewBinding.trackMapView.getController().setZoom(savedInstanceState.getDouble(STATE_ZOOM, 15.0));
            viewBinding.trackMapView.getController().setCenter(new GeoPoint(
                    savedInstanceState.getDouble(STATE_CENTER_LAT, 20.0),
                    savedInstanceState.getDouble(STATE_CENTER_LON, 0.0)));
            followEnabled = savedInstanceState.getBoolean(STATE_FOLLOW, true);
            // The user was deliberately looking somewhere: don't yank the camera on reload.
            zoomedToRoute = true;
            viewBinding.trackMapView.setMapOrientation(savedInstanceState.getFloat(STATE_ORIENTATION, 0f));
        } else {
            centerOnLastKnownLocation();
        }

        routeLine = createRouteLine();

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), viewBinding.trackMapView);
        locationOverlay.setDrawAccuracyEnabled(true);
        viewBinding.trackMapView.getOverlays().add(locationOverlay);

        viewBinding.trackMapRecenterButton.setOnClickListener(v -> {
            followEnabled = true;
            updateRecenterVisibility();
            centerOnCurrentPosition();
        });
        viewBinding.trackMapZoomInButton.setOnClickListener(v -> viewBinding.trackMapView.getController().zoomIn());
        viewBinding.trackMapZoomOutButton.setOnClickListener(v -> viewBinding.trackMapView.getController().zoomOut());
        viewBinding.trackMapCompassButton.setOnClickListener(v -> {
            suppressScrollCallback = true;
            try {
                viewBinding.trackMapView.setMapOrientation(0f);
            } finally {
                suppressScrollCallback = false;
            }
            viewBinding.trackMapView.invalidate();
        });
        viewBinding.trackMapRecenterButton.setTooltipText(getString(R.string.track_map_recenter));
        viewBinding.trackMapZoomInButton.setTooltipText(getString(R.string.track_map_zoom_in));
        viewBinding.trackMapZoomOutButton.setTooltipText(getString(R.string.track_map_zoom_out));
        viewBinding.trackMapCompassButton.setTooltipText(getString(R.string.track_map_compass));
        updateRecenterVisibility();

        viewBinding.trackMapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                // Programmatic moves (follow, fit-route) also raise this; only a real user pan breaks follow.
                if (!suppressScrollCallback && followEnabled) {
                    followEnabled = false;
                    updateRecenterVisibility();
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                return false;
            }
        });

        return viewBinding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewBinding != null) {
            IGeoPoint center = viewBinding.trackMapView.getMapCenter();
            outState.putDouble(STATE_CENTER_LAT, center.getLatitude());
            outState.putDouble(STATE_CENTER_LON, center.getLongitude());
            outState.putDouble(STATE_ZOOM, viewBinding.trackMapView.getZoomLevelDouble());
            outState.putBoolean(STATE_FOLLOW, followEnabled);
            outState.putFloat(STATE_ORIENTATION, viewBinding.trackMapView.getMapOrientation());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        resumeTrackDataHub();
        // Required by osmdroid: tile loading only runs between onResume/onPause.
        if (viewBinding != null) {
            viewBinding.trackMapView.onResume();
            // Location dot only while the map is visible, so it never drains GPS in background.
            if (locationOverlay != null && hasLocationPermission()) {
                locationOverlay.enableMyLocation();
            }
            updateOfflineBanner();
            registerNetworkCallback();
        }
        if (markersTrackId != null) {
            loadMarkersAsync(markersTrackId);
        }
    }

    @Override
    public void onPause() {
        // Required by osmdroid: tile loading only runs between onResume/onPause.
        if (locationOverlay != null) {
            locationOverlay.disableMyLocation();
        }
        unregisterNetworkCallback();
        if (viewBinding != null) {
            viewBinding.trackMapView.onPause();
        }
        pauseTrackDataHub();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewBinding != null) {
            viewBinding.trackMapView.onDetach();
        }
        viewBinding = null;
        routeLine = null;
        routeCasing = null;
        locationOverlay = null;
        markerOverlays.clear();
        startPin = null;
        endPin = null;
    }

    @Override
    public void onTrackUpdated(@NonNull Track track) {
        // Not needed for the route (drawn from sampled track points), but markers are per-track.
        if (track == null || track.id() == null) {
            return;
        }
        if (!track.id().equals(markersTrackId)) {
            markersTrackId = track.id();
            loadMarkersAsync(markersTrackId);
        }
    }

    @Override
    public void clearTrackPoints() {
        if (isResumed()) {
            pendingPoints.clear();
            lastRoutePoint = null;
            runOnUiThread(() -> {
                // Never call setPoints() with an empty list: drop the overlay and build a fresh one.
                if (isResumed() && viewBinding != null && routeLine != null) {
                    viewBinding.trackMapView.getOverlays().remove(routeLine);
                    if (routeCasing != null) {
                        viewBinding.trackMapView.getOverlays().remove(routeCasing);
                        routeCasing = null;
                    }
                    if (startPin != null) {
                        viewBinding.trackMapView.getOverlays().remove(startPin);
                        startPin = null;
                    }
                    if (endPin != null) {
                        viewBinding.trackMapView.getOverlays().remove(endPin);
                        endPin = null;
                    }
                    routeLine = createRouteLine();
                    moveLocationOverlayToTop();
                    viewBinding.trackMapView.invalidate();
                    zoomedToRoute = false;
                }
            });
        }
    }

    @Override
    public void onSampledInTrackPoint(@NonNull TrackPoint trackPoint, @NonNull Statistics trackStatistics) {
        if (isResumed() && trackPoint.position().hasLocation()) {
            pendingPoints.add(new GeoPoint(trackPoint.position().latitude(), trackPoint.position().longitude()));
        }
    }

    @Override
    public void onNewTrackPointsDone() {
        if (!isResumed() || pendingPoints.isEmpty()) {
            return;
        }

        List<GeoPoint> batch = new ArrayList<>(pendingPoints);
        pendingPoints.clear();
        boolean followLatest = followEnabled && isSelectedTrackRecording();

        runOnUiThread(() -> {
            if (!isResumed() || viewBinding == null || routeLine == null) {
                return;
            }

            List<GeoPoint> allPoints = new ArrayList<>(routeLine.getPoints());
            allPoints.addAll(batch);
            routeLine.setPoints(allPoints);
            if (routeCasing != null) {
                routeCasing.setPoints(allPoints);
            }
            lastRoutePoint = allPoints.get(allPoints.size() - 1);
            updateEndpointPins(allPoints);

            suppressScrollCallback = true;
            try {
                if (!zoomedToRoute && allPoints.size() >= 2) {
                    int paddingPx = Math.round(48 * requireContext().getResources().getDisplayMetrics().density);
                    viewBinding.trackMapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), false, paddingPx);
                    zoomedToRoute = true;
                } else if (followLatest) {
                    viewBinding.trackMapView.getController().setCenter(lastRoutePoint);
                }
            } finally {
                suppressScrollCallback = false;
            }

            viewBinding.trackMapView.invalidate();
        });
    }

    /**
     * Minimal Uber-like raster style: Esri Light Gray Canvas (day) / Dark Gray Canvas (night).
     * Flat fills plus road outlines, no POI clutter. Keyless endpoints (verified); the Esri
     * attribution overlay is legally required and added in onCreateView.
     * Esri orders tiles as z/y/x, hence the URL override.
     */
    public static OnlineTileSourceBase esriTileSource(boolean night) {
        String style = night ? "World_Dark_Gray_Base" : "World_Light_Gray_Base";
        String[] baseUrls = new String[]{
                "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/" + style + "/MapServer/tile/"
        };
        return new XYTileSource("Esri." + style, 0, 19, 256, "", baseUrls,
                "Esri, HERE, Garmin, (c) OpenStreetMap contributors, and the GIS user community") {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "/"
                        + MapTileIndex.getX(pMapTileIndex);
            }
        };
    }

    private boolean isNightMode() {
        int nightMask = requireContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Creates a fresh, styled route overlay (a dark casing outline with the brand line on top)
     * and attaches both to the map. The casing keeps the cyan route from blending into rivers,
     * forests or the basemap's own road fills.
     * New instances are required after clearing: osmdroid nulls a Polyline's internals on
     * detach, so a detached instance must never be reused.
     */
    private Polyline createRouteLine() {
        float density = requireContext().getResources().getDisplayMetrics().density;
        // ~1px of black on each side of the 6dp line.
        Polyline casing = new Polyline(viewBinding.trackMapView);
        casing.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.map_polyline_stroke));
        casing.getOutlinePaint().setStrokeWidth(6 * density + 2);
        viewBinding.trackMapView.getOverlays().add(casing);
        routeCasing = casing;

        Polyline line = new Polyline(viewBinding.trackMapView);
        line.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.opentracks));
        line.getOutlinePaint().setStrokeWidth(6 * density);
        viewBinding.trackMapView.getOverlays().add(line);
        return line;
    }

    /**
     * Draws a suggested loop (if one was handed over) as a dashed guidance line and frames it.
     * The recorded route keeps its own solid line on top.
     */
    private void drawPlannedRoute() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        ArrayList<GeoPoint> planned = args.getParcelableArrayList(KEY_PLANNED_ROUTE);
        if (planned == null || planned.size() < 2) {
            return;
        }
        float density = requireContext().getResources().getDisplayMetrics().density;
        Polyline plannedLine = new Polyline(viewBinding.trackMapView);
        plannedLine.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.opentracks));
        plannedLine.getOutlinePaint().setStrokeWidth(4 * density);
        plannedLine.getOutlinePaint().setPathEffect(new android.graphics.DashPathEffect(new float[]{12 * density, 8 * density}, 0));
        plannedLine.setPoints(planned);
        viewBinding.trackMapView.getOverlays().add(plannedLine);
        moveLocationOverlayToTop();

        int paddingPx = Math.round(48 * density);
        viewBinding.trackMapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(planned), false, paddingPx);
        zoomedToRoute = true;
        viewBinding.trackMapView.invalidate();
    }

    private void moveLocationOverlayToTop() {        if (locationOverlay != null) {
            viewBinding.trackMapView.getOverlays().remove(locationOverlay);
            viewBinding.trackMapView.getOverlays().add(locationOverlay);
        }
    }

    private void centerOnCurrentPosition() {
        GeoPoint target = lastRoutePoint != null ? lastRoutePoint
                : (locationOverlay != null ? locationOverlay.getMyLocation() : null);
        if (target != null && viewBinding != null) {
            suppressScrollCallback = true;
            try {
                viewBinding.trackMapView.getController().setCenter(target);
            } finally {
                suppressScrollCallback = false;
            }
            viewBinding.trackMapView.invalidate();
        }
    }

    /**
     * The recenter control only exists after the user pans away (AllTrails pattern):
     * while following, there is nothing to recenter to, so it stays hidden.
     */
    private void updateRecenterVisibility() {
        if (viewBinding != null) {
            viewBinding.trackMapRecenterButton.setVisibility(followEnabled ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Shows whether tiles come from network or cache, and honors the offline-mode setting.
     */
    private void updateOfflineBanner() {
        if (viewBinding == null || !isAdded()) {
            return;
        }
        boolean offlineOnly = PreferencesUtils.isMapOfflineOnly();
        viewBinding.trackMapView.setUseDataConnection(!offlineOnly);
        if (offlineOnly) {
            viewBinding.trackMapOfflineBanner.setText(R.string.track_map_offline_mode);
            viewBinding.trackMapOfflineBanner.setVisibility(View.VISIBLE);
        } else if (!isConnected()) {
            viewBinding.trackMapOfflineBanner.setText(R.string.track_map_offline_cached);
            viewBinding.trackMapOfflineBanner.setVisibility(View.VISIBLE);
        } else {
            viewBinding.trackMapOfflineBanner.setVisibility(View.GONE);
        }
    }

    private boolean isConnected() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return true;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return;
            }
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    refreshOfflineBanner();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    refreshOfflineBanner();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    refreshOfflineBanner();
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            networkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) {
            return;
        }
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            // Already unregistered or service gone; nothing to do.
        }
        networkCallback = null;
    }

    private void refreshOfflineBanner() {
        Activity fragmentActivity = getActivity();
        if (fragmentActivity != null) {
            fragmentActivity.runOnUiThread(this::updateOfflineBanner);
        }
    }

    /**
     * Start (green) and end (red) dots for the route, kept below the location dot.
     */
    private void updateEndpointPins(@NonNull List<GeoPoint> allPoints) {
        if (viewBinding == null || allPoints.size() < 2) {
            return;
        }
        if (startPin == null) {
            startPin = makeEndpointPin(0xFF4CAF50);
            endPin = makeEndpointPin(0xFFF44336);
            moveLocationOverlayToTop();
        }
        startPin.setPosition(allPoints.get(0));
        endPin.setPosition(allPoints.get(allPoints.size() - 1));
    }

    private Marker makeEndpointPin(int color) {
        Marker pin = new Marker(viewBinding.trackMapView);
        pin.setIcon(makeDotDrawable(color));
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        pin.setOnMarkerClickListener((tapped, mapView) -> true);
        if (locationOverlay != null) {
            viewBinding.trackMapView.getOverlays().remove(locationOverlay);
        }
        viewBinding.trackMapView.getOverlays().add(pin);
        if (locationOverlay != null) {
            viewBinding.trackMapView.getOverlays().add(locationOverlay);
        }
        return pin;
    }

    private Drawable makeDotDrawable(int color) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        int size = Math.round(16 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float center = size / 2f;
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setStyle(Paint.Style.FILL);
        outer.setColor(0xFFFFFFFF);
        canvas.drawCircle(center, center, center, outer);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.FILL);
        inner.setColor(color);
        canvas.drawCircle(center, center, center - 2 * density, inner);
        return new BitmapDrawable(requireContext().getResources(), bitmap);
    }

    /**
     * Centers the map on the device's last known location (street level) or, if unavailable,
     * shows a world view. The route auto-zooms once track points arrive.
     */
    private void centerOnLastKnownLocation() {
        try {
            LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location location = null;
            if (locationManager != null) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }
            if (location != null) {
                viewBinding.trackMapView.getController().setZoom(15.0);
                viewBinding.trackMapView.getController().setCenter(new GeoPoint(location.getLatitude(), location.getLongitude()));
            } else {
                viewBinding.trackMapView.getController().setZoom(3.0);
                viewBinding.trackMapView.getController().setCenter(new GeoPoint(20.0, 0.0));
            }
        } catch (SecurityException e) {
            viewBinding.trackMapView.getController().setZoom(3.0);
        }
    }

    private void loadMarkersAsync(Track.Id trackId) {
        markerExecutor.execute(() -> {
            List<MarkerData> loaded = new ArrayList<>();
            try (MarkerIterator iterator = contentProviderUtils.searchMarkers(trackId, null)) {
                while (iterator.hasNext()) {
                    com.waykeep.app.data.models.Marker marker = iterator.next();
                    if (!marker.position().hasLocation()) {
                        continue;
                    }
                    loaded.add(new MarkerData(
                            marker.id(),
                            new GeoPoint(marker.position().latitude(), marker.position().longitude()),
                            decodePhotoIcon(marker.photoUrl())));
                }
            } catch (RuntimeException e) {
                return;
            }

            runOnUiThread(() -> {
                if (!isAdded() || viewBinding == null) {
                    return;
                }
                for (Marker overlay : markerOverlays) {
                    viewBinding.trackMapView.getOverlays().remove(overlay);
                }
                markerOverlays.clear();
                for (MarkerData data : loaded) {
                    Marker pin = new Marker(viewBinding.trackMapView);
                    pin.setPosition(data.position);
                    if (data.icon != null) {
                        pin.setIcon(data.icon);
                        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    } else {
                        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    }
                    pin.setRelatedObject(data.markerId);
                    pin.setOnMarkerClickListener((tapped, mapView) -> {
                        MarkerBottomSheetFragment.newInstance((com.waykeep.app.data.models.Marker.Id) tapped.getRelatedObject())
                                .show(getParentFragmentManager(), "marker_sheet");
                        return true;
                    });
                    viewBinding.trackMapView.getOverlays().add(pin);
                    markerOverlays.add(pin);
                }
                moveLocationOverlayToTop();
                viewBinding.trackMapView.invalidate();
            });
        });
    }

    /**
     * Decodes a marker photo to a small map icon; null when there is no photo or it fails.
     * Runs on the marker executor, never on the UI thread.
     */
    private Drawable decodePhotoIcon(Uri photoUri) {
        if (photoUri == null) {
            return null;
        }
        try (InputStream input = requireContext().getApplicationContext().getContentResolver().openInputStream(photoUri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / MARKER_PHOTO_SIZE_PX);
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sampleSize;
            try (InputStream input2 = requireContext().getApplicationContext().getContentResolver().openInputStream(photoUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input2, null, decode);
                if (bitmap == null) {
                    return null;
                }
                return new BitmapDrawable(requireContext().getResources(), bitmap);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Resumes the trackDataHub.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    private synchronized void resumeTrackDataHub() {
        trackDataHub = ((TrackDataHubInterface) getActivity()).getTrackDataHub();
        trackDataHub.registerTrackDataListener(this);
    }

    /**
     * Pauses the trackDataHub.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    private synchronized void pauseTrackDataHub() {
        trackDataHub.unregisterTrackDataListener(this);
        trackDataHub = null;
    }

    /**
     * Returns true if the selected track is recording.
     * Needs to be synchronized because trackDataHub can be accessed by multiple threads.
     */
    private synchronized boolean isSelectedTrackRecording() {
        return trackDataHub != null && trackDataHub.isSelectedTrackRecording();
    }

    /**
     * Runs a runnable on the UI thread if possible.
     *
     * @param runnable the runnable
     */
    private void runOnUiThread(Runnable runnable) {
        Activity fragmentActivity = getActivity();
        if (fragmentActivity != null) {
            fragmentActivity.runOnUiThread(runnable);
        }
    }

    public static synchronized void ensureConfigured(Context appContext) {
        if (osmdroidConfigured) {
            return;
        }
        Configuration.getInstance().load(appContext, PreferenceManager.getDefaultSharedPreferences(appContext));
        // Required by the OSM tile usage policy: identify the app on tile requests.
        Configuration.getInstance().setUserAgentValue(appContext.getPackageName());
        // Keep tiles in the internal cache dir: it is never auto-backed-up, so up to 512 MB
        // of viewed-map tiles (i.e. where the user has been) stays off Google Drive.
        java.io.File osmdroidDir = new java.io.File(appContext.getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(osmdroidDir);
        Configuration.getInstance().setOsmdroidTileCache(new java.io.File(osmdroidDir, "tiles"));
        // Offline areas: keep half a gigabyte of tiles and honor them a week past server expiry.
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(512L * 1024L * 1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(256L * 1024L * 1024L);
        Configuration.getInstance().setExpirationExtendedDuration(7L * 24 * 60 * 60 * 1000);
        osmdroidConfigured = true;
    }

    private record MarkerData(
            com.waykeep.app.data.models.Marker.Id markerId,
            GeoPoint position,
            Drawable icon
    ) {
    }
}
