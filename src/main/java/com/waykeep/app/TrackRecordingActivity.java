package com.waykeep.app;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.viewpager2.adapter.FragmentStateAdapter;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.osmdroid.util.GeoPoint;

import com.waykeep.app.chart.ChartFragment;
import com.waykeep.app.chart.TrackDataHubInterface;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackDataHub;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackBuilder;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.databinding.TrackRecordingBinding;
import com.waykeep.app.fragments.ChooseActivityTypeDialogFragment;
import com.waykeep.app.fragments.StatisticsRecordingFragment;
import com.waykeep.app.sensors.GpsStatusValue;
import com.waykeep.app.services.HeartRateAlert;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.services.RecordingStatus;
import com.waykeep.app.services.TrackRecordingService;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.SettingsActivity;
import com.waykeep.app.ui.customRecordingLayout.RecordingLayout;
import com.waykeep.app.ui.intervals.IntervalsFragment;
import com.waykeep.app.ui.markers.MarkerEditActivity;
import com.waykeep.app.ui.markers.MarkerListActivity;
import com.waykeep.app.ui.trackmap.TrackMapFragment;
import com.waykeep.app.ui.util.ActivityUtils;
import com.waykeep.app.ui.util.DetailTabs;
import com.waykeep.app.util.IntentUtils;

