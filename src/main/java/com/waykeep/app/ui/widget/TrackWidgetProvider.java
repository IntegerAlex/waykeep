package com.waykeep.app.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.waykeep.app.R;
import com.waykeep.app.TrackListActivity;
import com.waykeep.app.TrackRecordingActivity;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.services.TrackIdStorage;
import com.waykeep.app.util.IntentUtils;

/**
 * Home-screen widget: state display plus deep link, mirroring the competitor pattern
 * (Strava/Garmin ship no launcher transport controls; tap returns to the app).
 *
 * <ul>
 *   <li>Not recording: "Start run" opens the feed where the FAB starts a run
 *   (keeps the permission and battery-exemption prompts on the correct path).</li>
 *   <li>Recording: "Open run" jumps straight into the recorder, where marker and
 *   photo capture live one tap away.</li>
 * </ul>
 *
 * No live stats and no polling: refreshes only on recording start/stop (pushed from
 * {@code TrackRecordingService}) and on provider updates. updatePeriodMillis is 0.
 */
public class TrackWidgetProvider extends AppWidgetProvider {

    private static final String TAG = TrackWidgetProvider.class.getSimpleName();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAll(context);
    }

    /** Called by TrackRecordingService after start/end so the widget never goes stale. */
    public static void requestRefresh(Context context) {
        updateAll(context.getApplicationContext());
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TrackWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        Track.Id trackId = null;
        try {
            trackId = TrackIdStorage.get(context);
        } catch (Exception e) {
            Log.w(TAG, "Could not read recording state.", e);
        }
        RemoteViews views = buildViews(context, trackId);
        for (int id : ids) {
            try {
                manager.updateAppWidget(id, views);
            } catch (Exception e) {
                Log.w(TAG, "Could not update widget.", e);
            }
        }
    }

    private static RemoteViews buildViews(Context context, Track.Id trackId) {
        boolean recording = trackId != null;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_track);
        views.setTextViewText(R.id.widget_status, context.getString(recording ? R.string.widget_recording : R.string.widget_ready));
        views.setTextViewText(R.id.widget_action, context.getString(recording ? R.string.widget_open : R.string.widget_start));

        Intent intent;
        if (recording) {
            intent = IntentUtils.newIntent(context, TrackRecordingActivity.class)
                    .putExtra(TrackRecordingActivity.EXTRA_TRACK_ID, trackId);
        } else {
            intent = IntentUtils.newIntent(context, TrackListActivity.class);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, recording ? 1 : 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_action, pendingIntent);
        return views;
    }
}
