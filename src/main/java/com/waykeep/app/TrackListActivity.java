/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.waykeep.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.location.LocationManager;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.osmdroid.util.GeoPoint;
import org.xml.sax.SAXException;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.insights.InsightSummary;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.diagnostics.DiagnosticsRecovery;
import com.waykeep.app.diagnostics.DiagnosticsRecovery.RecoveryCandidate;
import com.waykeep.app.diagnostics.DiagnosticsShare;
import com.waykeep.app.databinding.TrackListBinding;
import com.waykeep.app.io.file.importer.GpxRouteParser;
import com.waykeep.app.io.file.importer.RoutePoint;
import com.waykeep.app.publicapi.DataProvider;
import com.waykeep.app.sensors.GpsStatusValue;
import com.waykeep.app.services.MissingPermissionException;
import com.waykeep.app.services.RecordingStatus;
import com.waykeep.app.services.TrackRecordingService;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.SettingsActivity;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.share.ShareUtils;
import com.waykeep.app.ui.TrackListAdapter;
import com.waykeep.app.ui.aggregatedStatistics.AggregatedStatisticsActivity;
import com.waykeep.app.ui.aggregatedStatistics.ConfirmDeleteDialogFragment;
import com.waykeep.app.ui.markers.MarkerListActivity;
import com.waykeep.app.ui.monthlyRecap.MonthlyRecapActivity;
import com.waykeep.app.ui.tour.TourOverlay;
import com.waykeep.app.ui.personalBests.PersonalBestsActivity;
import com.waykeep.app.ui.suggested.SuggestedRoutesActivity;
import com.waykeep.app.ui.util.ActivityUtils;
import com.waykeep.app.ui.util.RecyclerViewSwipeDeleteCallback;
import com.waykeep.app.util.IntentUtils;
import com.waykeep.app.util.PermissionRequester;

/**
 * An activity displaying a list of tracks.
 *
 * @author Leif Hendrik Wilden
 */
public class TrackListActivity extends AbstractTrackDeleteActivity implements ConfirmDeleteDialogFragment.ConfirmDeleteCaller {

    private static final String TAG = TrackListActivity.class.getSimpleName();
    private static final String KEY_DEFERRED_RECOVERY_SESSION = "deferredRecoverySession";

    // The following are set in onCreate
    private TrackRecordingServiceConnection recordingStatusConnection;
    private TrackListAdapter adapter;
    private final ExecutorService trackLoader = Executors.newSingleThreadExecutor();

    private TrackListBinding viewBinding;
    private RecoveryCandidate recoveryPromptCandidate;
    private UUID deferredRecoverySessionId;
    private UUID recoveryWorkSessionId;
    private boolean recordingStatusReady;
    private boolean recoveryScanInFlight;
    private boolean recoveryScanCompleted;
    private boolean insightReady;

    // Preferences
    private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();

    private GpsStatusValue gpsStatusValue = TrackRecordingService.STATUS_GPS_DEFAULT;
    private RecordingStatus recordingStatus = TrackRecordingService.STATUS_DEFAULT;

    // Callback when an item is selected in the contextual action mode
    private final ActivityUtils.ContextualActionModeCallback contextualActionModeCallback = new ActivityUtils.ContextualActionModeCallback() {

        @Override
        public void onPrepare(Menu menu, int[] positions, long[] trackIds, boolean showSelectAll) {
            boolean isSingleSelection = trackIds.length == 1;

            viewBinding.bottomAppBar.performHide(true);
            viewBinding.trackListFabAction.setVisibility(View.INVISIBLE);

            menu.findItem(R.id.list_context_menu_edit).setVisible(isSingleSelection);
            menu.findItem(R.id.list_context_menu_select_all).setVisible(showSelectAll);
        }

        @Override
        public boolean onClick(int itemId, int[] positions, long[] trackIds) {
            return handleContextItem(itemId, trackIds);
        }

        @Override
        public void onDestroy() {
            viewBinding.trackListFabAction.setVisibility(View.VISIBLE);
            viewBinding.bottomAppBar.performShow(true);
        }
    };

