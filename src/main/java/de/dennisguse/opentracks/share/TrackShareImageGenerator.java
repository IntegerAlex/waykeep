package de.dennisguse.opentracks.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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

    public enum Background {
        DARK,
        LIGHT,
        TRANSPARENT,
        MAP_TINT
    }

    public record StatCard(@NonNull String value, @NonNull String label) {
    }

    /**
     * Resolved colors for one background. Text is derived per background, never user-picked.
     */
    private record CardTheme(int textPrimary, int textSecondary, int cardFill, int placeholder) {
    }

    private TrackShareImageGenerator() {
    }

    private static final String PREFS = "share_image_options";
    private static final String PREF_BACKGROUND = "background";

    public static Background loadBackground(@NonNull Context context) {
        try {
            return Background.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_BACKGROUND, Background.DARK.name()));
        } catch (IllegalArgumentException e) {
            return Background.DARK;
        }
    }

    public static void saveBackground(@NonNull Context context, @NonNull Background background) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_BACKGROUND, background.name()).apply();
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
                               @NonNull Background background, int widthPx, int heightPx) {
        String subtitle = StringUtils.formatDateTimeWithOffset(track.startTime())
                + "  ·  " + track.activityTypeLocalized();
        List<StatCard> cards = style == Style.STATS_CARD ? buildStats(context, track) : List.of();
        return renderCard(context, track.name(), subtitle, route.points(), cards,
                style == Style.ROUTE_ONLY, background, widthPx, heightPx);
    }

    /**
     * Renders a stats-only card (e.g. monthly recap): title, subtitle, stat grid, no route.
     */
    public static Bitmap renderStats(@NonNull Context context, @NonNull String title,
                                    @NonNull String subtitle, @NonNull List<StatCard> cards,
                                    @NonNull Background background, int widthPx, int heightPx) {
        return renderCard(context, title, subtitle, List.of(), cards, false, background, widthPx, heightPx);
    }

    private static CardTheme themeFor(Background background) {
        return switch (background) {
            case LIGHT -> new CardTheme(0xFF1A1A1A, 0x8A000000, 0x14000000, 0x59000000);
            case TRANSPARENT -> new CardTheme(0xFFFFFFFF, 0xE6FFFFFF, 0x40000000, 0x59FFFFFF);
            case DARK, MAP_TINT -> new CardTheme(0xFFFFFFFF, 0xB3FFFFFF, 0x26FFFFFF, 0x59FFFFFF);
        };
    }

    private static void drawBackground(@NonNull Context context, @NonNull Canvas canvas,
                                      @NonNull Background background, int widthPx, int heightPx, float density) {
        if (background == Background.TRANSPARENT) {
            return;
        }
        Paint paint = new Paint();
        if (background == Background.LIGHT) {
            paint.setShader(new LinearGradient(0, 0, 0, heightPx, 0xFFFFFFFF, 0xFFE4E4E4, Shader.TileMode.CLAMP));
        } else {
            int top = ContextCompat.getColor(context, R.color.thumb_gradient_start);
            int bottom = ContextCompat.getColor(context, R.color.thumb_gradient_end);
            paint.setShader(new LinearGradient(0, 0, 0, heightPx, top, bottom, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, 0, widthPx, heightPx, paint);
    }

    private static Bitmap renderCard(@NonNull Context context, @NonNull String title,
                                    @NonNull String subtitle, @NonNull List<RouteSampler.LatLon> routePoints,
                                    @NonNull List<StatCard> cards, boolean routeOnly,
                                    @NonNull Background background, int widthPx, int heightPx) {
        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        CardTheme theme = themeFor(background);

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawBackground(context, canvas, background, widthPx, heightPx, density);

        float padding = 48 * density;
        float y = padding;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(theme.textPrimary());
        titlePaint.setTextSize(34 * scaledDensity);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setColor(theme.textSecondary());
        subtitlePaint.setTextSize(16 * scaledDensity);
        if (background == Background.TRANSPARENT) {
            // Readable over any photo.
            titlePaint.setShadowLayer(4 * density, 0, 2 * density, 0xCC000000);
            subtitlePaint.setShadowLayer(4 * density, 0, 2 * density, 0xCC000000);
        }
        canvas.drawText(ellipsize(title, titlePaint, widthPx - 2 * padding), padding, y + 34 * scaledDensity, titlePaint);
        y += 48 * scaledDensity;

        canvas.drawText(ellipsize(subtitle, subtitlePaint, widthPx - 2 * padding), padding, y + 16 * scaledDensity, subtitlePaint);
        y += 40 * scaledDensity;

        if (!routePoints.isEmpty() || routeOnly) {
            float routeTop = y;
            // Strava composition: map fills the top half, stats the bottom half.
            float routeBottom = routeOnly || cards.isEmpty() ? heightPx - padding - 40 * scaledDensity : heightPx * 0.52f;
            int mapW = Math.round(widthPx - 2 * padding);
            int mapH = Math.round(routeBottom - routeTop);
            MapTileFetcher.MapResult map = null;
            if (background == Background.MAP_TINT && mapH > 0 && !routePoints.isEmpty()) {
                try {
                    map = MapTileFetcher.render(context, routePoints, mapW, mapH);
                } catch (RuntimeException e) {
                    map = null;
                }
            }
            if (map != null) {
                canvas.drawBitmap(map.bitmap, padding, routeTop, null);
                drawRouteMercator(canvas, routePoints, map.fit, padding, routeTop,
                        ContextCompat.getColor(context, R.color.opentracks), density);
                map.bitmap.recycle();
            } else {
                drawRoute(canvas, routePoints, padding, routeTop, widthPx - padding, routeBottom,
                        ContextCompat.getColor(context, R.color.opentracks), theme.placeholder(), density);
            }
            y = routeBottom + 24 * density;
        }

        if (!cards.isEmpty()) {
            drawStatsGrid(canvas, cards, padding, y,
                    widthPx - padding, heightPx - padding - 40 * scaledDensity, theme, scaledDensity, density);
        }

        Paint brandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(theme.textSecondary());
        brandPaint.setTextSize(14 * scaledDensity);
        if (background == Background.TRANSPARENT) {
            brandPaint.setShadowLayer(4 * density, 0, 2 * density, 0xCC000000);
        }
        String brand = context.getString(R.string.app_name);
        float brandWidth = brandPaint.measureText(brand);
        canvas.drawText(brand, widthPx - padding - brandWidth, heightPx - padding, brandPaint);

        return bitmap;
    }

    private static void drawRoute(Canvas canvas, List<RouteSampler.LatLon> points,
                                 float left, float top, float right, float bottom,
                                 int color, int placeholderColor, float density) {
        if (points.size() < 2) {
            Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
            empty.setColor(placeholderColor);
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

    /**
     * Draws the route in the same mercator projection as a fetched tile backdrop,
     * with a light casing so it reads on top of real streets.
     */
    private static void drawRouteMercator(Canvas canvas, List<RouteSampler.LatLon> points,
                                         MapTileFetcher.Fit fit, float left, float top,
                                         int color, float density) {
        Path path = new Path();
        float startX = 0;
        float startY = 0;
        for (int i = 0; i < points.size(); i++) {
            double[] m = MapTileFetcher.mercatorPx(points.get(i).longitude(), points.get(i).latitude(), fit.zoom);
            float x = (float) (left + m[0] * fit.scale + fit.offX);
            float y = (float) (top + m[1] * fit.scale + fit.offY);
            if (i == 0) {
                path.moveTo(x, y);
                startX = x;
                startY = y;
            } else {
                path.lineTo(x, y);
            }
        }

        Paint casing = new Paint(Paint.ANTI_ALIAS_FLAG);
        casing.setStyle(Paint.Style.STROKE);
        casing.setStrokeCap(Paint.Cap.ROUND);
        casing.setStrokeJoin(Paint.Join.ROUND);
        casing.setColor(0xE6FFFFFF);
        casing.setStrokeWidth(9 * density);
        canvas.drawPath(path, casing);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        paint.setStrokeWidth(5 * density);
        canvas.drawPath(path, paint);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(color);
        canvas.drawCircle(startX, startY, 5 * density, dot);
    }

    private static void drawStatsGrid(Canvas canvas, List<StatCard> cards,
                                     float left, float top, float right, float bottom,
                                     CardTheme theme, float scaledDensity, float density) {
        if (cards.isEmpty()) {
            return;
        }
        // Strava-like strata: value over label, hairline dividers, no boxes.
        int columns;
        if (cards.size() <= 3) {
            columns = cards.size();
        } else if (cards.size() == 4) {
            columns = 2;
        } else {
            columns = 3;
        }
        int rows = (int) Math.ceil(cards.size() / (double) columns);
        float gap = 12 * density;
        float cellWidth = (right - left - gap * (columns - 1)) / columns;
        // Fill the remaining space instead of huddling at the top: rows share it.
        float cellHeight = Math.min((bottom - top - gap * Math.max(rows - 1, 0)) / rows, 170 * density);
        float blockHeight = rows * cellHeight + (rows - 1) * gap;
        float contentTop = top + Math.max(0, (bottom - top - blockHeight) / 2f);

        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(theme.textPrimary());
        valuePaint.setTextSize(26 * scaledDensity);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(theme.textSecondary());
        labelPaint.setTextSize(13 * scaledDensity);

        Paint dividerPaint = new Paint();
        dividerPaint.setColor(theme.textSecondary());
        dividerPaint.setAlpha(70);
        dividerPaint.setStrokeWidth(Math.max(1, density / 2));

        // Hairline above the block.
        canvas.drawLine(left, contentTop, right, contentTop, dividerPaint);

        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            float cellLeft = left + col * (cellWidth + gap);
            float cellTop = contentTop + row * cellHeight;

            StatCard card = cards.get(i);
            float centerX = cellLeft + cellWidth / 2f;

            String value = ellipsize(card.value(), valuePaint, cellWidth - 8 * density);
            float valueWidth = valuePaint.measureText(value);
            float valueBaseline = cellTop + cellHeight / 2f - 2 * density;
            canvas.drawText(value, centerX - valueWidth / 2f, valueBaseline, valuePaint);
            float labelWidth = labelPaint.measureText(card.label());
            canvas.drawText(card.label(), centerX - labelWidth / 2f, valueBaseline + 22 * scaledDensity, labelPaint);

            // Thin vertical divider between columns.
            if (col > 0) {
                float dividerX = cellLeft - gap / 2f;
                canvas.drawLine(dividerX, cellTop + 6 * density, dividerX, cellTop + cellHeight - 16 * density, dividerPaint);
            }
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
