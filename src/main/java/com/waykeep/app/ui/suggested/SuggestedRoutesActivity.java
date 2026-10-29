package com.waykeep.app.ui.suggested;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.TrackRecordingActivity;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.SuggestedRoutesBinding;
import com.waykeep.app.services.MissingPermissionException;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.ui.trackmap.TrackMapFragment;
import com.waykeep.app.util.IntentUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates running loops around a start pin (BRouter round trips) and previews them on a map.
 * The pin is approximate on purpose: place it where you will go and run, not where you stand.
 */
public class SuggestedRoutesActivity extends AbstractActivity {

    private static final double FALLBACK_LAT = 28.6139;
    private static final double FALLBACK_LON = 77.2090;
    private static final int MAX_RADIUS_ADJUSTMENTS = 2;

    private SuggestedRoutesBinding viewBinding;
    private final BrouterClient brouterClient = new BrouterClient();

    private GeoPoint startPoint;
    private Marker startPin;
    private final List<RouteSuggestion> suggestions = new ArrayList<>();
    private final List<Polyline> suggestionLines = new ArrayList<>();
    private RouteSuggestion selected;
    private int pendingResponses;
    private boolean generating;
    private double desiredLengthM;
    private String desiredLabel;
    private double roundtripRadiusM;
    private int radiusAdjustments;

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = SuggestedRoutesBinding.inflate(getLayoutInflater());

