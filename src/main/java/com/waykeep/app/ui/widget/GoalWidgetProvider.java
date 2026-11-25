package com.waykeep.app.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.TrackListActivity;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackListIterator;
import com.waykeep.app.data.insights.InsightSummary;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.util.IntentUtils;

/**
 * Home-screen goal widget: the rolling seven-day distance, progress toward the weekly goal and
 * the consecutive-week streak. Values come from the same {@link InsightSummary} the home hero
 * uses, so widget and app can never disagree about what "this week" means.
 *
 * <p>Information widget: no live tracking. Queries run off the main thread; refresh is pushed
 * after a track ends, when the goal changes, plus the platform 30-minute tick.</p>
 */
public class GoalWidgetProvider extends AppWidgetProvider {

    private static final String TAG = GoalWidgetProvider.class.getSimpleName();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        BroadcastReceiver.PendingResult result = goAsync();
        new Thread(() -> {
            try {
                updateAll(context.getApplicationContext());
            } finally {
                result.finish();
            }
        }).start();
    }

    /** Called after a track ends or the goal changes so the widget never waits for the tick. */
    public static void requestRefresh(Context context) {
        new Thread(() -> updateAll(context.getApplicationContext())).start();
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, GoalWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        RemoteViews views;
        try {
            views = buildViews(context);
        } catch (Exception e) {
            Log.w(TAG, "Could not load goal widget.", e);
            return;
        }
        for (int id : ids) {
            try {
                manager.updateAppWidget(id, views);
            } catch (Exception e) {
                Log.w(TAG, "Could not update widget.", e);
            }
        }
    }

    private static RemoteViews buildViews(Context context) {
        InsightSummary summary = insightSummary(context);
        DistanceFormatter formatter = DistanceFormatter.Builder()
                .setUnit(PreferencesUtils.getUnitSystem())
                .setDecimalPlaces(1)
                .build(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_goal);
        views.setTextViewText(R.id.widget_goal_distance, formatter.formatDistance(summary.goalProgressDistance()));

        int streakWeeks = summary.weeklyStreak().consecutiveWeeks();
        views.setTextViewText(R.id.widget_goal_streak, streakWeeks > 0
                ? context.getResources().getQuantityString(R.plurals.insight_streak_weeks_active, streakWeeks, streakWeeks)
                : context.getString(summary.streakLineRes()));

        if (summary.hasGoal()) {
            views.setViewVisibility(R.id.widget_goal_bar, android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widget_goal_percent, android.view.View.VISIBLE);
            views.setProgressBar(R.id.widget_goal_bar, 100, summary.goalPercent(), false);
            views.setTextViewText(R.id.widget_goal_percent, context.getString(R.string.insight_goal_percent,
                    summary.goalPercent(),
                    formatter.formatDistance(summary.weeklyGoal())));
        } else {
            views.setViewVisibility(R.id.widget_goal_bar, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_goal_percent, android.view.View.VISIBLE);
            views.setTextViewText(R.id.widget_goal_percent, context.getString(R.string.insight_average_compact,
                    formatter.formatDistance(summary.fourWeekWeeklyAverage())));
        }

        Intent intent = IntentUtils.newIntent(context, TrackListActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        return views;
    }

    private static InsightSummary insightSummary(Context context) {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        List<TrackListIterator.Item> tracks = new ArrayList<>();
        try (TrackListIterator iterator = contentProviderUtils.searchTracks(null)) {
            while (iterator.hasNext()) {
                tracks.add(iterator.next());
            }
        }
        return InsightSummary.compute(tracks, Instant.now(), ZoneId.systemDefault(),
                PreferencesUtils.getWeeklyGoalDistance());
    }
}
