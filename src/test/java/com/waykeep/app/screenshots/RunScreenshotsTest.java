package com.waykeep.app.screenshots;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import app.cash.paparazzi.DeviceConfig;
import app.cash.paparazzi.Paparazzi;

import com.android.ide.common.rendering.api.SessionParams;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.SpeedFormatter;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.ui.trackmap.TrackThumbnailLoader;
import com.waykeep.app.ui.util.ListItemUtils;
import com.waykeep.app.util.StringUtils;

/**
 * Headless interface screenshots fed with the user's real run (test.kmz).
 * Same layouts, same production formatters, same thumbnail math as the app —
 * only the database is replaced by the KMZ file.
 */
public class RunScreenshotsTest {

    private static final UnitSystem UNIT = UnitSystem.METRIC;

    @Rule
    public Paparazzi paparazzi = new Paparazzi();

    @Test
    public void feedRow() throws Exception {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5, "DayNightDynamicTheme", SessionParams.RenderingMode.NORMAL);
        Context context = paparazzi.getContext();
        KmzRun run = KmzRun.load();

        Distance distance = Distance.of(run.distance_m());
        Duration duration = run.duration();

        View row = paparazzi.inflate(R.layout.track_list_item);

        ((ImageView) row.findViewById(R.id.track_list_item_icon)).setImageResource(R.drawable.ic_activity_run_24dp);
        ((ImageView) row.findViewById(R.id.track_list_item_thumbnail)).setImageBitmap(routeBitmap(context, run));

        ((TextView) row.findViewById(R.id.track_list_item_name)).setText(run.name);
        ListItemUtils.setDateAndTime(context,
                row.findViewById(R.id.track_list_item_date),
                row.findViewById(R.id.track_list_item_time),
                run.startTime(), ZoneOffset.of("+05:30"));

        ((TextView) row.findViewById(R.id.track_list_item_distance))
                .setText(ListItemUtils.getDistanceText(context, UNIT, false, distance));
        ((TextView) row.findViewById(R.id.track_list_item_duration))
                .setText(ListItemUtils.getTimeText(context, false, duration));

        ((TextView) row.findViewById(R.id.track_list_item_pace_label)).setText(R.string.stats_pace);
        Speed average = Speed.of(distance, duration);
        ((TextView) row.findViewById(R.id.track_list_item_pace)).setText(SpeedFormatter.Builder()
                .setUnit(UNIT)
                .setReportSpeedOrPace(false)
                .build(context)
                .formatSpeed(average.isInvalid() ? Speed.ZERO : average));

        android.util.Pair<String, String> gain = StringUtils.getAltitudeParts(context, (float) run.gain_m(), UNIT);
        ((TextView) row.findViewById(R.id.track_list_item_elevation))
                .setText(context.getString(R.string.altitude_with_unit, gain.first, gain.second));