    private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener = (sharedPreferences, key) -> {
        if (PreferencesUtils.isKey(R.string.stats_units_key, key)) {
            unitSystem = PreferencesUtils.getUnitSystem();
            if (adapter != null) {
                adapter.updateUnitSystem(unitSystem);
            }
        }
        if (key != null) {
            runOnUiThread(() -> {
                TrackListActivity.this.invalidateOptionsMenu();
                loadData();
            });
        }
    };

    // Menu items

    private static final String KEY_SEARCH_QUERY = "searchQuery";

    private String searchQuery;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // Pre-registered in onCreate: requesting background location from a dialog click
    // must use an early-registered launcher; registering late throws.
    private ActivityResultLauncher<String[]> backgroundPermissionLauncher;
    private ActivityResultLauncher<String[]> gpxRoutePicker;

    private final TrackRecordingServiceConnection.Callback bindChangedCallback = (service, unused) -> {
        service.getRecordingStatusObservable()
                .observe(TrackListActivity.this, this::onRecordingStatusChanged);

        service.getGpsStatusObservable()
                .observe(TrackListActivity.this, this::onGpsStatusChanged);

        updateGpsMenuItem(gpsStatusValue.isGpsStarted(), recordingStatus.isRecording());
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            String deferredId = savedInstanceState.getString(KEY_DEFERRED_RECOVERY_SESSION);
            try {
                deferredRecoverySessionId = deferredId == null ? null : UUID.fromString(deferredId);
            } catch (IllegalArgumentException ignored) {
                deferredRecoverySessionId = null;
            }
        }

