package de.dennisguse.opentracks.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.DistanceFormatter;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.SpeedFormatter;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.RouteSampler;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Renders a Strava-style shareable stat card: route polyline plus key stats on a dark card.
 * Runs synchronously; callers must invoke off the UI thread.
 */
public class TrackShareImageGenerator {

    public enum Style {
        ROUTE_ONLY,
        STATS_CARD
    }

    public record StatCard(@NonNull String value, @NonNull String label) {
    }

    private TrackShareImageGenerator() {
    }

    public static List<StatCard> buildStats(@NonNull Context context, @NonNull Track track) {
        Statistics stats = track.statistics();
        UnitSystem unitSystem = PreferencesUtils.getUnitSystem();
        boolean reportSpeed = PreferencesUtils.isReportSpeed(track);

        List<StatCard> cards = new java.util.ArrayList<>();
        DistanceFormatter distanceFormatter = DistanceFormatter.Builder().setUnit(unitSystem).build(context);
        cards.add(new StatCard(
                distanceFormatter.formatDistance(stats.totalDistance()),
                context.getString(R.string.stats_distance)));

        cards.add(new StatCard(
                StringUtils.formatElapsedTime(stats.movingDuration()),
                context.getString(R.string.stats_moving_time)));

        Speed moving = stats.getAverageMovingSpeed();
        SpeedFormatter paceFormatter = SpeedFormatter.Builder()
                .setUnit(unitSystem)
                .setReportSpeedOrPace(reportSpeed)
                .build(context);
        Pair<String, String> paceParts = paceFormatter.getSpeedParts(moving);
        cards.add(new StatCard(
                context.getString(R.string.speed_with_unit, paceParts.first, paceParts.second),
                context.getString(reportSpeed ? R.string.stats_speed : R.string.stats_pace)));

        if (stats.altitudeGainLoss() != null) {
            Pair<String, String> altitudeParts = StringUtils.getAltitudeParts(
                    context, (float) stats.altitudeGainLoss().gain_m(), unitSystem);
            cards.add(new StatCard(
                    context.getString(R.string.altitude_with_unit, altitudeParts.first, altitudeParts.second),
                    context.getString(R.string.share_image_stat_elevation_gain)));
        }

        if (stats.avgHeartRate() != null) {
            cards.add(new StatCard(
                    context.getString(R.string.sensor_state_heart_rate_value, Math.round(stats.avgHeartRate().getBPM())),
                    context.getString(R.string.sensor_state_heart_rate_avg)));
        }
        return cards;
    }

