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

package com.waykeep.app.services;

import android.app.Service;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Map;

import com.waykeep.app.BuildConfig;
import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Marker;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.data.models.TrackPoint;
import com.waykeep.app.diagnostics.RecordingDiagnosticEvents;
import com.waykeep.app.diagnostics.RecordingDiagnostics;
import com.waykeep.app.sensors.GpsStatusValue;
import com.waykeep.app.services.announcement.VoiceAnnouncementManager;
import com.waykeep.app.services.handlers.TrackPointCreator;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.ui.widget.StatsWidgetProvider;
import com.waykeep.app.util.StringUtils;

public class TrackRecordingService extends Service implements TrackPointCreator.Callback, SharedPreferences.OnSharedPreferenceChangeListener, TrackRecordingManager.IdleObserver {

    private static final String TAG = TrackRecordingService.class.getSimpleName();

    @VisibleForTesting
    public static final Duration RECORDING_DATA_UPDATE_INTERVAL = Duration.ofSeconds(1);

    public static final RecordingStatus STATUS_DEFAULT = RecordingStatus.notRecording();
    public static final GpsStatusValue STATUS_GPS_DEFAULT = GpsStatusValue.GPS_NONE;

    public static final String ACTION_PAUSE = "com.waykeep.app.services.TRACK_RECORDING_PAUSE";
    public static final String ACTION_RESUME = "com.waykeep.app.services.TRACK_RECORDING_RESUME";
    public static final String ACTION_STOP = "com.waykeep.app.services.TRACK_RECORDING_STOP";

    public TrackPoint getLastStoredTrackPointWithLocation() {
        return trackRecordingManager.getLastStoredTrackPointWithLocation();
    }

    public class Binder extends android.os.Binder {

        private Binder() {
            super();
        }

        public TrackRecordingService getService() {
            return TrackRecordingService.this;
        }
    }

    private final Binder binder = new Binder();

    private final Runnable updateRecordingData = new Runnable() {
        @Override
        public void run() {
            updateRecordingDataWhileRecording();

            TrackRecordingService.this.handler.postDelayed(this, RECORDING_DATA_UPDATE_INTERVAL.toMillis());
        }
    };

    // The following variables are set in onCreate:
    private RecordingStatus recordingStatus;
    private MutableLiveData<RecordingStatus> recordingStatusObservable;
    private MutableLiveData<GpsStatusValue> gpsStatusObservable;
    private MutableLiveData<String> gpsStatusTextObservable;
    private MutableLiveData<String> gpsAccuracyTextObservable;
    private MutableLiveData<Integer> lowBatteryPercentObservable;
    private MutableLiveData<RecordingData> recordingDataObservable;

    private Handler handler;
    private boolean batteryReceiverRegistered;

