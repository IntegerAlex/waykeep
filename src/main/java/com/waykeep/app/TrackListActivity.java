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
import android.graphics.drawable.AnimatedVectorDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.TrackListBinding;
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
import com.waykeep.app.ui.personalBests.PersonalBestsActivity;
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

    // The following are set in onCreate
    private TrackRecordingServiceConnection recordingStatusConnection;
    private TrackListAdapter adapter;
    private final ExecutorService trackLoader = Executors.newSingleThreadExecutor();

    private TrackListBinding viewBinding;

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
        }

        requestRequiredPermissions();

        recordingStatusConnection = new TrackRecordingServiceConnection(bindChangedCallback);

        viewBinding.aggregatedStatsButton.setOnClickListener((view) -> startActivity(IntentUtils.newIntent(this, AggregatedStatisticsActivity.class)));
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
        viewBinding.trackListEmptyAction.setOnClickListener(view -> viewBinding.trackListFabAction.performClick());

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(new RecyclerViewSwipeDeleteCallback(this) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                TrackListActivity.this.deleteTracks(new Track.Id(viewHolder.getItemId()));
            }
        });
        itemTouchhelper.attachToRecyclerView(viewBinding.trackList);
        viewBinding.trackListFabAction.setOnClickListener((view) -> {
            if (recordingStatus.isRecording()) {
                Toast.makeText(TrackListActivity.this, getString(R.string.hold_to_stop), Toast.LENGTH_LONG).show();
                return;
            }

            // Not Recording -> Recording
            Log.i(TAG, "Starting recording");
            updateGpsMenuItem(false, true);
            startSensorsOrRecording((service, connection) -> {
                Track.Id trackId = service.startNewTrack();

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
            ActivityUtils.vibrate(this, Duration.ofSeconds(1));
            updateGpsMenuItem(false, false);
            recordingStatusConnection.stopRecording(TrackListActivity.this);
            viewBinding.trackListFabAction.setImageResource(R.drawable.ic_baseline_record_24);
            viewBinding.trackListFabAction.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red_dark));
            return true;
        });

        setSupportActionBar(viewBinding.trackListToolbar);
        adapter.setActionModeCallback(contextualActionModeCallback);
    }

    private void requestRequiredPermissions() {
        PermissionRequester.ALL.requestPermissionsIfNeeded(this, this, null, (requester) -> Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onStart() {
        super.onStart();

        PreferencesUtils.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        recordingStatusConnection.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update UI
        this.invalidateOptionsMenu();
        loadData();

        // Float button
        setFloatButton();
    }

    @Override
    protected void onStop() {
        super.onStop();

        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        recordingStatusConnection.unbind(this);
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
                pendingSearch = () -> {
                    searchQuery = text;
                    loadData();
                };
                searchHandler.postDelayed(pendingSearch, 300);
            }
        });

        return viewBinding.getRoot();
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

        if (item.getItemId() == R.id.track_list_personal_bests) {
            startActivity(IntentUtils.newIntent(this, PersonalBestsActivity.class));
            return true;
        }

        if (item.getItemId() == R.id.track_list_monthly_recap) {
            startActivity(IntentUtils.newIntent(this, MonthlyRecapActivity.class));
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
                }
            });
        });
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
        viewBinding.trackListFabAction.setImageResource(recordingStatus.isRecording() ? R.drawable.ic_baseline_stop_24 : R.drawable.ic_baseline_record_24);
        viewBinding.trackListFabAction.setBackgroundTintList(ContextCompat.getColorStateList(this, recordingStatus.isRecording() ? R.color.opentracks : R.color.red_dark));
    }

    private void onRecordingStatusChanged(RecordingStatus status) {
        recordingStatus = status;
        setFloatButton();
        adapter.updateRecordingStatus(recordingStatus);
    }

    private void startSensorsOrRecording(TrackRecordingServiceConnection.Callback callback) {
        try {
            TrackRecordingServiceConnection.executeForeground(this, callback);
        } catch (MissingPermissionException e) {
            Toast.makeText(this, R.string.permission_recording_failed, Toast.LENGTH_LONG).show();
        }
    }
}