        TrackMapFragment.ensureConfigured(getApplicationContext());
        viewBinding.suggestedMap.setTileSource(TrackMapFragment.currentTileSource(isNightMode()));
        viewBinding.suggestedMap.setTilesScaledToDpi(true);
        viewBinding.suggestedMap.setMultiTouchControls(true);
        viewBinding.suggestedMap.setBuiltInZoomControls(false);

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                setStartPoint(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        });
        viewBinding.suggestedMap.getOverlays().add(eventsOverlay);

        centerOnLastKnownLocation();

        viewBinding.suggestedMyLocation.setOnClickListener(v -> centerOnLastKnownLocation());
        viewBinding.suggestedGenerate.setOnClickListener(v -> generateRoutes());
        viewBinding.suggestedRun.setOnClickListener(v -> runSelectedRoute());
        viewBinding.suggestedResultsGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                selectSuggestion((Integer) group.findViewById(checkedId).getTag());
            }
        });

        return viewBinding.getRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewBinding.suggestedMap.onResume();
    }

    @Override
    protected void onPause() {
        viewBinding.suggestedMap.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        brouterClient.shutdown();
        viewBinding.suggestedMap.onDetach();
        viewBinding = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private boolean isNightMode() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setStartPoint(GeoPoint point) {
        startPoint = point;
        if (startPin != null) {
            viewBinding.suggestedMap.getOverlays().remove(startPin);
        }
        startPin = new Marker(viewBinding.suggestedMap);
        startPin.setPosition(point);
        startPin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        viewBinding.suggestedMap.getOverlays().add(startPin);
        viewBinding.suggestedMap.invalidate();
    }

    private void centerOnLastKnownLocation() {
        GeoPoint center = null;
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = null;
            if (locationManager != null) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }
            if (location != null) {
                center = new GeoPoint(location.getLatitude(), location.getLongitude());
            }
        } catch (SecurityException e) {
            center = null;
        }
        if (center == null) {
            center = new GeoPoint(FALLBACK_LAT, FALLBACK_LON);
            viewBinding.suggestedStatus.setText(R.string.suggested_routes_no_location);
        }
        viewBinding.suggestedMap.getController().setZoom(14.0);
        viewBinding.suggestedMap.getController().setCenter(center);
        if (startPoint == null) {
            setStartPoint(center);
        }
    }

    private double checkedDistanceM() {
        int checkedId = viewBinding.suggestedDistanceGroup.getCheckedButtonId();
        if (checkedId == R.id.suggested_dist_3) {
            return 3000;
        }
        if (checkedId == R.id.suggested_dist_10) {
            return 10000;
        }
        return 5000;
    }

    private String checkedDistanceLabel() {
        int checkedId = viewBinding.suggestedDistanceGroup.getCheckedButtonId();
        if (checkedId == R.id.suggested_dist_3) {
            return getString(R.string.suggested_routes_3km);
        }
        if (checkedId == R.id.suggested_dist_10) {
            return getString(R.string.suggested_routes_10km);
        }
        return getString(R.string.suggested_routes_5km);
    }

    private void generateRoutes() {
        if (generating || startPoint == null) {
            return;
        }
        generating = true;
        suggestions.clear();
        selected = null;
        clearSuggestionLines();
        viewBinding.suggestedResultsGroup.removeAllViews();
        viewBinding.suggestedResultsGroup.setVisibility(View.GONE);
        viewBinding.suggestedRun.setVisibility(View.GONE);
        viewBinding.suggestedStatus.setText(R.string.suggested_routes_searching);
        viewBinding.suggestedGenerate.setEnabled(false);

        pendingResponses = 3;
        desiredLengthM = checkedDistanceM();
        desiredLabel = checkedDistanceLabel();
        roundtripRadiusM = BrouterClient.initialRoundtripRadius(desiredLengthM);
        radiusAdjustments = 0;
        requestSuggestions();
    }

    private void requestSuggestions() {
        pendingResponses = 3;
        brouterClient.suggestWithRoundtripRadius(startPoint.getLongitude(), startPoint.getLatitude(),
                roundtripRadiusM, PreferencesUtils.getSuggestedRoutesProfile(), new BrouterClient.Callback() {
            @Override
            public void onResult(RouteSuggestion suggestion) {
                runOnUiThread(() -> {
                    if (viewBinding != null && !isFinishing()) {
                        addSuggestion(suggestion);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    if (viewBinding != null && !isFinishing()) {
                        onSuggestFailed();
                    }
                });
            }
        });
    }

    private void addSuggestion(RouteSuggestion suggestion) {
        suggestions.add(suggestion);
        onSuggestSettled();
    }

    private void onSuggestFailed() {
        onSuggestSettled();
    }

    private void onSuggestSettled() {
        pendingResponses--;
        if (pendingResponses > 0) {
            return;
        }
        if (suggestions.isEmpty()) {
            generating = false;
            viewBinding.suggestedGenerate.setEnabled(true);
            viewBinding.suggestedStatus.setText(R.string.suggested_routes_error);
            return;
        }
        // Responses arrive in any order: select the measured route closest to the request,
        // not the first one back.
        List<Double> distances = new ArrayList<>();
        for (RouteSuggestion suggestion : suggestions) {
            distances.add(suggestion.distanceM());
        }
        int best = BrouterClient.bestMatchIndex(distances, desiredLengthM);
        RouteSuggestion closest = suggestions.get(best);
        if (!BrouterClient.isWithinDistanceTolerance(closest.distanceM(), desiredLengthM)) {
            if (radiusAdjustments < MAX_RADIUS_ADJUSTMENTS) {
                radiusAdjustments++;
                roundtripRadiusM = BrouterClient.adjustRoundtripRadius(
                        roundtripRadiusM, desiredLengthM, closest.distanceM());
                suggestions.clear();
                requestSuggestions();
                return;
            }
            generating = false;
            viewBinding.suggestedGenerate.setEnabled(true);
            viewBinding.suggestedResultsGroup.removeAllViews();
            viewBinding.suggestedResultsGroup.setVisibility(View.GONE);
            viewBinding.suggestedRun.setVisibility(View.GONE);
            selected = null;
            clearSuggestionLines();
            viewBinding.suggestedStatus.setText(getString(R.string.suggested_routes_no_match,
                    desiredLabel, BrouterClient.formatDistance(closest.distanceM())));
            suggestions.clear();
            return;
        }

        generating = false;
        viewBinding.suggestedGenerate.setEnabled(true);
        int bestAccepted = 0;
        double bestOffset = Double.MAX_VALUE;
        List<RouteSuggestion> accepted = new ArrayList<>();
        for (RouteSuggestion suggestion : suggestions) {
            if (!BrouterClient.isWithinDistanceTolerance(suggestion.distanceM(), desiredLengthM)) {
                continue;
            }
            double offset = Math.abs(suggestion.distanceM() - desiredLengthM);
            if (offset < bestOffset) {
                bestOffset = offset;
                bestAccepted = accepted.size();
            }
            accepted.add(suggestion);
        }
        suggestions.clear();
        suggestions.addAll(accepted);
        viewBinding.suggestedResultsGroup.removeAllViews();
        for (int i = 0; i < suggestions.size(); i++) {
            RouteSuggestion suggestion = suggestions.get(i);
            MaterialButton option = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            option.setId(View.generateViewId());
            option.setTag(i);
            option.setText(BrouterClient.formatDistance(suggestion.distanceM()) + " · " + BrouterClient.formatGain(suggestion.gainM()));
            viewBinding.suggestedResultsGroup.addView(option);
        }
        viewBinding.suggestedResultsGroup.setVisibility(View.VISIBLE);
        viewBinding.suggestedStatus.setText(R.string.suggested_routes_found);
        viewBinding.suggestedResultsGroup.check(viewBinding.suggestedResultsGroup.getChildAt(bestAccepted).getId());
    }

    private void selectSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) {
            return;
        }
        selected = suggestions.get(index);
        clearSuggestionLines();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < suggestions.size(); i++) {
            RouteSuggestion suggestion = suggestions.get(i);
            Polyline line = new Polyline(viewBinding.suggestedMap);
            if (suggestion.equals(selected)) {
                line.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.opentracks));
                line.getOutlinePaint().setStrokeWidth(6 * density);
            } else {
                line.getOutlinePaint().setColor(0xFF888888);
                line.getOutlinePaint().setStrokeWidth(3 * density);
            }
            line.setPoints(suggestion.points());
            viewBinding.suggestedMap.getOverlays().add(line);
            suggestionLines.add(line);
        }
        int paddingPx = Math.round(48 * density);
        viewBinding.suggestedMap.zoomToBoundingBox(BoundingBox.fromGeoPoints(selected.points()), false, paddingPx);
        viewBinding.suggestedMap.invalidate();
        viewBinding.suggestedRun.setVisibility(View.VISIBLE);
    }

    private void clearSuggestionLines() {
        for (Polyline line : suggestionLines) {
            viewBinding.suggestedMap.getOverlays().remove(line);
        }
        suggestionLines.clear();
        viewBinding.suggestedMap.invalidate();
    }

    /**
     * Starts a normal recording and hands the chosen loop to the recording map as a dashed
     * guidance overlay.
     */
    private void runSelectedRoute() {
        if (selected == null) {
            return;
        }
        try {
            TrackRecordingServiceConnection.executeForeground(this, (service, connection) -> {
                Track.Id trackId = service.startNewTrack();
                Intent intent = IntentUtils.newIntent(this, TrackRecordingActivity.class);
                intent.putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
                intent.putParcelableArrayListExtra(TrackRecordingActivity.EXTRA_PLANNED_ROUTE, new ArrayList<>(selected.points()));
                // Land straight on the Map tab: that is where the dashed loop lives.
                intent.putExtra(TrackRecordingActivity.EXTRA_OPEN_MAP_TAB, true);
                startActivity(intent);
            });
        } catch (MissingPermissionException e) {
            Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show();
        }
    }
}