    private final BroadcastReceiver batteryChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLowBatteryWarning(intent);
        }
    };

    private TrackPointCreator trackPointCreator;
    private TrackRecordingManager trackRecordingManager;
    private RecordingDiagnostics diagnostics;
    private boolean hasReceivedGpsFix;
    /** When acquisition started, so the status can show how long the receiver has been waiting. */
    private long gpsAcquiringSinceMs;
    private GpsStatusValue lastGpsStatus = STATUS_GPS_DEFAULT;
    private String lastGpsStatusText;
    private int satellitesVisible = -1;
    private int satellitesUsedInFix = -1;

    private final Runnable deviceStateSnapshot = new Runnable() {
        @Override
        public void run() {
            if (!isRecording()) return;
            recordDeviceState();
            handler.postDelayed(this, Duration.ofMinutes(1).toMillis());
        }
    };

    private VoiceAnnouncementManager voiceAnnouncementManager;
    private TrackRecordingServiceNotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Create");

        handler = new Handler(Looper.getMainLooper());

        recordingStatusObservable = new MutableLiveData<>();
        updateRecordingStatus(STATUS_DEFAULT);
        gpsStatusObservable = new MutableLiveData<>(STATUS_GPS_DEFAULT);
        gpsStatusTextObservable = new MutableLiveData<>(getString(STATUS_GPS_DEFAULT.message));
        gpsAccuracyTextObservable = new MutableLiveData<>();
        lowBatteryPercentObservable = new MutableLiveData<>(-1);
        recordingDataObservable = new MutableLiveData<>(RecordingData.NOT_RECORDING);

        trackPointCreator = new TrackPointCreator(this);
        diagnostics = new RecordingDiagnostics(this);
        trackRecordingManager = new TrackRecordingManager(this, trackPointCreator, this, handler, diagnostics);

        voiceAnnouncementManager = new VoiceAnnouncementManager(this);
        notificationManager = new TrackRecordingServiceNotificationManager(this);

        PreferencesUtils.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying");
        if (diagnostics != null && isRecording()) {
            diagnostics.event("service_destroyed", Map.of("track_id", recordingStatus.trackId().id()));
        }
        stopBatteryMonitoring();
        if (diagnostics != null) diagnostics.close();
        if (trackPointCreator != null && trackPointCreator.isStarted()) {
            stopSensors();
        }

        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(this);

        trackPointCreator = null;

        handler.removeCallbacksAndMessages(null); //Some tests do not finish the recording completely
        handler = null;

        trackRecordingManager = null;

        // Reverse order from onCreate
        notificationManager = null;

        voiceAnnouncementManager = null;

        recordingStatusObservable = null;
        gpsStatusObservable = null;
        recordingDataObservable = null;
        gpsAccuracyTextObservable = null;
        lowBatteryPercentObservable = null;

        Log.d(TAG, "Destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PAUSE -> pauseCurrentTrack();
                case ACTION_RESUME -> resumeCurrentTrack();
                case ACTION_STOP -> endCurrentTrack();
                default -> Log.w(TAG, "Unknown action: " + intent.getAction());
            }
            return START_STICKY;
        }
        if (intent == null) {
            Log.i(TAG, "Service got killed and is restarting");

            //Best guess: do we need to resume a recording?
            Track.Id trackId = TrackIdStorage.get(this);
            if (trackId != null) {
                Log.i(TAG, "After restart: resuming recording for " + trackId);
                resumeTrack(trackId, true);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app from recents must not end the run. With stopWithTask=false
        // the service stays; re-assert foreground so low-RAM devices and aggressive
        // OEM task killers keep the GPS notification (and location) alive.
        // If the process still dies, START_STICKY + TrackIdStorage resume above.
        // Ref upstream #2333 / #2334.
        if (isRecording()) {
            Log.i(TAG, "Task removed while recording; staying foreground.");
            try {
                tryStartSensors();
            } catch (Exception e) {
                Log.w(TAG, "Could not re-assert foreground after task removal.", e);
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public Binder onBind(Intent intent) {
        return binder;
    }

    public Track.Id startNewTrack() {
        if (isRecording()) {
            Log.w(TAG, "Ignore startNewTrack. Already recording.");
            return null;
        }
        Log.i(TAG, "startNewTrack");
        hasReceivedGpsFix = false;
        gpsAcquiringSinceMs = SystemClock.elapsedRealtime();

        // Set recording status
        Track.Id trackId = trackRecordingManager.startNewTrack();
        startRecording(trackId, false);
        return trackId;
    }

    public void resumeTrack(Track.Id trackId) {
        resumeTrack(trackId, false);
    }

    private void resumeTrack(Track.Id trackId, boolean serviceRestart) {
        hasReceivedGpsFix = false;
        gpsAcquiringSinceMs = SystemClock.elapsedRealtime();
        if (!trackRecordingManager.resumeExistingTrack(trackId, serviceRestart, false)) {
            Log.w(TAG, "Cannot resume a non-existing track.");
            return;
        }
        Log.i(TAG, "resumeTrack");

        startRecording(trackId, serviceRestart);
    }

    /**
     * Pauses the recording: the segment is closed so time/distance freeze, but the
     * sensors keep running so resume is instant (no GPS re-acquire wait).
     */
    public void pauseCurrentTrack() {
        if (!isRecordingAndNotPaused()) {
            Log.w(TAG, "Ignore pauseCurrentTrack. Not recording or already paused.");
            return;
        }
        Log.i(TAG, "pauseCurrentTrack");
        diagnostics.event("recording_paused", Map.of("track_id", recordingStatus.trackId().id()));

        trackRecordingManager.pauseCurrentTrack();

        // Settle the UI on the pause point (the periodic update skips paused
        // state, so post the frozen stats directly without announcing).
        recordingDataObservable.postValue(trackRecordingManager.getDataForUI());
        updateRecordingStatus(recordingStatus.pause());

        stopUpdateRecordingData();

        voiceAnnouncementManager.stop();

        notificationManager.onRecordingPaused(this, recordingStatus.trackId());
    }

    /**
     * Resumes a paused recording: a new segment is opened, stats continue from the
     * pause point. Sensors were never stopped, so there is no re-acquire delay.
     */
    public void resumeCurrentTrack() {
        if (!isPaused()) {
            Log.w(TAG, "Ignore resumeCurrentTrack. Not paused.");
            return;
        }
        Track.Id trackId = recordingStatus.trackId();
        Log.i(TAG, "resumeCurrentTrack");

        if (!trackRecordingManager.resumeExistingTrack(trackId, false, true)) {
            Log.w(TAG, "Cannot resume a non-existing track.");
            return;
        }

        startRecording(trackId, false);
    }

    private void startRecording(Track.Id trackId, boolean serviceRestart) {
        updateRecordingStatus(RecordingStatus.record(trackId));
        TrackIdStorage.set(this, trackId);
        startBatteryMonitoring();

        diagnostics.event("recording_started", Map.of(
                "track_id", trackId.id(),
                "service_restart", serviceRestart));
        recordDeviceState();
        handler.removeCallbacks(deviceStateSnapshot);
        handler.postDelayed(deviceStateSnapshot, Duration.ofMinutes(1).toMillis());

        startSensors();

        voiceAnnouncementManager.start(trackRecordingManager.getTrackStatistics());

        notificationManager.onRecordingStarted(this, recordingStatus.trackId());

        // Update instance variables
        updateRecordingData.run();
        handler.postDelayed(updateRecordingData, RECORDING_DATA_UPDATE_INTERVAL.toMillis());
    }

    public void tryStartSensors() {
        if (trackPointCreator.isStarted()) return;

        Log.i(TAG, "tryStartSensors");

        startSensors();
    }

    private synchronized void startSensors() {
        if (trackPointCreator.isStarted()) {
            Log.i(TAG, "sensors already started; skipping");
            return;
        }
        Log.i(TAG, "startSensors");
        trackPointCreator.start(this, handler);

        ServiceCompat.startForeground(this, TrackRecordingServiceNotificationManager.NOTIFICATION_ID, notificationManager.onSensorsStarted(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION + ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    }

    public void endCurrentTrack() {
        if (!isRecording()) {
            Log.w(TAG, "Ignore endCurrentTrack. Not recording.");
            return;
        }

        boolean wasPaused = recordingStatus.isPaused();
        Track.Id stoppedTrackId = recordingStatus.trackId();

        // Set recording status
        updateRecordingStatus(STATUS_DEFAULT);

        if (wasPaused) {
            // Segment end was already inserted on pause.
            trackRecordingManager.endCurrentTrackAfterPause();
        } else {
            trackRecordingManager.endCurrentTrack();
        }
        diagnostics.event("recording_stopped", Map.of("track_id", stoppedTrackId.id()));
        diagnostics.finish(wasPaused ? "stopped_after_pause" : "stopped");
        TrackIdStorage.unset(this);
        stopBatteryMonitoring();

        stopUpdateRecordingData();
        handler.removeCallbacks(deviceStateSnapshot);

        voiceAnnouncementManager.stop();

        stopSensors();

        StatsWidgetProvider.requestRefresh(this);
    }

    void stopSensors() {
        trackPointCreator.stop();
        stopForeground(true);
        notificationManager.stop();
        lastGpsStatus = STATUS_GPS_DEFAULT;
        gpsStatusObservable.postValue(STATUS_GPS_DEFAULT);
        gpsAccuracyTextObservable.postValue(null);
    }

    public Marker.Id createMarker() {
        if (!isRecording()) {
            return null;
        }

        //TODO This contains some duplication to TrackRecodingActivity's Marker creation
        TrackPoint trackPoint = trackRecordingManager.getLastStoredTrackPointWithLocation();
        if (trackPoint == null) {
            return null;
        }
        Marker marker = new Marker(
                null,
                recordingStatus.trackId(),
                "",
                "",
                "",
                trackPoint.position(),
                null
        );
        try {
            Marker.Id markerId = new ContentProviderUtils(this).insertMarker(marker);
            diagnostics.event("marker_created", Map.of("marker_id", markerId.id(), "track_id", recordingStatus.trackId().id()));
            return markerId;
        } catch (RuntimeException e) {
            diagnostics.event("marker_write_failed", Map.of(
                    "track_id", recordingStatus.trackId().id(),
                    "error_class", e.getClass().getSimpleName()));
            throw e;
        }
    }

    @Override
    public boolean newTrackPoint(TrackPoint trackPoint, Distance thresholdHorizontalAccuracy) {
        if (!isRecordingAndNotPaused()) {
            return false;
        }

        boolean stored = trackRecordingManager.onNewTrackPoint(trackPoint);
        if (lastGpsStatus.hasGoodSignal() && trackPoint.position().hasLocation()) {
            gpsAccuracyTextObservable.postValue(RecordingLiveStatus.formatAccuracy(
                    trackPoint.position().horizontalAccuracy(), getString(R.string.gps_accuracy_glance)));
        }
        //TODO Should be done during updateRecordingDataWhileRecording()
        notificationManager.updateTrackPoint(this, trackRecordingManager.getDataForUI(), thresholdHorizontalAccuracy);
        return stored;
    }

    @Override
    public void newGpsStatus(GpsStatusValue gpsStatusValue) {
        Log.i(TAG, "newGpsStatus: " + gpsStatusValue.message);
        diagnostics.event("gps_status", RecordingDiagnosticEvents.gpsStatus(
                gpsStatusValue.name(), hasReceivedGpsFix));

        TrackRecordingServiceNotificationManager nm = notificationManager;
        MutableLiveData<GpsStatusValue> obs = gpsStatusObservable;
        if (nm == null || obs == null) {
            StringWriter writer = new StringWriter();
            Exception e = new RuntimeException("TrackRecording.newGpsStatus() called after onDestroy(); objectID: " + this + " with thread: " + Thread.currentThread());
            e.printStackTrace(new PrintWriter(writer));
            Log.e(TAG, e.getMessage() + " " + writer);
            return;
        }
        // The accuracy belongs to the last accepted position: it is only meaningful while
        // the signal is good. Otherwise it is stale and hides the real problem.
        lastGpsStatus = gpsStatusValue;
        if (!gpsStatusValue.hasGoodSignal()) {
            gpsAccuracyTextObservable.postValue(null);
        }
        obs.postValue(gpsStatusValue);
        refreshGpsStatusText();
    }

    @Override
    public void onGnssStatus(int satellitesVisible, int satellitesUsedInFix, Double maxSnrDb, long sinceConnectMs) {
        diagnostics.event("gnss_status", RecordingDiagnosticEvents.gnssStatus(
                satellitesVisible, satellitesUsedInFix, maxSnrDb, sinceConnectMs));

        this.satellitesVisible = satellitesVisible;
        this.satellitesUsedInFix = satellitesUsedInFix;
        if (lastGpsStatus.isGpsStarted()) {
            refreshGpsStatusText();
        }
    }

    @Override
    public void onGnssFirstFix(int ttffMs) {
        diagnostics.event("gnss_first_fix", RecordingDiagnosticEvents.gnssFirstFix(ttffMs));
    }

    @Override
    public void onGnssAssistance(String command, boolean accepted, String errorClass) {
        diagnostics.event("gnss_assistance", RecordingDiagnosticEvents.gnssAssistance(command, accepted, errorClass));
    }

    private void refreshGpsStatusText() {
        String text = gpsStatusText(lastGpsStatus);
        if (text.equals(lastGpsStatusText)) {
            return;
        }
        lastGpsStatusText = text;

        TrackRecordingServiceNotificationManager nm = notificationManager;
        if (nm != null) {
            nm.updateGPSStatus(text, !lastGpsStatus.hasGoodSignal());
        }
        if (gpsStatusTextObservable != null) {
            gpsStatusTextObservable.postValue(text);
        }
    }

    /**
     * Say what is actually true while the GPS has no fix: whether the app already has coordinates
     * (a coarse network position) or how many satellites the receiver sees. "Waiting for GPS"
     * alone cannot tell a blind receiver from a slow one.
     */
    private String gpsStatusText(GpsStatusValue value) {
        if (!hasReceivedGpsFix && value.isGpsStarted() && !value.hasGoodSignal()) {
            String text;
            Distance provisionalAccuracy = trackRecordingManager == null ? null : trackRecordingManager.provisionalStartAccuracy();
            if (provisionalAccuracy != null) {
                text = getString(R.string.gps_approximate_location, Math.round(provisionalAccuracy.toM()));
            } else if (satellitesVisible >= 0) {
                text = getString(R.string.gps_searching_satellites, satellitesUsedInFix, satellitesVisible);
            } else {
                text = getString(value.message);
            }
            // The distance cannot move until a fix arrives. A ticking clock next to it is the
            // difference between "waiting" and "frozen", which is what it looks like otherwise.
            return getString(R.string.gps_status_acquiring_for, text, acquiringElapsed());
        }
        return getString(value.message);
    }

    private String acquiringElapsed() {
        long elapsedMs = gpsAcquiringSinceMs == 0 ? 0 : SystemClock.elapsedRealtime() - gpsAcquiringSinceMs;
        return StringUtils.formatElapsedTime(Duration.ofMillis(elapsedMs));
    }

    @Override
    public void onRawGpsFix(Position position, String provider, long providerTimeMillis,
                            long elapsedRealtimeNanos, Distance threshold, String decision) {
        hasReceivedGpsFix = true;
        diagnostics.event("gps_fix", RecordingDiagnosticEvents.gpsFix(
                position, providerTimeMillis, provider, elapsedRealtimeNanos, threshold, decision));
    }

    private void recordDeviceState() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPercent = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int batteryStatus = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = plugged != 0 || batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
        updateLowBatteryWarning(battery);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean powerSave = powerManager != null && powerManager.isPowerSaveMode();
        boolean idle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null && powerManager.isDeviceIdleMode();
        boolean interactive = powerManager != null && powerManager.isInteractive();

        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        boolean connected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        String transport = capabilities == null ? "none"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet"
                : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ? "bluetooth" : "other";

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean fineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean gpsEnabled = false;
        if (fineLocation && locationManager != null) {
            try {
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (SecurityException ignored) {
            }
        }
        boolean backgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        Map<String, Object> state = RecordingDiagnosticEvents.deviceState(
                batteryPercent, charging, powerSave, idle, interactive, transport, connected,
                fineLocation, backgroundLocation, gpsEnabled, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT);
        state.put("app_version", BuildConfig.VERSION_NAME);
        state.put("build_type", BuildConfig.BUILD_TYPE);
        state.put("version_code", BuildConfig.VERSION_CODE);
        diagnostics.event("device_state", state);
    }

    @Deprecated
    @VisibleForTesting
    public TrackPointCreator getTrackPointCreator() {
        return trackPointCreator;
    }

    @Deprecated
    @VisibleForTesting
    public TrackRecordingManager getTrackRecordingManager() {
        return trackRecordingManager;
    }

    public LiveData<GpsStatusValue> getGpsStatusObservable() {
        return gpsStatusObservable;
    }

    public LiveData<String> getGpsStatusTextObservable() {
        return gpsStatusTextObservable;
    }

    public LiveData<String> getGpsAccuracyTextObservable() {
        return gpsAccuracyTextObservable;
    }

    public LiveData<Integer> getLowBatteryPercentObservable() {
        return lowBatteryPercentObservable;
    }

    public LiveData<RecordingData> getRecordingDataObservable() {
        return recordingDataObservable;
    }

    private void updateRecordingDataWhileRecording() {
        if (!isRecordingAndNotPaused()) {
            return;
        }

        // The provisional network start arrives asynchronously; pick it up for the status text.
        refreshGpsStatusText();

        // Compute temporary track statistics using sensorData and update time.
        RecordingData data = trackRecordingManager.getDataForUI();

        voiceAnnouncementManager.announceStatisticsIfNeeded(data);

        recordingDataObservable.postValue(data);
    }

    public void onIdle() {
        voiceAnnouncementManager.announceIdle();
    }

    @VisibleForTesting
    public void stopUpdateRecordingData() {
        handler.removeCallbacks(updateRecordingData);
    }

    public LiveData<RecordingStatus> getRecordingStatusObservable() {
        return recordingStatusObservable;
    }

    private void updateRecordingStatus(RecordingStatus status) {
        Log.i(TAG, "new status " + recordingStatus + " -> " + status);
        recordingStatus = status;
        recordingStatusObservable.postValue(recordingStatus);
    }

    private void startBatteryMonitoring() {
        if (batteryReceiverRegistered) return;
        Intent battery = ContextCompat.registerReceiver(this, batteryChangedReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED);
        batteryReceiverRegistered = true;
        updateLowBatteryWarning(battery);
    }

    private void stopBatteryMonitoring() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryChangedReceiver);
            batteryReceiverRegistered = false;
        }
        if (lowBatteryPercentObservable != null) {
            lowBatteryPercentObservable.postValue(-1);
        }
    }

    private void updateLowBatteryWarning(@Nullable Intent battery) {
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int warningPercent = isRecording()
                ? RecordingLiveStatus.warningBatteryPercent(level, scale, charging)
                : -1;
        if (lowBatteryPercentObservable != null) {
            lowBatteryPercentObservable.postValue(warningPercent);
        }
    }

    @Deprecated //TODO Should be @VisibleForTesting
    public boolean isRecording() {
        return recordingStatus.isRecording();
    }

    public boolean isPaused() {
        return recordingStatus.isPaused();
    }

    public boolean isRecordingAndNotPaused() {
        return recordingStatus.isRecordingAndNotPaused();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        voiceAnnouncementManager.onSharedPreferenceChanged(sharedPreferences, key);
        trackRecordingManager.onSharedPreferenceChanged(sharedPreferences, key);
        trackPointCreator.onSharedPreferenceChanged(sharedPreferences, key);
        notificationManager.onSharedPreferenceChanged(sharedPreferences, key);
    }
}