        row.findViewById(R.id.track_list_item_marker_chip).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.track_list_item_activity_type_localized)).setText(run.activityType);
        row.findViewById(R.id.track_list_item_description).setVisibility(View.GONE);

        paparazzi.snapshot(row);
    }

    @Test
    public void stoppedScreen() throws Exception {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5, "DayNightDynamicTheme", SessionParams.RenderingMode.NORMAL);
        Context context = paparazzi.getContext();
        KmzRun run = KmzRun.load();

        Distance distance = Distance.of(run.distance_m());
        Duration duration = run.duration();
        Speed average = Speed.of(distance, duration);
        if (average.isInvalid()) {
            average = Speed.ZERO;
        }

        View screen = paparazzi.inflate(R.layout.track_stopped);

        ((TextView) screen.findViewById(R.id.track_edit_name)).setText(run.name);
        ((TextView) screen.findViewById(R.id.track_edit_activity_type)).setText(run.activityType);
        ((ImageView) screen.findViewById(R.id.track_edit_activity_type_icon)).setImageResource(R.drawable.ic_activity_run_24dp);

        ((TextView) screen.findViewById(R.id.time)).setText(StringUtils.formatElapsedTime(duration));

        android.util.Pair<String, String> distParts = DistanceFormatter.Builder().setUnit(UNIT).build(context)
                .getDistanceParts(distance);
        ((TextView) screen.findViewById(R.id.distance)).setText(distParts.first);
        ((TextView) screen.findViewById(R.id.distance_unit)).setText(distParts.second);

        android.util.Pair<String, String> speedParts = SpeedFormatter.Builder()
                .setUnit(UNIT)
                .setReportSpeedOrPace(false)
                .build(context)
                .getSpeedParts(average);
        ((TextView) screen.findViewById(R.id.speed)).setText(speedParts.first);
        ((TextView) screen.findViewById(R.id.speed_unit)).setText(speedParts.second);

        paparazzi.snapshot(screen);
    }

    @Test
    public void statsWidget() throws Exception {        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5, "DayNightDynamicTheme", SessionParams.RenderingMode.NORMAL);
        Context context = paparazzi.getContext();
        KmzRun run = KmzRun.load();

        Distance distance = Distance.of(run.distance_m());
        String distText = DistanceFormatter.Builder().setUnit(UNIT).build(context).formatDistance(distance);
        String detail = context.getString(R.string.stats_widget_week_detail, 1,
                StringUtils.formatElapsedTime(run.duration()));

        View widget = paparazzi.inflate(R.layout.widget_stats);
        ((TextView) widget.findViewById(R.id.widget_week_distance)).setText(distText);
        ((TextView) widget.findViewById(R.id.widget_week_detail)).setText(detail);
        ((TextView) widget.findViewById(R.id.widget_month_line))
                .setText(context.getString(R.string.stats_widget_month_line, distText));

        // Screenshots are screen-sized; the launcher gives this layout a single row,
        // so frame it in a 4x1 cell. Content pixels are untouched production layout.
        android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
        frame.setBackgroundColor(0xFF101418);
        frame.setLayoutParams(new android.widget.FrameLayout.LayoutParams(1000, 180));
        frame.addView(widget, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        paparazzi.snapshot(frame);
    }

    /**
     * Same projection math as TrackThumbnailLoader.render: equirectangular fit with
     * longitude convergence correction, brand stroke, start dot.
     */
    private static Bitmap routeBitmap(Context context, KmzRun run) {
        List<double[]> points = run.locatedLatLon();
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (double[] p : points) {
            minLat = Math.min(minLat, p[0]);
            maxLat = Math.max(maxLat, p[0]);
            minLon = Math.min(minLon, p[1]);
            maxLon = Math.max(maxLon, p[1]);
        }

        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = context.getResources().getDisplayMetrics().widthPixels - Math.round(24 * density);
        int heightPx = TrackThumbnailLoader.heroHeightPx(context);
        int strokeWidthPx = Math.max(2, Math.round(3 * density));
        int paddingPx = Math.round(12 * density);
        int strokeColor = ContextCompat.getColor(context, R.color.opentracks);

        double lonScale = Math.cos(Math.toRadians((minLat + maxLat) / 2.0));
        double routeWidth = (maxLon - minLon) * lonScale;
        double routeHeight = maxLat - minLat;
        double scale = Math.min((widthPx - 2.0 * paddingPx) / Math.max(routeWidth, 1e-9),
                (heightPx - 2.0 * paddingPx) / Math.max(routeHeight, 1e-9));
        float offsetX = (float) ((widthPx - routeWidth * scale) / 2.0);
        float offsetY = (float) ((heightPx - routeHeight * scale) / 2.0);

        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
            float x = (float) (offsetX + (points.get(i)[1] - minLon) * lonScale * scale);
            float y = (float) (offsetY + (maxLat - points.get(i)[0]) * scale);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidthPx);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(strokeColor);
        canvas.drawPath(path, paint);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(strokeColor);
        float sx = (float) (offsetX + (points.get(0)[1] - minLon) * lonScale * scale);
        float sy = (float) (offsetY + (maxLat - points.get(0)[0]) * scale);
        canvas.drawCircle(sx, sy, strokeWidthPx, dot);
        return bitmap;
    }
}
