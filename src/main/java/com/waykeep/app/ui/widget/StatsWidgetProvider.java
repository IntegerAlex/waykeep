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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

import com.waykeep.app.R;
import com.waykeep.app.data.AggregatedStatistic;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.TrackSelection;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.ui.aggregatedStatistics.AggregatedStatisticsActivity;
import com.waykeep.app.util.IntentUtils;
import com.waykeep.app.util.StringUtils;

/**
 * Home-screen stats widget: this week's distance plus session count and moving time,
 * with this month's distance below. Tap opens the aggregated statistics screen.
 *
 * Information widget, not a control: no live tracking here (Strava's own widget shows
 * weekly progress only). Queries are single cheap SQL aggregates run off the main
 * thread; refresh is pushed after a track ends plus the platform 30-minute tick.
 */
public class StatsWidgetProvider extends AppWidgetProvider {

    private static final String TAG = StatsWidgetProvider.class.getSimpleName();

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

    /** Called after a track ends so totals never go stale until the next tick. */
    public static void requestRefresh(Context context) {
        new Thread(() -> updateAll(context.getApplicationContext())).start();
    }

    private static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StatsWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        RemoteViews views;
        try {
            views = buildViews(context);
        } catch (Exception e) {
            Log.w(TAG, "Could not load widget stats.", e);
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
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        Instant weekStart = LocalDate.now().with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1).atStartOfDay(zone).toInstant();
        Instant monthStart = YearMonth.now().atDay(1).atStartOfDay(zone).toInstant();

        Totals week = total(contentProviderUtils.getAggregatedStatisticsForTracks(new TrackSelection().addDateRange(weekStart, now)));
        Totals month = total(contentProviderUtils.getAggregatedStatisticsForTracks(new TrackSelection().addDateRange(monthStart, now)));

        DistanceFormatter formatter = DistanceFormatter.Builder().setUnit(PreferencesUtils.getUnitSystem()).build(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stats);
        views.setTextViewText(R.id.widget_week_distance, formatter.formatDistance(week.distance));
        views.setTextViewText(R.id.widget_week_detail, context.getString(R.string.stats_widget_week_detail,
                week.sessions, StringUtils.formatElapsedTime(week.movingTime)));
        views.setTextViewText(R.id.widget_month_line, context.getString(R.string.stats_widget_month_line, formatter.formatDistance(month.distance)));

        Intent intent = IntentUtils.newIntent(context, AggregatedStatisticsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        return views;
    }

    private static Totals total(List<AggregatedStatistic> stats) {
        Distance distance = Distance.ZERO;
        java.time.Duration movingTime = java.time.Duration.ZERO;
        int sessions = 0;
        for (AggregatedStatistic stat : stats) {
            distance = distance.plus(stat.totalDistance());
            movingTime = movingTime.plus(stat.totalMovingTime());
            sessions += stat.countTracks();
        }
        return new Totals(distance, movingTime, sessions);
    }

    private record Totals(Distance distance, java.time.Duration movingTime, int sessions) {
    }
}