    /**
     * @param widthPx  target width, e.g. 1080
     * @param heightPx target height, e.g. 1350 (4:5) or 1920 (9:16)
     */
    public static Bitmap render(@NonNull Context context, @NonNull Track track,
                               @NonNull RouteSampler.Route route, @NonNull Style style,
                               int widthPx, int heightPx) {
        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int top = ContextCompat.getColor(context, R.color.thumb_gradient_start);
        int bottom = ContextCompat.getColor(context, R.color.thumb_gradient_end);
        Paint background = new Paint();
        background.setShader(new LinearGradient(0, 0, 0, heightPx, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, widthPx, heightPx, background);

        float padding = 48 * density;
        float y = padding;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(34 * scaledDensity);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(ellipsize(track.name(), titlePaint, widthPx - 2 * padding), padding, y + 34 * scaledDensity, titlePaint);
        y += 48 * scaledDensity;

        Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setColor(0xB3FFFFFF);
        subtitlePaint.setTextSize(16 * scaledDensity);
        String subtitle = StringUtils.formatDateTimeWithOffset(track.startTime())
                + "  ·  " + track.activityTypeLocalized();
        canvas.drawText(ellipsize(subtitle, subtitlePaint, widthPx - 2 * padding), padding, y + 16 * scaledDensity, subtitlePaint);
        y += 40 * scaledDensity;

        float routeTop = y;
        float routeBottom = style == Style.ROUTE_ONLY ? heightPx - padding - 40 * scaledDensity : heightPx * 0.62f;
        drawRoute(canvas, route.points(), padding, routeTop, widthPx - padding, routeBottom,
                ContextCompat.getColor(context, R.color.opentracks), density);

        if (style == Style.STATS_CARD) {
            float statsTop = routeBottom + 24 * density;
            drawStatsGrid(canvas, buildStats(context, track), padding, statsTop,
                    widthPx - padding, heightPx - padding - 40 * scaledDensity, scaledDensity, density);
        }

        Paint brandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(0xB3FFFFFF);
        brandPaint.setTextSize(14 * scaledDensity);
        String brand = context.getString(R.string.app_name);
        float brandWidth = brandPaint.measureText(brand);
        canvas.drawText(brand, widthPx - padding - brandWidth, heightPx - padding, brandPaint);

        return bitmap;
    }

    private static void drawRoute(Canvas canvas, List<RouteSampler.LatLon> points,
                                 float left, float top, float right, float bottom,
                                 int color, float density) {
        if (points.size() < 2) {
            Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
            empty.setColor(0x59FFFFFF);
            empty.setTextSize(18 * density);
            empty.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("—", (left + right) / 2f, (top + bottom) / 2f, empty);
            return;
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (RouteSampler.LatLon p : points) {
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }

        double midLatRad = Math.toRadians((minLat + maxLat) / 2.0);
        double lonScale = Math.cos(midLatRad);
        double routeWidth = Math.max((maxLon - minLon) * lonScale, 1e-9);
        double routeHeight = Math.max(maxLat - minLat, 1e-9);
        double scale = Math.min((right - left) / routeWidth, (bottom - top) / routeHeight);

        float offsetX = (float) ((left + right - routeWidth * scale) / 2.0);
        float offsetY = (float) ((top + bottom - routeHeight * scale) / 2.0);

        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
            float x = (float) (offsetX + (points.get(i).longitude() - minLon) * lonScale * scale);
            float y = (float) (offsetY + (maxLat - points.get(i).latitude()) * scale);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        // Soft glow under the line.
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeJoin(Paint.Join.ROUND);
        glow.setColor(color);
        glow.setAlpha(70);
        glow.setStrokeWidth(14 * density);
        canvas.drawPath(path, glow);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        paint.setStrokeWidth(6 * density);
        canvas.drawPath(path, paint);

        // Start dot.
        float startX = (float) (offsetX + (points.get(0).longitude() - minLon) * lonScale * scale);
        float startY = (float) (offsetY + (maxLat - points.get(0).latitude()) * scale);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(color);
        canvas.drawCircle(startX, startY, 5 * density, dot);
    }

    private static void drawStatsGrid(Canvas canvas, List<StatCard> cards,
                                     float left, float top, float right, float bottom,
                                     float scaledDensity, float density) {
        if (cards.isEmpty()) {
            return;
        }
        int columns = cards.size() <= 3 ? cards.size() : 3;
        int rows = (int) Math.ceil(cards.size() / (double) columns);
        float gap = 12 * density;
        float cellWidth = (right - left - gap * (columns - 1)) / columns;
        float cellHeight = Math.min((bottom - top - gap * (rows - 1)) / rows, 110 * density);

        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setTextSize(26 * scaledDensity);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xB3FFFFFF);
        labelPaint.setTextSize(13 * scaledDensity);

        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(0x26FFFFFF);

        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            float cellLeft = left + col * (cellWidth + gap);
            float cellTop = top + row * (cellHeight + gap);
            RectF rect = new RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight);
            canvas.drawRoundRect(rect, 16 * density, 16 * density, cardPaint);

            StatCard card = cards.get(i);
            float centerX = cellLeft + cellWidth / 2f;
            float valueY = cellTop + cellHeight / 2f - 4 * density;
            float labelY = valueY + 24 * scaledDensity;

            String value = ellipsize(card.value(), valuePaint, cellWidth - 24 * density);
            float valueWidth = valuePaint.measureText(value);
            canvas.drawText(value, centerX - valueWidth / 2f, valueY, valuePaint);
            float labelWidth = labelPaint.measureText(card.label());
            canvas.drawText(card.label(), centerX - labelWidth / 2f, labelY, labelPaint);
        }
    }

    private static String ellipsize(String text, Paint paint, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(end, 0)) + ellipsis;
    }
}
