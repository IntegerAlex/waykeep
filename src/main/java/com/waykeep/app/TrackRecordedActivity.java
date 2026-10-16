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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;

import com.waykeep.app.chart.ChartFragment;
import com.waykeep.app.chart.TrackDataHubInterface;
import com.waykeep.app.data.TrackDataHub;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.TrackRecordedBinding;
import com.waykeep.app.fragments.StatisticsRecordedFragment;
import com.waykeep.app.services.RecordingStatus;
import com.waykeep.app.services.TrackRecordingService;
import com.waykeep.app.services.TrackRecordingServiceConnection;
import com.waykeep.app.settings.SettingsActivity;
import com.waykeep.app.share.ShareUtils;
import com.waykeep.app.share.ShareTrackImageActivity;
import com.waykeep.app.ui.aggregatedStatistics.ConfirmDeleteDialogFragment;
import com.waykeep.app.ui.intervals.IntervalsFragment;
import com.waykeep.app.ui.markers.MarkerListActivity;
import com.waykeep.app.ui.trackmap.TrackMapFragment;
import com.waykeep.app.util.IntentUtils;

/**
 * An activity to show the track detail, record a new track or resumes an existing one.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
//TODO Should not use TrackRecordingServiceConnection; only used to determine if there is NO current recording, to enable resume functionality.
public class TrackRecordedActivity extends AbstractTrackDeleteActivity implements ConfirmDeleteDialogFragment.ConfirmDeleteCaller, TrackDataHubInterface {

    private static final String TAG = TrackRecordedActivity.class.getSimpleName();

    public static final String EXTRA_TRACK_ID = "track_id";
    public static final String EXTRA_OPEN_MAP_TAB = "open_map_tab";

    private static final String CURRENT_TAB_TAG_KEY = "current_tab_tag_key";

    private static final int MAP_TAB_POSITION = 3;

    private TrackDataHub trackDataHub;

    private TrackRecordedBinding viewBinding;

    private Track.Id trackId;
    private RecordingStatus recordingStatus = TrackRecordingService.STATUS_DEFAULT;

    private TrackRecordingServiceConnection trackRecordingServiceConnection;

    private final TrackRecordingServiceConnection.Callback bindCallback = (service, unused) -> service.getRecordingStatusObservable()
            .observe(TrackRecordedActivity.this, this::onRecordingStatusChanged);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handleIntent(getIntent());

        trackDataHub = new TrackDataHub(this);

        CustomFragmentPagerAdapter pagerAdapter = new CustomFragmentPagerAdapter(this);
        viewBinding.trackDetailActivityViewPager.setAdapter(pagerAdapter);
        new TabLayoutMediator(viewBinding.trackDetailActivityTablayout, viewBinding.trackDetailActivityViewPager,
                (tab, position) -> tab.setText(pagerAdapter.getPageTitle(position))).attach();
        if (savedInstanceState != null) {
            viewBinding.trackDetailActivityViewPager.setCurrentItem(savedInstanceState.getInt(CURRENT_TAB_TAG_KEY));
        } else if (getIntent().getBooleanExtra(EXTRA_OPEN_MAP_TAB, false)) {
            viewBinding.trackDetailActivityViewPager.setCurrentItem(MAP_TAB_POSITION);
        }

        trackRecordingServiceConnection = new TrackRecordingServiceConnection(bindCallback);

        viewBinding.bottomAppBarLayout.bottomAppBar.replaceMenu(R.menu.track_detail);
        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);
    }

    @Override
    protected void onStart() {
        super.onStart();

        trackDataHub.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update UI
        this.invalidateOptionsMenu();

        if (trackId != null) {
            trackDataHub.loadTrack(trackId);
        }

        trackRecordingServiceConnection.bind(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        trackRecordingServiceConnection.unbind(this);
        trackDataHub.stop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_TAB_TAG_KEY, viewBinding.trackDetailActivityViewPager.getCurrentItem());
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = TrackRecordedBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.track_detail, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.track_detail_markers).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.findItem(R.id.track_detail_resume_track).setVisible(!recordingStatus.isRecording());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.track_detail_share) {
            String[] options = {
                    getString(R.string.share_image_title),
                    getString(R.string.share_file_title),
                    getString(R.string.share_ai_title)
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.menu_share)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            Intent imageIntent = IntentUtils.newIntent(this, ShareTrackImageActivity.class)
                                    .putExtra(ShareTrackImageActivity.EXTRA_TRACK_ID, trackId);
                            startActivity(imageIntent);
                        } else if (which == 1) {
                            Intent fileIntent = Intent.createChooser(ShareUtils.newShareFileIntent(this, trackId), null);
                            startActivity(fileIntent);
                        } else {
                            // Prompt via clipboard (receivers that prefer a text extra over the
                            // attachment would otherwise swallow the file); export runs off the
                            // main thread since it does database + file I/O.
                            String prompt = getString(R.string.share_ai_prompt);
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("ai_prompt", prompt));
                            }
                            Toast.makeText(this, R.string.share_ai_prompt_copied, Toast.LENGTH_LONG).show();
                            Toast.makeText(this, R.string.share_ai_preparing, Toast.LENGTH_SHORT).show();
                            new Thread(() -> {
                                java.io.File aiFile = ShareUtils.writeAiShareFile(this, trackId);
                                runOnUiThread(() -> {
                                    if (isFinishing()) {
                                        return;
                                    }
                                    if (aiFile == null) {
                                        Toast.makeText(this, R.string.share_ai_failed, Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    android.net.Uri uri = com.waykeep.app.share.ShareImageStore.uri(this, aiFile);
                                    startActivity(Intent.createChooser(ShareUtils.newShareAiIntent(this, uri), null));
                                });
                            }).start();
                        }
                    })
                    .show();
            return true;
        }

        if (item.getItemId() == R.id.track_detail_menu_show_on_map) {
            viewBinding.trackDetailActivityViewPager.setCurrentItem(MAP_TAB_POSITION);
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

        if (item.getItemId() == R.id.track_detail_delete) {
            deleteTracks(trackId);
            return true;
        }

        if (item.getItemId() == R.id.track_detail_resume_track) {
            TrackRecordingServiceConnection.executeForeground(this, (service, connection) -> {
                service.resumeTrack(trackId);

                Intent newIntent = IntentUtils.newIntent(TrackRecordedActivity.this, TrackRecordingActivity.class)
                        .putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
                startActivity(newIntent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

                finish();
            });
            return true;
        }

        if (item.getItemId() == R.id.track_detail_settings) {
            startActivity(IntentUtils.newIntent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    protected Track.Id getRecordingTrackId() {
        return recordingStatus.trackId();
    }

    @Override
    protected void onDeleteConfirmed() {
        runOnUiThread(this::finish);
    }

    public void onDeleteFinished() {
        runOnUiThread(this::finish);
    }

    /**
     * Gets the {@link TrackDataHub}.
     */
    @Override
    public TrackDataHub getTrackDataHub() {
        return trackDataHub;
    }

    private void handleIntent(Intent intent) {
        trackId = intent.getParcelableExtra(EXTRA_TRACK_ID);
        if (trackId == null) {
            Log.e(TAG, TrackRecordedActivity.class.getSimpleName() + " needs EXTRA_TRACK_ID.");
            finish();
        }
    }

    private class CustomFragmentPagerAdapter extends FragmentStateAdapter {

        public CustomFragmentPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> StatisticsRecordedFragment.newInstance(trackId);
                case 1 -> IntervalsFragment.newInstance(trackId, true);
                case 2 -> ChartFragment.newInstance();
                case 3 -> TrackMapFragment.newInstance();
                default ->
                        throw new RuntimeException("There isn't Fragment associated with the position: " + position);
            };
        }

        @Override
        public int getItemCount() {
            return 4;
        }

        public CharSequence getPageTitle(int position) {
            return switch (position) {
                case 0 -> getString(R.string.track_detail_stats_tab);
                case 1 -> getString(R.string.track_detail_intervals_tab);
                case 2 -> getString(R.string.track_detail_chart_tab);
                case 3 -> getString(R.string.track_detail_map_tab);
                default ->
                        throw new RuntimeException("There isn't Fragment associated with the position: " + position);
            };
        }
    }

    private void onRecordingStatusChanged(RecordingStatus status) {
        recordingStatus = status;
    }
}