        requestRequiredPermissions();
        backgroundPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            // Best-effort survival aid; recording proceeds regardless. Chain the battery prompt next.
            showBatteryPrompt();
        });
        gpxRoutePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) {
                Toast.makeText(this, R.string.gpx_route_cancelled, Toast.LENGTH_SHORT).show();
                return;
            }
            followGpxRoute(uri);
        });

        recordingStatusConnection = new TrackRecordingServiceConnection(bindChangedCallback);

        viewBinding.aggregatedStatsButton.setOnClickListener((view) -> startActivity(IntentUtils.newIntent(this, AggregatedStatisticsActivity.class)));
        viewBinding.suggestedRoutesButton.setOnClickListener((view) -> startActivity(IntentUtils.newIntent(this, SuggestedRoutesActivity.class)));
        viewBinding.sensorStartButton.setOnClickListener((view) -> {
            LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else {
                if (gpsStatusValue.isGpsStarted()) {
                    recordingStatusConnection.stopService(this);
                } else {
                    // Optimistic feedback: the service start + first fix take seconds, during
                    // which the button would otherwise sit unchanged on the off icon.
                    gpsStatusValue = GpsStatusValue.GPS_ENABLED;
                    updateGpsMenuItem(true, recordingStatus.isRecording());
                    startSensorsOrRecording((service, connection) -> service.tryStartSensors());
                }
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        adapter = new TrackListAdapter(this, viewBinding.trackList, recordingStatus, unitSystem);
        viewBinding.trackList.setLayoutManager(layoutManager);
        viewBinding.trackList.setAdapter(adapter);
        addRowDivider(viewBinding.trackList);
        viewBinding.trackListEmptyAction.setOnClickListener(view -> viewBinding.trackListFabAction.performClick());
        viewBinding.diagnosticsRecoveryNotNow.setOnClickListener(view -> deferRecoveryPrompt());
        viewBinding.diagnosticsRecoveryCopy.setOnClickListener(view -> recoverTrackCopy());

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(new RecyclerViewSwipeDeleteCallback(this) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                TrackListActivity.this.onTrackSwiped(new Track.Id(viewHolder.getItemId()));
            }
        });
        itemTouchhelper.attachToRecyclerView(viewBinding.trackList);
        viewBinding.trackListFabAction.setOnClickListener((view) -> {
            if (recordingStatus.isPaused()) {
                // Paused -> jump back into the recording screen to resume.
                Intent resumeIntent = IntentUtils.newIntent(TrackListActivity.this, TrackRecordingActivity.class);
                resumeIntent.putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, recordingStatus.trackId());
                startActivity(resumeIntent);
                return;
            }
            if (recordingStatus.isRecording()) {
                // Explicit stop path; long-press remains as a shortcut.
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.stop_recording_title)
                        .setMessage(R.string.stop_recording_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.image_stop, (dialog, which) -> stopRecordingFromFeed())
                        .show();
                return;
            }

            // Not Recording -> Recording
            Log.i(TAG, "Starting recording");
            updateGpsMenuItem(false, true);
            startSensorsOrRecording((service, connection) -> {
                Track.Id trackId = service.startNewTrack();

                ensureBackgroundResilience();

                Intent newIntent = IntentUtils.newIntent(TrackListActivity.this, TrackRecordingActivity.class);
                newIntent.putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
                startActivity(newIntent);
            });
        });
        viewBinding.trackListFabAction.setOnLongClickListener((view) -> {
            if (!recordingStatus.isRecording()) {
                return false;
            }

            // Recording -> Stop
            stopRecordingFromFeed();
            return true;
        });

        setSupportActionBar(viewBinding.trackListToolbar);
        adapter.setActionModeCallback(contextualActionModeCallback);
        applyBottomBarClearance();
    }

    /**
     * Hairline under each row. Drawn as a decoration, not inside the item, so it follows the
     * final row height when the context line wraps at large font scales.
     */
    private void addRowDivider(RecyclerView list) {
        float density = getResources().getDisplayMetrics().density;
        int insetStart = Math.round(124 * density);
        int insetEnd = Math.round(20 * density);
        Paint paint = new Paint();
        paint.setColor(MaterialColors.getColor(list,
                com.google.android.material.R.attr.colorOutlineVariant, 0));
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
                int right = parent.getWidth() - parent.getPaddingRight() - insetEnd;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    canvas.drawRect(insetStart, child.getBottom() - 1, right, child.getBottom(), paint);
                }
            }
        });
    }

    /**
     * The cradled record FAB protrudes above the bottom app bar, so the RecyclerView needs that
     * much extra bottom padding to keep the last row readable and tappable at any font scale.
     */
    private void applyBottomBarClearance() {
        View list = viewBinding.trackList;
        View bar = viewBinding.bottomAppBar;
        View fab = viewBinding.trackListFabAction;
        list.post(() -> {
            int margin = Math.round(8 * getResources().getDisplayMetrics().density);
            int bottom = bar.getHeight() + fab.getHeight() / 2 + margin;
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottom);
        });
    }

    private void requestRequiredPermissions() {
        PermissionRequester.ALL.requestPermissionsIfNeeded(this, this, null, (requester) -> Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onStart() {
        super.onStart();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        recordingStatusReady = false;
        recordingStatusConnection.bindWithStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update UI
        this.invalidateOptionsMenu();
        loadData();

        // Float button
        setFloatButton();

        maybeShowTour();
        maybePromptRecovery();
    }

    /**
     * First-run spotlight tour (Driver.js-style): points at the GPS button and the record
     * button. Shown once, after the introduction slides.
     */
    private void maybeShowTour() {
        if (!PreferencesUtils.shouldShowTour()) {
            return;
        }
        PreferencesUtils.setShowTour(false);

        List<TourOverlay.Step> steps = List.of(
                new TourOverlay.Step(R.id.sensor_start_button, getString(R.string.tour_gps_title), getString(R.string.tour_gps_body)),
                new TourOverlay.Step(R.id.track_list_fab_action, getString(R.string.tour_record_title), getString(R.string.tour_record_body))
        );
        // The targets need to be laid out before their position can be read.
        viewBinding.getRoot().post(() -> new TourOverlay(this, steps, null).show());
    }

    @Override
    protected void onStop() {
        super.onStop();

        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        recordingStatusConnection.unbind(this);
        deferredRecoverySessionId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trackLoader.shutdownNow();
        searchHandler.removeCallbacksAndMessages(null);
        viewBinding = null;
        recordingStatusConnection = null;
        adapter = null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SEARCH_QUERY, searchQuery);
        if (deferredRecoverySessionId != null) {
            outState.putString(KEY_DEFERRED_RECOVERY_SESSION, deferredRecoverySessionId.toString());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // The SearchBar logo is the ActionBar home button, and AppCompat routes its taps here
        // (setNavigationOnClickListener is ignored once setSupportActionBar is used).
        // Scroll to top instead of exiting; Exit lives in the overflow menu.
        if (viewBinding != null) {
            viewBinding.trackList.smoothScrollToPosition(0);
        }
        return true;
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = TrackListBinding.inflate(getLayoutInflater());

        viewBinding.trackListSearchView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            searchQuery = viewBinding.trackListSearchView.getEditText().getText().toString();
            viewBinding.trackListSearchView.hide();
            loadData();
            return true;
        });
        if (searchQuery != null) {
            viewBinding.trackListSearchView.getEditText().setText(searchQuery);
        }
        // Live filtering: every keystroke re-queries after a short debounce.
        viewBinding.trackListSearchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                final String text = s.toString();
                updateInsightHeroVisibility();
                pendingSearch = () -> {
                    searchQuery = text;
                    loadData();
                };
                searchHandler.postDelayed(pendingSearch, 300);
            }
        });
        // #2268: the floating record button overlaps the expanded search field.
        viewBinding.trackListSearchView.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
            updateRecordButtonVisibility();
            updateInsightHeroVisibility();
        });

        return viewBinding.getRoot();
    }

    private void updateRecordButtonVisibility() {
        if (viewBinding == null) {
            return;
        }
        boolean searching = viewBinding.trackListSearchView.getEditText().hasFocus();
        viewBinding.trackListFabAction.setVisibility(searching ? View.GONE : View.VISIBLE);
    }

    /**
     * The insight hero is a scanning aid, not a result: hide it while the feed is filtered or the
     * search field is active, and restore it once the query clears.
     */
    private void updateInsightHeroVisibility() {
        if (viewBinding == null) {
            return;
        }
        boolean searching = !TextUtils.isEmpty(searchQuery)
                || !TextUtils.isEmpty(viewBinding.trackListSearchView.getEditText().getText())
                || viewBinding.trackListSearchView.getEditText().hasFocus();
        viewBinding.insightHero.setVisibility(insightReady && !searching ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.track_list, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateGpsMenuItem(gpsStatusValue.isGpsStarted(), recordingStatus.isRecording());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.track_list_markers) {
            startActivity(IntentUtils.newIntent(this, MarkerListActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_follow_gpx) {
            gpxRoutePicker.launch(new String[]{"*/*"});
            return true;
        }

        if (item.getItemId() == R.id.track_list_personal_bests) {
            startActivity(IntentUtils.newIntent(this, PersonalBestsActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_monthly_recap) {
            startActivity(IntentUtils.newIntent(this, MonthlyRecapActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_share_diagnostics) {
            shareLatestDiagnostics();
            return true;
        }

        if (item.getItemId() == R.id.track_list_settings) {
            startActivity(IntentUtils.newIntent(this, SettingsActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_help) {
            startActivity(IntentUtils.newIntent(this, HelpActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_about) {
            startActivity(IntentUtils.newIntent(this, AboutActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_exit) {
            finishAndRemoveTask();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareLatestDiagnostics() {
        trackLoader.execute(() -> {
            Intent shareIntent;
            try {
                File archive = DiagnosticsShare.createLatestArchive(this);
                if (archive == null) {
                    runOnUiThread(() -> Toast.makeText(this,
                            R.string.diagnostics_share_no_session, Toast.LENGTH_SHORT).show());
                    return;
                }
                shareIntent = DiagnosticsShare.newShareIntent(this, archive);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Could not prepare diagnostics archive.", e);
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.diagnostics_share_failed, Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) startActivity(shareIntent);
            });
        });
    }

    private void stopRecordingFromFeed() {
        ActivityUtils.vibrate(this, Duration.ofSeconds(1));
        updateGpsMenuItem(false, false);
        recordingStatusConnection.stopRecording(TrackListActivity.this);
        viewBinding.trackListFabAction.setImageResource(R.drawable.ic_baseline_record_24);
        viewBinding.trackListFabAction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red_dark));
        viewBinding.trackListFabAction.setImageTintList(ColorStateList.valueOf(Color.WHITE));
    }

    /**
     * Swipe-to-delete asks first: the confirm dialog deletes on confirmation and
     * restores the row on cancel (see onConfirmDeleteAbort).
     */
    private void onTrackSwiped(Track.Id trackId) {
        if (trackId.equals(recordingStatus.trackId())) {
            Toast.makeText(this, getString(R.string.track_delete_not_recording), Toast.LENGTH_LONG).show();
            loadData();
            return;
        }
        ConfirmDeleteDialogFragment.showDialog(getSupportFragmentManager(), trackId);
    }

    private void loadData() {
        viewBinding.trackListToolbar.setText(searchQuery);

        viewBinding.trackListToolbar.setTitle(Objects.requireNonNullElseGet(searchQuery, () -> getString(R.string.app_name)));

        // SQLite query plus cursor walk off the main thread; DiffUtil then animates only what changed.
        final String query = searchQuery;
        trackLoader.execute(() -> {
            List<TrackListIterator.Item> items = new ArrayList<>();
            try (TrackListIterator iterator = new ContentProviderUtils(this).searchTracks(query)) {
                while (iterator.hasNext()) {
                    items.add(iterator.next());
                }
            } catch (RuntimeException e) {
                return;
            }
            // Insight covers the whole history, so skip it while the feed is filtered.
            final InsightSummary insight = TextUtils.isEmpty(query)
                    ? InsightSummary.compute(items, Instant.now(), ZoneId.systemDefault(),
                    PreferencesUtils.getWeeklyGoalDistance())
                    : null;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && adapter != null) {
                    adapter.setHighlightQuery(query);
                    adapter.swapData(items);
                    boolean empty = items.isEmpty();
                    viewBinding.trackListEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                    if (empty) {
                        boolean searching = !TextUtils.isEmpty(query);
                        viewBinding.trackListEmptyMessage.setText(searching ? R.string.track_list_no_results : R.string.track_list_empty_message);
                        viewBinding.trackListEmptyAction.setVisibility(searching ? View.GONE : View.VISIBLE);
                    }
                    renderInsight(insight);
                }
            });
        });
    }

    private void renderInsight(@Nullable InsightSummary summary) {
        if (viewBinding == null || summary == null) {
            return;
        }

        DistanceFormatter formatter = DistanceFormatter.Builder()
                .setUnit(PreferencesUtils.getUnitSystem())
                .setDecimalPlaces(1)
                .build(this);
        Distance weekDistance = summary.goalProgressDistance();
        viewBinding.insightWeekDistance.setText(formatter.formatDistance(weekDistance));
        viewBinding.insightWeekAverage.setText(getString(R.string.insight_average_compact,
                formatter.formatDistance(summary.fourWeekWeeklyAverage())));
        viewBinding.insightTrend.setText(summary.trendLineRes());

        int streakDays = summary.dailyStreak().consecutiveDays();
        viewBinding.insightStreakText.setText(streakDays > 0
                ? getString(R.string.insight_streak_days, streakDays)
                : getString(summary.streakLineRes()));

        if (summary.hasGoal()) {
            Distance goal = summary.weeklyGoal();
            viewBinding.insightSetGoalHint.setVisibility(View.GONE);
            viewBinding.insightGoalProgress.setVisibility(View.VISIBLE);
            viewBinding.insightLane.setVisibility(View.VISIBLE);
            int percent = (int) Math.min(100, Math.round(100.0 * weekDistance.toM() / goal.toM()));
            viewBinding.insightGoalProgress.setText(getString(R.string.insight_goal_percent,
                    percent, formatter.formatDistance(goal)));
            viewBinding.insightLane.setValues(weekDistance.toM(), goal.toM(),
                    summary.fourWeekWeeklyAverage().toM());
        } else {
            viewBinding.insightGoalProgress.setVisibility(View.GONE);
            viewBinding.insightLane.setVisibility(View.GONE);
            viewBinding.insightSetGoalHint.setVisibility(View.VISIBLE);
        }

        insightReady = true;
        updateInsightHeroVisibility();
    }

    @Override
    public void onConfirmDeleteAbort(Track.Id... trackIds) {
        // This is not nice, but it will happen rarely (as deletion is not a standard case).
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    protected void onDeleteConfirmed() {
        // Do nothing
    }

    @Override
    public void onDeleteFinished() {
        // Do nothing
    }

    @Nullable
    @Override
    protected Track.Id getRecordingTrackId() {
        return recordingStatus.trackId();
    }

    /**
     * Updates the menu items with the icon specified.
     *
     * @param isGpsStarted true if gps is started
     * @param isRecording  true if recording
     */
    //TODO Check if if can be avoided to call this outside of onGpsStatusChanged()
    private void updateGpsMenuItem(boolean isGpsStarted, boolean isRecording) {
        MaterialButton startGpsMenuItem = viewBinding.sensorStartButton;
        startGpsMenuItem.setVisibility(!isRecording ? View.VISIBLE : View.INVISIBLE);
        int gpsAction = isGpsStarted ? R.string.action_stop_gps : R.string.action_start_gps;
        startGpsMenuItem.setContentDescription(getString(gpsAction));
        startGpsMenuItem.setTooltipText(getString(gpsAction));
        if (!isRecording) {
            android.graphics.drawable.Drawable icon = AppCompatResources.getDrawable(this,
                    isGpsStarted ? gpsStatusValue.icon : R.drawable.ic_gps_off_24dp);
            if (icon == null) {
                icon = AppCompatResources.getDrawable(this, R.drawable.ic_gps_off_24dp);
            }
            startGpsMenuItem.setIcon(icon);
            if (startGpsMenuItem.getIcon() instanceof AnimatedVectorDrawable animatedVectorDrawable) {
                animatedVectorDrawable.start();
            }
        }
    }

    /**
     * Handles a context item selection.
     *
     * @param itemId       the menu item id
     * @param longTrackIds the track ids
     * @return true if handled.
     */
    private boolean handleContextItem(int itemId, long... longTrackIds) {
        Track.Id[] trackIds = new Track.Id[longTrackIds.length];
        for (int i = 0; i < longTrackIds.length; i++) {
            trackIds[i] = new Track.Id(longTrackIds[i]);
        }

        if (itemId == R.id.list_context_menu_show_on_map) {
            if (trackIds.length == 1) {
                // Single track: open the in-app map tab.
                Track.Id trackId = trackIds[0];
                Intent intent;
                if (recordingStatus.isRecording() && trackId.equals(recordingStatus.trackId())) {
                    intent = IntentUtils.newIntent(this, TrackRecordingActivity.class)
                            .putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId)
                            .putExtra(TrackRecordingActivity.EXTRA_OPEN_MAP_TAB, true);
                } else {
                    intent = IntentUtils.newIntent(this, TrackRecordedActivity.class)
                            .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, trackId)
                            .putExtra(TrackRecordedActivity.EXTRA_OPEN_MAP_TAB, true);
                }
                startActivity(intent);
            } else {
                // Multiple tracks: delegate to an external dashboard app.
                DataProvider.showTrackOnMap(this, recordingStatus.isRecording(), trackIds);
            }
            return true;
        }

        if (itemId == R.id.list_context_menu_share) {
            Intent intent = ShareUtils.newShareFileIntent(this, trackIds);
            intent = Intent.createChooser(intent, null);
            startActivity(intent);
            return true;
        }

        if (itemId == R.id.list_context_menu_edit) {
            Intent intent = IntentUtils.newIntent(this, TrackEditActivity.class)
                    .putExtra(TrackEditActivity.EXTRA_TRACK_ID, trackIds[0]);
            startActivity(intent);
            return true;
        }

        if (itemId == R.id.list_context_menu_delete) {
            deleteTracks(trackIds);
            return true;
        }

        if (itemId == R.id.list_context_menu_aggregated_stats) {
            Intent intent = IntentUtils.newIntent(this, AggregatedStatisticsActivity.class)
                    .putParcelableArrayListExtra(AggregatedStatisticsActivity.EXTRA_TRACK_IDS, new ArrayList<>(Arrays.asList(trackIds)));
            startActivity(intent);
            return true;
        }

        if (itemId == R.id.list_context_menu_select_all) {
            adapter.setAllSelected(true);
            return false;
        }

        return false;
    }

    public void onGpsStatusChanged(GpsStatusValue newStatus) {
        gpsStatusValue = newStatus;
        updateGpsMenuItem(newStatus.isGpsStarted(), recordingStatus.isRecording());
    }

    private void setFloatButton() {
        int icon = recordingStatus.isPaused() ? R.drawable.ic_baseline_play_arrow_24
                : recordingStatus.isRecording() ? R.drawable.ic_baseline_stop_24 : R.drawable.ic_baseline_record_24;
        viewBinding.trackListFabAction.setImageResource(icon);
        boolean recording = recordingStatus.isRecording();
        viewBinding.trackListFabAction.setBackgroundTintList(ContextCompat.getColorStateList(this, recording ? R.color.opentracks : R.color.red_dark));
        // White on dark red, black on cyan: both states stay above the 4.5:1 floor.
        viewBinding.trackListFabAction.setImageTintList(ColorStateList.valueOf(recording ? Color.BLACK : Color.WHITE));
    }

    private void onRecordingStatusChanged(RecordingStatus status) {
        boolean wasRecording = recordingStatus.isRecording();
        recordingStatus = status;
        recordingStatusReady = true;
        setFloatButton();
        adapter.updateRecordingStatus(recordingStatus);
        if (status.isRecording()) {
            hideRecoveryPrompt();
        } else {
            // A run just ended, so a new recoverable session may exist; rescan once.
            if (wasRecording) recoveryScanCompleted = false;
            maybePromptRecovery();
        }
    }

    private void maybePromptRecovery() {
        if (viewBinding == null || !recordingStatusReady || recordingStatus.isRecording()
                || recoveryScanInFlight || recoveryScanCompleted
                || recoveryPromptCandidate != null || recoveryWorkSessionId != null) return;
        recoveryScanInFlight = true;
        Context appContext = getApplicationContext();
        trackLoader.execute(() -> {
            Optional<RecoveryCandidate> candidate = Optional.empty();
            try {
                candidate = DiagnosticsRecovery.findPending(appContext);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not check for a recoverable recording.", e);
            }
            Optional<RecoveryCandidate> pending = candidate;
            runOnUiThread(() -> {
                recoveryScanInFlight = false;
                recoveryScanCompleted = true;
                if (viewBinding == null || isFinishing()
                        || !DiagnosticsRecovery.shouldOffer(recordingStatus.isRecording(), pending)) return;
                RecoveryCandidate recovery = pending.get();
                if (recovery.sessionId().equals(deferredRecoverySessionId)
                        || recovery.sessionId().equals(recoveryWorkSessionId)) return;
                recoveryPromptCandidate = recovery;
                viewBinding.diagnosticsRecoveryPrompt.setVisibility(View.VISIBLE);
            });
        });
    }

    private void hideRecoveryPrompt() {
        recoveryPromptCandidate = null;
        if (viewBinding != null) viewBinding.diagnosticsRecoveryPrompt.setVisibility(View.GONE);
    }

    private void deferRecoveryPrompt() {
        if (recoveryPromptCandidate != null) {
            deferredRecoverySessionId = recoveryPromptCandidate.sessionId();
        }
        hideRecoveryPrompt();
    }

    private void recoverTrackCopy() {
        RecoveryCandidate candidate = recoveryPromptCandidate;
        if (candidate == null) return;
        recoveryWorkSessionId = candidate.sessionId();
        hideRecoveryPrompt();
        try {
            DiagnosticsRecovery.recover(this, candidate);
            Toast.makeText(this, R.string.diagnostics_recovery_started, Toast.LENGTH_SHORT).show();
            observeRecoveryWork(candidate);
        } catch (RuntimeException e) {
            recoveryWorkSessionId = null;
            Log.w(TAG, "Could not enqueue recovered-track copy.", e);
            Toast.makeText(this, R.string.diagnostics_recovery_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void observeRecoveryWork(RecoveryCandidate candidate) {
        LiveData<List<WorkInfo>> work = WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(DiagnosticsRecovery.uniqueWorkName(candidate));
        Observer<List<WorkInfo>> observer = new Observer<>() {
            @Override
            public void onChanged(List<WorkInfo> infos) {
                if (infos == null || infos.isEmpty()
                        || infos.stream().anyMatch(info -> !info.getState().isFinished())) return;
                work.removeObserver(this);
                recoveryWorkSessionId = null;
                if (infos.stream().anyMatch(info -> info.getState() == WorkInfo.State.SUCCEEDED)) {
                    Toast.makeText(TrackListActivity.this,
                            R.string.diagnostics_recovery_done, Toast.LENGTH_SHORT).show();
                    loadData();
                } else {
                    Toast.makeText(TrackListActivity.this,
                            R.string.diagnostics_recovery_failed, Toast.LENGTH_LONG).show();
                    maybePromptRecovery();
                }
            }
        };
        work.observe(this, observer);
    }

    private void followGpxRoute(Uri uri) {
        trackLoader.execute(() -> {
            List<RoutePoint> route;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("No stream for " + uri);
                }
                route = GpxRouteParser.parse(input, GpxRouteParser.MAX_POINTS);
            } catch (IOException | SAXException | SecurityException e) {
                Log.w(TAG, "Could not read GPX route.", e);
                runOnUiThread(() -> Toast.makeText(this, R.string.gpx_route_parse_failed, Toast.LENGTH_LONG).show());
                return;
            }

            if (route.size() < 2) {
                runOnUiThread(() -> Toast.makeText(this, R.string.gpx_route_no_points, Toast.LENGTH_LONG).show());
                return;
            }

            ArrayList<GeoPoint> points = new ArrayList<>(route.size());
            for (RoutePoint point : route) {
                points.add(new GeoPoint(point.latitude(), point.longitude()));
            }
            runOnUiThread(() -> startRecordingWithRoute(points));
        });
    }

    /**
     * Starts a normal recording and hands the picked GPX route to the recording map as a guidance
     * overlay, mirroring the suggested-routes flow.
     */
    private void startRecordingWithRoute(ArrayList<GeoPoint> route) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            TrackRecordingServiceConnection.executeForeground(this, (service, connection) -> {
                Track.Id trackId = service.startNewTrack();
                if (trackId == null) {
                    Toast.makeText(this, R.string.gpx_route_already_recording, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = IntentUtils.newIntent(this, TrackRecordingActivity.class);
                intent.putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
                intent.putParcelableArrayListExtra(TrackRecordingActivity.EXTRA_PLANNED_ROUTE, route);
                intent.putExtra(TrackRecordingActivity.EXTRA_OPEN_MAP_TAB, true);
                startActivity(intent);
            });
        } catch (MissingPermissionException e) {
            Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startSensorsOrRecording(TrackRecordingServiceConnection.Callback callback) {
        try {
            TrackRecordingServiceConnection.executeForeground(this, callback);
        } catch (MissingPermissionException e) {
            Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * After a run starts, ask for the two things that keep it alive when swiped away:
     * background location (Android 10+) and battery-optimization exemption (low-RAM /
     * aggressive OEM killers). Recording already proceeds; these only improve survival.
     * Prompts chain one at a time instead of stacking.
     */
    private void ensureBackgroundResilience() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionRequester.BACKGROUND.hasPermission(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_background_title)
                    .setMessage(R.string.permission_background_message)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> showBatteryPrompt())
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            backgroundPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}))
                    .setOnCancelListener(dialog -> showBatteryPrompt())
                    .show();
            return;
        }
        showBatteryPrompt();
    }

    private void showBatteryPrompt() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.battery_optimization_title)
                .setMessage(R.string.battery_optimization_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // 1. Direct system request: shows the "Allow this app to ignore
                    // battery optimizations?" dialog on stock Android.
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "Direct battery exemption request failed", e);
                    }
                    // 2. HyperOS/Xiaomi and other OEMs block the direct request. The app's
                    // details page always opens and exposes "Battery" -> "Unrestricted".
                    try {
                        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        details.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(details);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "App details settings failed", e);
                    }
                    // 3. Last resort: the optimization list.
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception e2) {
                        Log.w(TAG, "Battery settings fallback failed", e2);
                    }
                })
                .show();
    }
}