/**
 * An activity to show the track detail, record a new track or resumes an existing one.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
public class TrackRecordingActivity extends AbstractActivity implements ChooseActivityTypeDialogFragment.ChooseActivityTypeCaller, TrackDataHubInterface {

    public static final String EXTRA_TRACK_ID = "track_id";
    public static final String EXTRA_OPEN_MAP_TAB = "open_map_tab";
    public static final String EXTRA_PLANNED_ROUTE = "planned_route";

    private static final String TAG = TrackRecordingActivity.class.getSimpleName();

    private static final String CURRENT_TAB_TAG_KEY = "current_tab_tag_key";

    private static final int MAP_TAB_POSITION = 3;

    private ValueAnimator statusDotPulse;

    // The following are setFrequency in onCreate
    private ContentProviderUtils contentProviderUtils;
    private TrackRecordingServiceConnection trackRecordingServiceConnection;
    private TrackDataHub trackDataHub;

    private TrackRecordingBinding viewBinding;

    private Track.Id trackId;
    private ArrayList<GeoPoint> plannedRoute;

    private RecordingStatus recordingStatus = TrackRecordingService.STATUS_DEFAULT;
    private GpsStatusValue gpsStatus;
    private String gpsStatusText;
    private String gpsAccuracyText;
    private int lowBatteryPercent = -1;

    @Nullable
    private HeartRateAlert heartRateAlert;

    private final Observer<RecordingData> recordingDataObserver = this::onRecordingDataForHeartRateAlert;

    private final TrackRecordingServiceConnection.Callback bindChangedCallback = (service, unused) -> {
        service.getRecordingStatusObservable()
                .observe(TrackRecordingActivity.this, this::onRecordingStatusChanged);

        service.getGpsStatusObservable()
                .observe(TrackRecordingActivity.this, this::onGpsStatusChanged);

        service.getGpsStatusTextObservable()
                .observe(TrackRecordingActivity.this, text -> {
                    gpsStatusText = text;
                    updateStatusStrip();
                });

        service.getGpsAccuracyTextObservable()
                .observe(TrackRecordingActivity.this, text -> {
                    gpsAccuracyText = text;
                    updateStatusStrip();
                });

        service.getLowBatteryPercentObservable()
                .observe(TrackRecordingActivity.this, percent -> {
                    lowBatteryPercent = percent == null ? -1 : percent;
                    updateStatusStrip();
                });

        service.getRecordingDataObservable()
                .observe(TrackRecordingActivity.this, recordingDataObserver);

        if (!service.isRecording()) {
            finish();
            return;
        }

        trackDataHub.loadTrack(trackId);
        trackDataHub.setRecordingStatus(recordingStatus);
    };

    private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        if (PreferencesUtils.isKey(R.string.stats_show_on_lockscreen_while_recording_key, key)) {
            setLockscreenPolicy();
        }
        if (PreferencesUtils.isKey(R.string.stats_keep_screen_on_while_recording_key, key)) {
            setScreenOnPolicy();
        }
        if (PreferencesUtils.isKey(R.string.stats_fullscreen_while_recording_key, key)) {
            setFullscreenPolicy();
        }
        if (PreferencesUtils.isKey(R.string.settings_heart_rate_alert_enabled_key, key)
                || PreferencesUtils.isKey(R.string.settings_heart_rate_alert_threshold_percent_key, key)
                || PreferencesUtils.isKey(R.string.settings_sensor_heart_rate_max_key, key)) {
            heartRateAlert = null;
        }
        if (key == null) return;

        runOnUiThread(TrackRecordingActivity.this::invalidateOptionsMenu); //TODO Should not be necessary
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contentProviderUtils = new ContentProviderUtils(this);

        trackId = getIntent().getParcelableExtra(EXTRA_TRACK_ID);
        if (trackId == null) {
            throw new RuntimeException("TrackId is mandatory");
        }
        ArrayList<GeoPoint> raw = getIntent().getParcelableArrayListExtra(EXTRA_PLANNED_ROUTE);
        if (raw != null && raw.size() > 20000) {
            raw = new ArrayList<>(raw.subList(0, 20000));
        }
        plannedRoute = raw;
        if (contentProviderUtils.getTrack(trackId) == null) {
            Log.w(TAG, "TrackId does not exists.");
            finish();
        }

        trackRecordingServiceConnection = new TrackRecordingServiceConnection(bindChangedCallback);
        trackDataHub = new TrackDataHub(this);

        CustomFragmentPagerAdapter pagerAdapter = new CustomFragmentPagerAdapter(this);
        viewBinding.trackDetailActivityViewPager.setAdapter(pagerAdapter);
        int initialTab = savedInstanceState != null
                ? savedInstanceState.getInt(CURRENT_TAB_TAG_KEY)
                : (getIntent().getBooleanExtra(EXTRA_OPEN_MAP_TAB, false) ? MAP_TAB_POSITION : 0);
        DetailTabs.bind(viewBinding.trackDetailActivityTabs,
                viewBinding.trackDetailActivityViewPager, initialTab);

        viewBinding.trackRecordingFabAction.setImageResource(R.drawable.ic_baseline_pause_24);
        viewBinding.trackRecordingFabAction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.opentracks));
        viewBinding.trackRecordingFabAction.setBackgroundColor(ContextCompat.getColor(this, R.color.opentracks));
        viewBinding.trackRecordingFabAction.setOnLongClickListener((view) -> {
            ActivityUtils.vibrate(this, Duration.ofSeconds(1));
            trackRecordingServiceConnection.stopRecording(TrackRecordingActivity.this);
            Intent newIntent = IntentUtils.newIntent(TrackRecordingActivity.this, TrackStoppedActivity.class)
                    .putExtra(TrackStoppedActivity.EXTRA_TRACK_ID, trackId);
            startActivity(newIntent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
            return true;
        });
        viewBinding.trackRecordingFabAction.setOnClickListener((view) -> {
            var service = trackRecordingServiceConnection.getTrackRecordingService();
            if (service == null) {
                return;
            }
            if (service.isPaused()) {
                service.resumeCurrentTrack();
                Toast.makeText(TrackRecordingActivity.this, getString(R.string.track_resumed), Toast.LENGTH_SHORT).show();
            } else {
                service.pauseCurrentTrack();
                Toast.makeText(TrackRecordingActivity.this, getString(R.string.track_paused_hint), Toast.LENGTH_SHORT).show();
            }
        });

        setSupportActionBar(viewBinding.bottomAppBar);
    }

    @Override
    public void onAttachedToWindow() {
        setLockscreenPolicy();
        setScreenOnPolicy();
        setFullscreenPolicy();
        super.onAttachedToWindow();
    }

    private void setLockscreenPolicy() {
        boolean showOnLockScreen = PreferencesUtils.shouldShowStatsOnLockscreen();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showOnLockScreen);
        } else if (showOnLockScreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void setScreenOnPolicy() {
        boolean keepScreenOn = PreferencesUtils.shouldKeepScreenOn();

        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setFullscreenPolicy() {
        boolean fullscreen = PreferencesUtils.shouldUseFullscreen();

        WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (fullscreen) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
            }
            return;
        }

        if (fullscreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        trackRecordingServiceConnection.bind(this);
        trackDataHub.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update UI
        invalidateOptionsMenu();

        if (trackId != null) {
            //TODO Pass recordingStatus directly to them
            trackDataHub.loadTrack(trackId);
            trackDataHub.setRecordingStatus(recordingStatus);
        }

        trackRecordingServiceConnection.bind(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_TAB_TAG_KEY, viewBinding.trackDetailActivityViewPager.getCurrentItem());
    }

    @Override
    protected void onStop() {
        super.onStop();
        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        trackRecordingServiceConnection.unbind(this);
        trackDataHub.stop();
    }

    @Override
    protected void onDestroy() {
        stopStatusDotPulse();
        super.onDestroy();
        viewBinding = null;
        trackRecordingServiceConnection = null;
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = TrackRecordingBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.track_record, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem lap = menu.findItem(R.id.track_detail_lap);
        if (lap != null) {
            lap.setVisible(recordingStatus.isRecordingAndNotPaused());
            lap.setContentDescription(getString(R.string.menu_lap_content_description));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.track_detail_lap) {
            var service = trackRecordingServiceConnection.getTrackRecordingService();
            if (service == null) {
                Log.w(TAG, "Lap ignored: recording service is not connected.");
                return true;
            }
            if (!service.recordLap()) {
                Log.w(TAG, "Lap ignored: recording is paused or not active.");
                return true;
            }
            ActivityUtils.vibrate(this, Duration.ofMillis(80));
            Toast.makeText(this, R.string.lap_recorded, Toast.LENGTH_SHORT).show();
            return true;
        }

        if (item.getItemId() == R.id.track_detail_menu_show_on_map) {
            viewBinding.trackDetailActivityViewPager.setCurrentItem(MAP_TAB_POSITION);
            return true;
        }

        if (item.getItemId() == R.id.track_detail_insert_marker) {
            TrackPoint trackPoint = trackRecordingServiceConnection.getTrackRecordingService().getLastStoredTrackPointWithLocation();
            android.location.Location location = trackPoint != null ? trackPoint.position().toLocation() : getLastKnownLocation();
            if (location == null) {
                Toast.makeText(this, R.string.marker_add_error, Toast.LENGTH_LONG).show();
                return true;
            }
            Intent intent = IntentUtils
                    .newIntent(this, MarkerEditActivity.class)
                    .putExtra(MarkerEditActivity.EXTRA_TRACK_ID, trackId)
                    .putExtra(MarkerEditActivity.EXTRA_LOCATION, location);
            startActivity(intent);
            return true;
        }

        if (item.getItemId() == R.id.track_detail_menu_select_layout) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            List<String> layoutNames = PreferencesUtils.getAllCustomLayouts().stream()
                    .map(RecordingLayout::getName)
                    .toList();
            builder.setTitle(getString(R.string.custom_layout_select_layout)).setItems(layoutNames.toArray(new String[0]), (dialog, which) -> PreferencesUtils.setSelectedLayoutName(layoutNames.get(which)));
            builder.create().show();
            return true;
        }

        if (item.getItemId() == R.id.track_detail_markers) {
            Intent intent = IntentUtils.newIntent(this, MarkerListActivity.class)
                    .putExtra(MarkerListActivity.EXTRA_TRACK_ID, trackId);
            startActivity(intent);
            return true;

        }

        if (item.getItemId() == R.id.track_detail_edit) {
            Intent intent = IntentUtils.newIntent(this, TrackEditActivity.class)
                    .putExtra(TrackEditActivity.EXTRA_TRACK_ID, trackId);
            startActivity(intent);
            return true;
        }

        if (item.getItemId() == R.id.track_detail_settings) {
            Intent intent = IntentUtils.newIntent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Gets the {@link TrackDataHub}.
     */
    @Override
    public TrackDataHub getTrackDataHub() {
        return trackDataHub;
    }

    @Override
    public void onChooseActivityTypeDone(ActivityType activityType) {
        TrackBuilder track = new TrackBuilder(contentProviderUtils.getTrack(trackId));
        String activityTypeLocalized = getString(activityType.getLocalizedStringId());
        track.setActivityTypeLocalizedAndUpdateActivityType(this, activityTypeLocalized);

        contentProviderUtils.updateTrack(track.getTrack());
    }

    private class CustomFragmentPagerAdapter extends FragmentStateAdapter {

        public CustomFragmentPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> StatisticsRecordingFragment.newInstance();
                case 1 -> IntervalsFragment.newInstance(trackId, false);
                case 2 -> ChartFragment.newInstance();
                case 3 -> TrackMapFragment.newInstance(plannedRoute);
                default ->
                        throw new RuntimeException("There isn't Fragment associated with the position: " + position);
            };
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }

    /**
     * Fallback when the recording service has no stored trackpoint with location yet
     * (weak/indoor GPS): use the last known system location so markers still work.
     */
    private android.location.Location getLastKnownLocation() {
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }
        try {
            for (String provider : locationManager.getProviders(true)) {
                android.location.Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    return location;
                }
            }
        } catch (SecurityException e) {
            // Permission not granted; caller handles null.
        }
        return null;
    }

    private void onRecordingDataForHeartRateAlert(@Nullable RecordingData data) {
        if (!PreferencesUtils.isHeartRateAlertEnabled()) {
            heartRateAlert = null;
            return;
        }

        if (heartRateAlert == null) {
            HeartRate max = PreferencesUtils.getHeartRateZones().getMax();
            if (!max.isValid() || !Float.isFinite(max.getBPM()) || max.getBPM() <= 0f) {
                return;
            }
            int threshold = Math.round(max.getBPM() * PreferencesUtils.getHeartRateAlertThresholdPercent() / 100f);
            // Keep recovery below the alert threshold: at a low percentage the fixed 85% line
            // would otherwise sit above it and let a plateau re-fire.
            int rearm = Math.min(Math.round(max.getBPM() * 0.85f), threshold);
            heartRateAlert = new HeartRateAlert(threshold, rearm, Duration.ofSeconds(10), Duration.ofSeconds(30));
        }

        HeartRate heartRate = data != null && data.sensorDataSet() != null && data.sensorDataSet().heartRate() != null
                ? data.sensorDataSet().heartRate().data()
                : null;

        if (heartRateAlert.update(heartRate, Instant.now())) {
            Toast.makeText(this, R.string.heart_rate_alert_fired, Toast.LENGTH_SHORT).show();
            ActivityUtils.vibrate(this, Duration.ofMillis(400));
        }
    }

    private void onRecordingStatusChanged(RecordingStatus status) {
        if (!status.isRecording()) {
            finish();
        }
        recordingStatus = status;

        trackDataHub.setRecordingStatus(recordingStatus);

        // Tap pauses, long-press stops: the icon shows the tap action.
        viewBinding.trackRecordingFabAction.setImageResource(status.isPaused()
                ? R.drawable.ic_baseline_play_arrow_24
                : R.drawable.ic_baseline_pause_24);

        // The lap action is only offered while actively recording.
        invalidateOptionsMenu();

        updateStatusStrip();

        setLockscreenPolicy();
        setScreenOnPolicy();
    }

    private void onGpsStatusChanged(GpsStatusValue gpsStatusValue) {
        gpsStatus = gpsStatusValue;
        if (!gpsStatusValue.hasGoodSignal()) {
            gpsAccuracyText = null;
        }
        updateStatusStrip();
    }

    /**
     * The strip is the persistent home of recording state, visible on every tab. A usable signal
     * shows its current accuracy; GPS problems and low battery get warning text.
     */
    private void updateStatusStrip() {
        if (!recordingStatus.isRecording()) {
            viewBinding.recordingStatusStrip.setVisibility(View.GONE);
            stopStatusDotPulse();
            return;
        }

        String message = null;
        boolean hideGpsDisabled = false;
        if (recordingStatus.isPaused()) {
            message = getString(R.string.track_paused_notification);
        } else if (gpsStatus != null && gpsStatus.isSignalUnusable()) {
            message = gpsStatusMessage();
        } else if (gpsStatus == GpsStatusValue.GPS_DISABLED || gpsStatus == GpsStatusValue.GPS_NONE) {
            if (!PreferencesUtils.shouldShowGpsDisabledWarning()) {
                hideGpsDisabled = true;
            } else {
                message = gpsStatusMessage();
            }
        } else if (gpsStatus == GpsStatusValue.GPS_ENABLED) {
            message = gpsStatusMessage();
        }

        if (hideGpsDisabled && lowBatteryPercent < 0) {
            viewBinding.recordingStatusStrip.setVisibility(View.GONE);
            stopStatusDotPulse();
            return;
        }

        boolean gpsProblem = message != null;
        String detail = message;
        if (detail == null && gpsStatus != null && gpsStatus.hasGoodSignal()) {
            detail = gpsAccuracyText;
        }
        if (lowBatteryPercent >= 0) {
            String batteryWarning = getString(R.string.recording_low_battery, lowBatteryPercent);
            detail = detail == null ? batteryWarning
                    : getString(R.string.recording_status_details, detail, batteryWarning);
        }

        boolean nominal = !gpsProblem && lowBatteryPercent < 0;
        viewBinding.recordingStatusStrip.setVisibility(View.VISIBLE);
        String description = detail == null ? getString(R.string.recording_status_recording)
                : nominal ? getString(R.string.recording_status_details,
                getString(R.string.recording_status_recording), detail) : detail;
        viewBinding.recordingStatusStrip.setContentDescription(description);
        viewBinding.recordingStatusStripText.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        viewBinding.recordingStatusStripText.setText(detail);
        viewBinding.recordingStatusStripDot.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                nominal ? R.color.semantic_recording_red : R.color.semantic_offline_amber)));

        if (nominal) {
            startStatusDotPulse();
        } else {
            stopStatusDotPulse();
        }
    }

    /** Falls back to the enum text when no satellite count has arrived (old devices, no fix yet). */
    private String gpsStatusMessage() {
        return gpsStatusText != null ? gpsStatusText : getString(gpsStatus.message);
    }

    private void startStatusDotPulse() {
        if (statusDotPulse != null) {
            return;
        }
        if (animationsDisabled()) {
            viewBinding.recordingStatusStripDot.setAlpha(1f);
            return;
        }
        statusDotPulse = ValueAnimator.ofFloat(0.35f, 1f);
        statusDotPulse.setDuration(1200);
        statusDotPulse.setRepeatCount(ValueAnimator.INFINITE);
        statusDotPulse.setRepeatMode(ValueAnimator.REVERSE);
        statusDotPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        statusDotPulse.addUpdateListener(animation ->
                viewBinding.recordingStatusStripDot.setAlpha((float) animation.getAnimatedValue()));
        statusDotPulse.start();
    }

    private void stopStatusDotPulse() {
        if (statusDotPulse != null) {
            statusDotPulse.cancel();
            statusDotPulse = null;
        }
        if (viewBinding != null) {
            viewBinding.recordingStatusStripDot.setAlpha(1f);
        }
    }

    private boolean animationsDisabled() {
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }
}
