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

package com.waykeep.app.chart;

import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import com.waykeep.app.R;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.data.statistics.ExtremityMonitor;
import com.waykeep.app.ui.util.ThemeUtils;
import com.waykeep.app.util.StringUtils;

/**
 * Visualization of the chart.
 * Provides support for zooming (via pinch), scrolling, flinging, and selecting shown markers (single touch).
 *
 * @author Sandor Dornbush
 * @author Leif Hendrik Wilden
 */
public class ChartView extends View {

    static final int Y_AXIS_INTERVALS = 5;

    private static final int TARGET_X_AXIS_INTERVALS = 4;

    private static final int MIN_ZOOM_LEVEL = 1;
    private static final int MAX_ZOOM_LEVEL = 10;

    private static final long INSPECTION_HIDE_DELAY_MS = 2000;
    private static final long INSPECTION_FADE_MS = 250;

    private static final NumberFormat X_NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final NumberFormat X_FRACTION_FORMAT = NumberFormat.getNumberInstance();
    private static final NumberFormat VALUE_1_DECIMAL_FORMAT = NumberFormat.getNumberInstance();
    private static final int BORDER = 8;
    private static final int SPACER = 4;
    private static final int Y_AXIS_OFFSET = 16;

    static {
        X_FRACTION_FORMAT.setMaximumFractionDigits(1);
        X_FRACTION_FORMAT.setMinimumFractionDigits(1);
        VALUE_1_DECIMAL_FORMAT.setMaximumFractionDigits(1);
        VALUE_1_DECIMAL_FORMAT.setMinimumFractionDigits(1);
    }

    private final List<ChartValueSeries> seriesList = new LinkedList<>();
    private final ChartValueSeries elevationSeries;
    private final ChartValueSeries speedSeries;
    private final ChartValueSeries paceSeries;
    private final ChartValueSeries heartRateSeries;
    private final ChartValueSeries cadenceSeries;
    private final ChartValueSeries powerSeries;

    private final LinkedList<ChartPoint> chartPoints = new LinkedList<>();
    private final ExtremityMonitor xExtremityMonitor = new ExtremityMonitor();
    private final int backgroundColor;
    private final Paint axisPaint;
    private final Paint xAxisMarkerPaint;
    private final Paint gridPaint;
    private final Paint crosshairPaint;
    private final Paint tooltipBackgroundPaint;
    private final Paint tooltipBorderPaint;
    private final Paint tooltipHeaderPaint;
    private final Paint tooltipTextPaint;
    private final Paint seriesDotPaint;
    private final Paint seriesDotRingPaint;
    private final Scroller scroller;
    private double maxX = 1.0;
    private int zoomLevel = 1;

    private ChartPoint inspectionPoint;
    private boolean scrubbing = false;
    private float inspectionAlpha = 1f;
    private ValueAnimator inspectionFade;
    private final Runnable inspectionHideRunnable = this::startInspectionFade;

    private int leftBorder = BORDER;
    private int topBorder = BORDER;
    private int bottomBorder = BORDER;
    private int rightBorder = BORDER;
    private int spacer = SPACER;
    private int yAxisOffset = Y_AXIS_OFFSET;

    private int width = 0;
    private int height = 0;
    private int effectiveWidth = 0;
    private int effectiveHeight = 0;
    private TitleDimensions titleDimensions;
    private boolean twoLineYaxisNumbers = false;
    private int maxYaxisNumberHeight = 0;

    private boolean chartByDistance = false;
    private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();
    private boolean reportSpeed = true;
    private boolean showPointer = false;

    private final GestureDetectorCompat detectorScrollFlingTab = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (Math.abs(distanceX) > 0) {
                int availableToScroll = effectiveWidth * (zoomLevel - 1) - getScrollX();
                if (availableToScroll > 0) {
                    scrollBy(Math.min(availableToScroll, (int) distanceX));
                }
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX);
            return true;
        }

    });

    private final ScaleGestureDetector detectorZoom = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor >= 1.1f) {
                zoomIn();
                return true;
            } else if (scaleFactor <= 0.9) {
                zoomOut();
                return true;
            }
            return false;
        }
    });

    /**
     * Tap selects the nearest data point for inspection; long-press then drag scrubs along the
     * chart. A quick horizontal swipe is left to the parent pager (we don't disallow interception
     * until scrubbing actually starts), so tab switching still works.
     */
    private final GestureDetectorCompat detectorInspect = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return true;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            scrubbing = true;
            updateInspection(e.getX());
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            toggleInspection(e.getX());
            return true;
        }
    });

    public ChartView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        int fontSizeSmall = ThemeUtils.getFontSizeSmallInPx(context);
        int fontSizeMedium = ThemeUtils.getFontSizeMediumInPx(context);

        elevationSeries = new ChartValueSeries(context,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            new int[]{5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000},
            R.string.description_altitude_metric,
            R.string.description_altitude_imperial,
            R.string.description_altitude_imperial,
            R.color.chart_altitude_fill,
            R.color.chart_altitude_border,
            fontSizeSmall,
            fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.altitude();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        };
        seriesList.add(elevationSeries);

        speedSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 5, 10, 20, 50, 100},
                R.string.description_speed_metric,
                R.string.description_speed_imperial,
                R.string.description_speed_nautical,
                R.color.chart_speed_fill,
                R.color.chart_speed_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.speed();
            }

            @Override
            String formatValue(double value) {
                return VALUE_1_DECIMAL_FORMAT.format(value);
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return reportSpeed;
            }
        };
        seriesList.add(speedSeries);

        paceSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 2, 5, 10, 15, 20, 30, 60, 120},
                R.string.description_pace_metric,
                R.string.description_pace_imperial,
                R.string.description_pace_nautical,
                R.color.chart_pace_fill,
                R.color.chart_pace_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.pace();
            }

            @Override
            String formatValue(double value) {
                // value is minutes as a decimal (e.g. 5.5 -> 5:30)
                long totalSeconds = Math.round(value * 60d);
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                return String.format(Locale.US, "%d:%02d", minutes, seconds);
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return !reportSpeed;
            }
        };
        seriesList.add(paceSeries);

        heartRateSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                // For Zone 5 cardio, the 25 value should result in nice visuals, as the values
                // will range from ~70 - ~180 (around 4.5 intervals).
                // For Zone 1 cardio, the 15 value should result in nice visuals, as the values
                // will range from ~70 - ~120 (around 3.5 intervals)
                // The fallback of 50 should give appropriate visuals for values outside this range.
                new int[]{15, 25, 50},
                R.string.description_sensor_heart_rate,
                R.string.description_sensor_heart_rate,
                R.string.description_sensor_heart_rate,
                R.color.chart_heart_rate_fill,
                R.color.chart_heart_rate_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.heartRate();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        };
        seriesList.add(heartRateSeries);

        cadenceSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{5, 10, 25, 50},
                R.string.description_sensor_cadence,
                R.string.description_sensor_cadence,
                R.string.description_sensor_cadence,
                R.color.chart_cadence_fill,
                R.color.chart_cadence_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.cadence();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        };
        seriesList.add(cadenceSeries);

        powerSeries = new ChartValueSeries(context,
                0,
                1000,
                new int[]{5, 50, 100, 200},
                R.string.description_sensor_power,
                R.string.description_sensor_power,
                R.string.description_sensor_power,
                R.color.chart_power_fill,
                R.color.chart_power_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint) {
                return chartPoint.power();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        };
        seriesList.add(powerSeries);

        backgroundColor = ThemeUtils.getBackgroundColor(context);
        float density = context.getResources().getDisplayMetrics().density;

        axisPaint = new Paint();
        axisPaint.setStyle(Style.FILL_AND_STROKE);
        axisPaint.setColor(ThemeUtils.getTextColorPrimary(context));
        axisPaint.setAntiAlias(true);
        axisPaint.setTextSize(fontSizeSmall);

        xAxisMarkerPaint = new Paint(axisPaint);
        xAxisMarkerPaint.setTextAlign(Align.CENTER);

        gridPaint = new Paint();
        gridPaint.setStyle(Style.STROKE);
        gridPaint.setColor(withAlpha(ThemeUtils.getTextColorPrimary(context), 0x1F));
        gridPaint.setAntiAlias(true);
        gridPaint.setStrokeWidth(Math.max(1f, density * 0.5f));

        crosshairPaint = new Paint();
        crosshairPaint.setStyle(Style.STROKE);
        crosshairPaint.setColor(withAlpha(ThemeUtils.getTextColorPrimary(context), 0x99));
        crosshairPaint.setAntiAlias(true);
        crosshairPaint.setStrokeWidth(density);

        tooltipBackgroundPaint = new Paint();
        tooltipBackgroundPaint.setStyle(Style.FILL);
        tooltipBackgroundPaint.setColor(withAlpha(backgroundColor, 0xF2));
        tooltipBackgroundPaint.setAntiAlias(true);

        tooltipBorderPaint = new Paint();
        tooltipBorderPaint.setStyle(Style.STROKE);
        tooltipBorderPaint.setColor(withAlpha(ThemeUtils.getTextColorPrimary(context), 0x33));
        tooltipBorderPaint.setAntiAlias(true);
        tooltipBorderPaint.setStrokeWidth(Math.max(1f, density * 0.5f));

        tooltipHeaderPaint = new Paint(axisPaint);
        tooltipHeaderPaint.setStyle(Style.FILL);
        tooltipHeaderPaint.setTextSize(fontSizeMedium);
        tooltipHeaderPaint.setFakeBoldText(true);

        tooltipTextPaint = new Paint(axisPaint);
        tooltipTextPaint.setStyle(Style.FILL);

        seriesDotPaint = new Paint();
        seriesDotPaint.setStyle(Style.FILL);
        seriesDotPaint.setAntiAlias(true);

        seriesDotRingPaint = new Paint();
        seriesDotRingPaint.setStyle(Style.STROKE);
        seriesDotRingPaint.setColor(backgroundColor);
        seriesDotRingPaint.setAntiAlias(true);
        seriesDotRingPaint.setStrokeWidth(2 * density);

        scroller = new Scroller(context);
        setFocusable(true);
        setClickable(true);
        updateDimensions();

        applyMetricSelection();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return true;
    }

    public void setChartByDistance(boolean chartByDistance) {
        this.chartByDistance = chartByDistance;
    }

    public UnitSystem getUnitSystem() {
        return unitSystem;
    }

    public void setUnitSystem(UnitSystem value) {
        unitSystem = value;
    }

    public boolean getReportSpeed() {
        return reportSpeed;
    }

    /**
     * Sets report speed.
     *
     * @param value report speed (true) or pace (false)
     */
    public void setReportSpeed(boolean value) {
        reportSpeed = value;
    }

    public boolean applyReportSpeed() {
        if (selectedMetric != Metric.SPEED) {
            return false;
        }

        if (reportSpeed) {
            if (!speedSeries.isEnabled()) {
                speedSeries.setEnabled(true);
                paceSeries.setEnabled(false);
                return true;
            }
        } else {
            if (!paceSeries.isEnabled()) {
                speedSeries.setEnabled(false);
                paceSeries.setEnabled(true);
                return true;
            }
        }

        return false;
    }

    /**
     * Which single metric the chart plots.
     */
    public enum Metric {
        ELEVATION, SPEED, HEART_RATE, CADENCE, POWER
    }

    private Metric selectedMetric = Metric.ELEVATION;

    /**
     * Selects a single metric to plot. Only that series is drawn, so the chart always has a
     * single Y-axis. {@link Metric#SPEED} resolves to speed or pace depending on the
     * speed/pace preference.
     */
    public void setMetric(Metric metric) {
        if (metric == null) {
            return;
        }
        selectedMetric = metric;
        applyMetricSelection();
        updateSeries();
        updateDimensions();
        requestLayout();
        invalidate();
    }

    public Metric getMetric() {
        return selectedMetric;
    }

    /**
     * Returns true when the selected chart has enough data to show this metric.
     */
    public boolean hasDataFor(Metric metric) {
        return seriesFor(metric).hasData();
    }

    /**
     * Resolves a metric to its series for the current speed/pace preference. When the metric is
     * SPEED and no data exists for the preferred series, the other one is used.
     */
    private ChartValueSeries seriesFor(Metric metric) {
        if (metric == Metric.SPEED) {
            if (reportSpeed) {
                return speedSeries.hasData() || !paceSeries.hasData() ? speedSeries : paceSeries;
            }
            return paceSeries.hasData() || !speedSeries.hasData() ? paceSeries : speedSeries;
        }
        return switch (metric) {
            case ELEVATION -> elevationSeries;
            case HEART_RATE -> heartRateSeries;
            case CADENCE -> cadenceSeries;
            case POWER -> powerSeries;
            case SPEED -> speedSeries;
        };
    }

    private void applyMetricSelection() {
        for (ChartValueSeries series : seriesList) {
            series.setEnabled(false);
        }
        seriesFor(selectedMetric).setEnabled(true);
    }

    public void setShowPointer(boolean value) {
        showPointer = value;
    }

    public void addChartPoints(List<ChartPoint> dataPoints) {
        synchronized (chartPoints) {
            chartPoints.addAll(dataPoints);
            for (ChartPoint dataPoint : dataPoints) {
                xExtremityMonitor.update(dataPoint.timeOrDistance());
                for (ChartValueSeries i : seriesList) {
                    i.update(dataPoint);
                }
            }
            updateDimensions();
            updateSeries();
        }
    }

    /**
     * Clears all data.
     */
    public void reset() {
        synchronized (chartPoints) {
            chartPoints.clear();
            xExtremityMonitor.reset();
            zoomLevel = 1;
            cancelInspectionHide();
            cancelFade();
            inspectionPoint = null;
            inspectionAlpha = 1f;
            scrubbing = false;
            updateDimensions();
        }
    }

    /**
     * Resets scroll.
     * To be called on the UI thread.
     */
    public void resetScroll() {
        scrollTo(0, 0);
    }

    private boolean canZoomIn() {
        return zoomLevel < MAX_ZOOM_LEVEL;
    }

    private boolean canZoomOut() {
        return zoomLevel > MIN_ZOOM_LEVEL;
    }

    private void zoomIn() {
        if (canZoomIn()) {
            zoomLevel++;
            updateSeries();
            invalidate();
        }
    }

    private void zoomOut() {
        if (canZoomOut()) {
            zoomLevel--;
            scroller.abortAnimation();
            int scrollX = getScrollX();
            int maxWidth = effectiveWidth * (zoomLevel - 1);
            if (scrollX > maxWidth) {
                scrollX = maxWidth;
                scrollTo(scrollX, 0);
            }
            updateSeries();
            invalidate();
        }
    }

    /**
     * Initiates flinging.
     *
     * @param velocityX velocity of fling in pixels per second
     */
    private void fling(int velocityX) {
        int maxWidth = effectiveWidth * (zoomLevel - 1);
        scroller.fling(getScrollX(), 0, velocityX, 0, 0, maxWidth, 0, 0);
        invalidate();
    }

    /**
     * Handle parent's view disallow touch event.
     *
     * @param disallow Does disallow parent touch event?
     */
    private void requestDisallowInterceptTouchEventInParent(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /**
     * Scrolls the view horizontally by a given amount.
     *
     * @param deltaX the number of pixels to scroll
     */
    private void scrollBy(int deltaX) {
        int scrollX = getScrollX() + deltaX;
        if (scrollX <= 0) {
            scrollX = 0;
        }

        int maxWidth = effectiveWidth * (zoomLevel - 1);
        if (scrollX >= maxWidth) {
            scrollX = maxWidth;
        }

        scrollTo(scrollX, 0);
    }

    /**
     * Called by the parent to indicate that the mScrollX/Y values need to be
     * updated. Triggers a redraw during flinging.
     */
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int x = scroller.getCurrX();
            scrollTo(x, 0);
            if (oldX != x) {
                onScrollChanged(x, 0, oldX, 0);
                postInvalidate();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isZoom = detectorZoom.onTouchEvent(event);

        if (event.getPointerCount() == 1 && zoomLevel == MIN_ZOOM_LEVEL) {
            // Single-pointer at full zoom: tap to inspect, long-press to scrub. We intentionally
            // allow the parent pager to intercept until scrubbing starts, so left/right swipes
            // still switch tabs.
            detectorInspect.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && scrubbing) {
                updateInspection(event.getX());
            }
            if ((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && scrubbing) {
                scrubbing = false;
                requestDisallowInterceptTouchEventInParent(false);
                scheduleInspectionHide();
            }
            if (scrubbing) {
                requestDisallowInterceptTouchEventInParent(true);
            }
            return true;
        }

        boolean isScrollTab = detectorScrollFlingTab.onTouchEvent(event);

        // ChartView handles zoom gestures (more than one pointer) and all gestures when zoomed itself
        requestDisallowInterceptTouchEventInParent(event.getPointerCount() != 1 || zoomLevel != MIN_ZOOM_LEVEL);

        return isZoom || isScrollTab;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateEffectiveDimensionsIfChanged(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        synchronized (chartPoints) {
            canvas.save();

            canvas.drawColor(backgroundColor);

            canvas.save();

            clipToGraphArea(canvas);
            drawDataSeries(canvas);
            drawGrid(canvas);
            drawInspectionMarkers(canvas);

            canvas.restore();

            drawSeriesTitles(canvas);
            drawXAxis(canvas);
            drawYAxis(canvas);

            canvas.restore();

            if (showPointer) {
                drawPointer(canvas);
            }

            drawInspectionTooltip(canvas);
        }
    }

    /**
     * Clips a canvas to the graph area.
     *
     * @param canvas the canvas
     */
    private void clipToGraphArea(Canvas canvas) {
        int x = getScrollX() + leftBorder;
        int y = topBorder;
        canvas.clipRect(x, y, x + effectiveWidth, y + effectiveHeight);
    }

    /**
     * Draws the data series.
     *
     * @param canvas the canvas
     */
    private void drawDataSeries(Canvas canvas) {
        for (ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData()) {
                chartValueSeries.updateFillGradient(topBorder, topBorder + effectiveHeight);
                chartValueSeries.drawPath(canvas, titleDimensions.titlePositions.size() < 3);
            }
        }
    }

    /**
     * Draws the grid.
     *
     * @param canvas the canvas
     */
    private void drawGrid(Canvas canvas) {
        // X axis grid
        List<Double> xAxisMarkerPositions = getXAxisMarkerPositions(getXAxisInterval());
        for (double position : xAxisMarkerPositions) {
            int x = getX(position);
            canvas.drawLine(x, topBorder, x, topBorder + effectiveHeight, gridPaint);
        }
        // Y axis grid
        float rightEdge = getX(maxX);
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            double percentage = (double) i / Y_AXIS_INTERVALS;
            int range = effectiveHeight - 2 * yAxisOffset;
            int y = topBorder + yAxisOffset + (int) (percentage * range);
            canvas.drawLine(leftBorder, y, rightEdge, y, gridPaint);
        }
    }

    private record TitlePosition(
            int line, // line number (starts at 1, top to bottom numbering)
            int xPos // x position in points (starts at 0, left to right indexing)
    ) {}

    private record TitleDimensions(
            int lineCount, // number of lines the titles will take
            int lineHeight, // height of a line (all lines have the same height)
            List<TitlePosition> titlePositions // positions of visible titles (the order corresponds to seriesList)
    ) {}

    /**
     * Draws series titles.
     *
     * @param canvas the canvas
     */
    private void drawSeriesTitles(Canvas canvas) {
        Iterator<TitlePosition> tpI = titleDimensions.titlePositions.iterator();

        for (ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                String title = getContext().getString(chartValueSeries.getTitleId(unitSystem));
                Paint paint = chartValueSeries.getTitlePaint();

                // It is possible for the titlePositions to become empty temporarily, while switching between
                // chart screens quickly.
                if (!tpI.hasNext()) {
                    return;
                }

                TitlePosition tp = tpI.next();
                int y = topBorder - spacer - (titleDimensions.lineCount - tp.line) * (titleDimensions.lineHeight + spacer);
                canvas.drawText(title, tp.xPos + getScrollX(), y, paint);
            }
        }
    }

    /**
     * Gets the title dimensions.
     * Returns an array of 2 integers, first element is the number of lines and the second element is the line height.
     */
    private TitleDimensions getTitleDimensions() {
        int lineCnt = 1;
        int lineHeight = 0;
        List<TitlePosition> tps = new ArrayList<>();
        int xPosInLine = spacer;
        for (ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                String title = getContext().getString(chartValueSeries.getTitleId(unitSystem));
                Rect rect = getRect(chartValueSeries.getTitlePaint(), title);
                if (rect.height() > lineHeight) lineHeight = rect.height();
                int xNextPosInLine = xPosInLine + rect.width() + 2*spacer;
                // if second or later title does not fully fit on this line then print it on the next line
                if (xPosInLine > spacer && xNextPosInLine-spacer > width) {
                    lineCnt++;
                    xPosInLine = spacer;
                }
                tps.add(new TitlePosition(lineCnt, xPosInLine));
                xPosInLine += rect.width() + 2*spacer;
            }
        }
        return new TitleDimensions(lineCnt, lineHeight, tps);
    }

    /**
     * Draws the x axis.
     *
     * @param canvas the canvas
     */
    private void drawXAxis(Canvas canvas) {
        int x = getScrollX() + leftBorder;
        int y = topBorder + effectiveHeight;
        canvas.drawLine(x, y, x + effectiveWidth, y, axisPaint);
        String label = getXAxisLabel();
        Rect rect = getRect(axisPaint, label);
        int yOffset = rect.height() / 2;
        canvas.drawText(label, x + effectiveWidth - rect.width(), y + 3 * yOffset, axisPaint);

        double interval = getXAxisInterval();
        NumberFormat numberFormat = interval < 1 ? X_FRACTION_FORMAT : X_NUMBER_FORMAT;

        for (double markerPosition : getXAxisMarkerPositions(interval)) {
            drawXAxisMarker(canvas, markerPosition, numberFormat, spacer + rect.width(), spacer + yOffset);
        }
    }

    private String getXAxisLabel() {
        Context context = getContext();
        if (chartByDistance) {
            return switch (unitSystem) {
                case METRIC -> context.getString(R.string.unit_kilometer);
                case IMPERIAL_FEET, IMPERIAL_METER -> context.getString(R.string.unit_mile);
                case NAUTICAL_IMPERIAL -> context.getString(R.string.unit_nautical_mile);
            };
        } else {
            return context.getString(R.string.description_time);
        }
    }

    /**
     * Draws a x axis marker.
     *
     * @param canvas       canvas
     * @param value        value
     * @param numberFormat the number format
     * @param xRightSpace  the space taken up by the x axis label
     * @param yBottomSpace the space between x axis and marker
     */
    private void drawXAxisMarker(Canvas canvas, double value, NumberFormat numberFormat, int xRightSpace, int yBottomSpace) {
        String marker = chartByDistance ? numberFormat.format(value) : StringUtils.formatElapsedTime((Duration.ofMillis((long) value)));
        Rect rect = getRect(xAxisMarkerPaint, marker);
        int markerXPos = getX(value);
        int markerEndXPos = markerXPos + rect.width()/2;
        if (markerEndXPos > getScrollX() + leftBorder + effectiveWidth - xRightSpace) return;
        canvas.drawText(marker, markerXPos, topBorder + effectiveHeight + yBottomSpace + rect.height(), xAxisMarkerPaint);
    }

    private double getXAxisInterval() {
        double interval = maxX / zoomLevel / TARGET_X_AXIS_INTERVALS;
        if (interval < 1) {
            interval = .5;
        } else if (interval < 5) {
            interval = 2;
        } else if (interval < 10) {
            interval = 5;
        } else {
            interval = (interval / 10) * 10;
        }
        return interval;
    }

    private List<Double> getXAxisMarkerPositions(double interval) {
        List<Double> markers = new ArrayList<>();
        markers.add(0d);
        for (int i = 1; i * interval < maxX; i++) {
            markers.add(i * interval);
        }

        if (markers.size() < 2) {
            markers.add(maxX);
        }
        return markers;
    }

    /**
     * Draws the y axis.
     *
     * @param canvas the canvas
     */
    private void drawYAxis(Canvas canvas) {
        final int x = getScrollX() + leftBorder;
        final int y = topBorder;
        canvas.drawLine(x, y, x, y + effectiveHeight, axisPaint);

        //TODO
        int markerXPosition = x - spacer;
        int index = titleDimensions.titlePositions.size() - 1; // index only over the visible chart series
        final int lastDrawn2ndLineMarkerIndex = getYmarkerCountOn1stLine();
        for (int i = seriesList.size()-1; i>=0 ;--i) { // draw markers from the last series to achieve right alignment
            ChartValueSeries chartValueSeries = seriesList.get(i);
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                int yOffset = !twoLineYaxisNumbers ? 0 : (spacer+maxYaxisNumberHeight)/2 * (index >= lastDrawn2ndLineMarkerIndex ? 1 : -1);
                markerXPosition -= drawYAxisMarkers(chartValueSeries, canvas, markerXPosition, yOffset) + spacer;
                if (twoLineYaxisNumbers && index == lastDrawn2ndLineMarkerIndex)
                    markerXPosition = x - spacer;
                --index;
            }
        }
    }

    /**
     * Draws the y axis markers for a chart value series.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @param yOffset          offset to apply to y position
     * @return the maximum marker width.
     */
    private float drawYAxisMarkers(ChartValueSeries chartValueSeries, Canvas canvas, int xPosition, int yOffset) {
        int interval = chartValueSeries.getInterval();
        float maxMarkerWidth = 0;
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            maxMarkerWidth = Math.max(maxMarkerWidth, drawYAxisMarker(chartValueSeries, canvas, xPosition,
                    yOffset, i * interval + chartValueSeries.getMinMarkerValue()));
        }
        return maxMarkerWidth;
    }

    /**
     * Draws a y axis marker.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @param yOffset           offset to apply to y position
     * @param yValue           the y value
     * @return the marker width.
     */
    private float drawYAxisMarker(ChartValueSeries chartValueSeries, Canvas canvas, int xPosition, int yOffset, int yValue) {
        String marker = chartValueSeries.formatMarker(yValue);
        Paint paint = chartValueSeries.getMarkerPaint();
        Rect rect = getRect(paint, marker);
        int yPosition = getY(chartValueSeries, yValue) + (rect.height() / 2 + yOffset);
        canvas.drawText(marker, xPosition, yPosition, paint);
        return paint.measureText(marker);
    }

    private void drawPointer(Canvas canvas) {
        if (chartPoints.isEmpty()) {
            return;
        }
        ChartPoint last = chartPoints.getLast();

        ChartValueSeries firstChartValueSeries = null;
        for (ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() && chartValueSeries.isChartPointValid(last)) {
                firstChartValueSeries = chartValueSeries;
                break;
            }
        }
        if (firstChartValueSeries == null) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float radius = 5 * density;
        int cx = getX(maxX);
        double value = firstChartValueSeries.extractDataFromChartPoint(last);
        int cy = (int) clampGraphY(getY(firstChartValueSeries, value), topBorder + effectiveHeight);

        seriesDotPaint.setColor(firstChartValueSeries.getStrokeColor());
        canvas.drawCircle(cx, cy, radius, seriesDotRingPaint);
        canvas.drawCircle(cx, cy, radius - seriesDotRingPaint.getStrokeWidth() / 2f, seriesDotPaint);
    }

    /**
     * Moves the inspection crosshair to the data point nearest to the touch x.
     */
    private void updateInspection(float touchX) {
        ChartPoint nearest = findNearestChartPoint(touchX);
        if (nearest == null) {
            return;
        }
        inspectionPoint = nearest;
        inspectionAlpha = 1f;
        cancelFade();
        cancelInspectionHide();
        invalidate();
    }

    /**
     * Tap behaviour: select the nearest point, or clear the selection when tapping the same point.
     */
    private void toggleInspection(float touchX) {
        ChartPoint nearest = findNearestChartPoint(touchX);
        if (nearest == null) {
            return;
        }
        boolean clear = nearest == inspectionPoint;
        inspectionPoint = clear ? null : nearest;
        inspectionAlpha = 1f;
        cancelFade();
        if (clear) {
            cancelInspectionHide();
        } else {
            scheduleInspectionHide();
        }
        invalidate();
    }

    /**
     * Clears the inspection crosshair immediately and cancels any pending fade.
     */
    private void clearInspection() {
        cancelInspectionHide();
        cancelFade();
        inspectionPoint = null;
        inspectionAlpha = 1f;
        invalidate();
    }

    private void scheduleInspectionHide() {
        cancelInspectionHide();
        if (inspectionPoint != null) {
            postDelayed(inspectionHideRunnable, INSPECTION_HIDE_DELAY_MS);
        }
    }

    private void cancelInspectionHide() {
        removeCallbacks(inspectionHideRunnable);
    }

    private void cancelFade() {
        if (inspectionFade != null) {
            inspectionFade.cancel();
            inspectionFade = null;
        }
    }

    private void startInspectionFade() {
        if (inspectionPoint == null) {
            return;
        }
        cancelFade();
        inspectionFade = ValueAnimator.ofFloat(inspectionAlpha, 0f);
        inspectionFade.setDuration(INSPECTION_FADE_MS);
        inspectionFade.addUpdateListener(animation -> {
            inspectionAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        inspectionFade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                inspectionPoint = null;
                inspectionAlpha = 1f;
                invalidate();
            }
        });
        inspectionFade.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelInspectionHide();
        cancelFade();
    }

    private ChartPoint findNearestChartPoint(float touchX) {
        double trackWidth = (double) effectiveWidth * zoomLevel;
        if (trackWidth <= 0 || maxX <= 0) {
            return null;
        }
        double contentX = touchX + getScrollX() - leftBorder;
        double xValue = (contentX / trackWidth) * maxX;
        xValue = Math.max(0, Math.min(maxX, xValue));

        ChartPoint nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (ChartPoint point : chartPoints) {
            double distance = Math.abs(point.timeOrDistance() - xValue);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = point;
            }
        }
        return nearest;
    }

    private String formatXValue(double value) {
        if (chartByDistance) {
            return X_FRACTION_FORMAT.format(value) + " " + getXAxisLabel();
        }
        return StringUtils.formatElapsedTime(Duration.ofMillis((long) value));
    }

    private void drawInspectionMarkers(Canvas canvas) {
        if (inspectionPoint == null) {
            return;
        }
        int x = getX(inspectionPoint.timeOrDistance());
        if (x < getScrollX() + leftBorder || x > getScrollX() + leftBorder + effectiveWidth) {
            return;
        }
        int layer = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * inspectionAlpha));
        canvas.drawLine(x, topBorder, x, topBorder + effectiveHeight, crosshairPaint);

        float density = getResources().getDisplayMetrics().density;
        float radius = 4 * density;
        for (ChartValueSeries series : seriesList) {
            if (!series.isEnabled() || !series.hasData() || !series.isChartPointValid(inspectionPoint)) {
                continue;
            }
            double value = series.extractDataFromChartPoint(inspectionPoint);
            int y = (int) clampGraphY(getY(series, value), topBorder + effectiveHeight);
            seriesDotPaint.setColor(series.getStrokeColor());
            canvas.drawCircle(x, y, radius + seriesDotRingPaint.getStrokeWidth() / 2f, seriesDotRingPaint);
            canvas.drawCircle(x, y, radius, seriesDotPaint);
        }
        canvas.restoreToCount(layer);
    }

    private void drawInspectionTooltip(Canvas canvas) {
        if (inspectionPoint == null) {
            return;
        }
        int crosshairX = getX(inspectionPoint.timeOrDistance());
        if (crosshairX < getScrollX() + leftBorder || crosshairX > getScrollX() + leftBorder + effectiveWidth) {
            return;
        }
        int layer = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * inspectionAlpha));

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(formatXValue(inspectionPoint.timeOrDistance()));
        colors.add(ThemeUtils.getTextColorPrimary(getContext()));
        for (ChartValueSeries series : seriesList) {
            if (!series.isEnabled() || !series.hasData() || !series.isChartPointValid(inspectionPoint)) {
                continue;
            }
            double value = series.extractDataFromChartPoint(inspectionPoint);
            lines.add(getContext().getString(series.getTitleId(unitSystem)) + "  " + series.formatValue(value));
            colors.add(series.getStrokeColor());
        }

        float density = getResources().getDisplayMetrics().density;
        float padding = 6 * density;
        float lineGap = 3 * density;
        float maxWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            Paint paint = i == 0 ? tooltipHeaderPaint : tooltipTextPaint;
            maxWidth = Math.max(maxWidth, paint.measureText(lines.get(i)));
        }

        float lineHeight = tooltipTextPaint.getFontSpacing() + lineGap;
        float width = maxWidth + 2 * padding;
        float height = tooltipHeaderPaint.getFontSpacing() + (lines.size() - 1) * lineHeight + 2 * padding;

        float left = crosshairX + 12 * density;
        float graphRight = getScrollX() + leftBorder + effectiveWidth;
        if (left + width > graphRight) {
            left = crosshairX - 12 * density - width;
        }
        left = Math.max(getScrollX() + leftBorder, left);
        float top = topBorder + 4 * density;
        RectF rect = new RectF(left, top, left + width, top + height);

        float corner = 8 * density;
        canvas.drawRoundRect(rect, corner, corner, tooltipBackgroundPaint);
        canvas.drawRoundRect(rect, corner, corner, tooltipBorderPaint);

        float textX = left + padding;
        float baseline = top + padding - tooltipHeaderPaint.ascent();
        canvas.drawText(lines.get(0), textX, baseline, tooltipHeaderPaint);
        for (int i = 1; i < lines.size(); i++) {
            baseline += lineHeight;
            tooltipTextPaint.setColor(colors.get(i));
            canvas.drawText(lines.get(i), textX, baseline, tooltipTextPaint);
        }
        canvas.restoreToCount(layer);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * The path needs to be updated any time after the data or the dimensions change.
     */
    private void updateSeries() {
        synchronized (chartPoints) {
            seriesList.forEach(this::updateSerie);
        }
    }

    private void updateSerie(ChartValueSeries series) {
        final int yCorner = topBorder + effectiveHeight;
        final Path path = series.getPath();

        path.rewind();

        // Collect the visible points first so the smoothing can look at neighbours.
        final List<float[]> points = new ArrayList<>();
        for (ChartPoint point : chartPoints) {
            if (!series.isChartPointValid(point)) {
                continue;
            }
            double value = series.extractDataFromChartPoint(point);
            float x = getX(point.timeOrDistance());
            float y = clampGraphY(getY(series, value), yCorner);
            points.add(new float[]{x, y});
        }
        if (points.isEmpty()) {
            return;
        }

        final float firstX = points.get(0)[0];
        // start from lower left corner
        path.moveTo(firstX, yCorner);
        path.lineTo(points.get(0)[0], points.get(0)[1]);

        // Catmull-Rom style smoothing: the curve passes through every data point, so peaks are
        // preserved, while control points (derived from neighbours) round the corners. Control
        // points are clamped to the graph so the curve cannot overshoot outside the plot.
        for (int i = 0; i < points.size() - 1; i++) {
            float[] p0 = points.get(Math.max(i - 1, 0));
            float[] p1 = points.get(i);
            float[] p2 = points.get(i + 1);
            float[] p3 = points.get(Math.min(i + 2, points.size() - 1));

            float c1x = p1[0] + (p2[0] - p0[0]) / 6f;
            float c1y = clampGraphY(p1[1] + (p2[1] - p0[1]) / 6f, yCorner);
            float c2x = p2[0] - (p3[0] - p1[0]) / 6f;
            float c2y = clampGraphY(p2[1] - (p3[1] - p1[1]) / 6f, yCorner);

            path.cubicTo(c1x, c1y, c2x, c2y, p2[0], p2[1]);
        }

        // last point: move to lower right
        final float lastX = points.get(points.size() - 1)[0];
        path.lineTo(lastX, yCorner);

        // back to lower left corner
        path.close();
    }

    private float clampGraphY(float y, int yCorner) {
        return Math.max(topBorder, Math.min(yCorner, y));
    }

    /// expected number of Y-axis markers on the first line if the Y-axis markers are split to two lines
    private int getYmarkerCountOn1stLine() {
        return (int)Math.ceil(titleDimensions.titlePositions.size()/2.0);
    }
    /**
     * Updates the chart dimensions.
     */
    private void updateDimensions() {
        maxX = xExtremityMonitor.hasData() ? xExtremityMonitor.getMax() : 1.0;
        for (ChartValueSeries chartValueSeries : seriesList) {
            chartValueSeries.updateDimension();
        }
        float density = getResources().getDisplayMetrics().density;
        spacer = (int) (density * SPACER);
        yAxisOffset = (int) (density * Y_AXIS_OFFSET);

        titleDimensions = getTitleDimensions();
        topBorder = (int) (density * BORDER + titleDimensions.lineCount * (titleDimensions.lineHeight + spacer));
        Rect xAxisLabelRect = getRect(axisPaint, getXAxisLabel());
        bottomBorder = (int) (density * BORDER + getRect(xAxisMarkerPaint, "1").height() + spacer + (xAxisLabelRect.height() / 2));

        final int markerCountOn1stLine = getYmarkerCountOn1stLine();
        int allMarkerLength = 0;
        int firstLineMarkerLength = 0;
        int secondLineMarkerLength = 0;
        int currentSeriesIndex = 0;
        maxYaxisNumberHeight = 0;
        for (ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                Rect rect = getRect(chartValueSeries.getMarkerPaint(), chartValueSeries.getLargestMarker());
                maxYaxisNumberHeight = Math.max(maxYaxisNumberHeight, rect.height() );
                int lengthInc = rect.width() + spacer;
                allMarkerLength += lengthInc;
                if (currentSeriesIndex < markerCountOn1stLine) firstLineMarkerLength += lengthInc;
                else secondLineMarkerLength += lengthInc;
                ++currentSeriesIndex;
            }
        }
        if ( currentSeriesIndex > 1 && height-topBorder-bottomBorder-spacer > (Y_AXIS_INTERVALS+1)*(spacer+maxYaxisNumberHeight*3) ) {
            allMarkerLength = Math.max(firstLineMarkerLength, secondLineMarkerLength);
            twoLineYaxisNumbers = true;
        } else twoLineYaxisNumbers = false;

        leftBorder = (int) (density * BORDER + allMarkerLength);
        rightBorder = (int) (density * BORDER + spacer);
        updateEffectiveDimensions();
    }

    /**
     * Updates the effective dimensions.
     */
    private void updateEffectiveDimensions() {
        effectiveWidth = Math.max(0, width - leftBorder - rightBorder);
        effectiveHeight = Math.max(0, height - topBorder - bottomBorder - spacer);
    }

    /**
     * Updates the effective dimensions if changed.
     *
     * @param newWidth  the new width
     * @param newHeight the new height
     */
    private void updateEffectiveDimensionsIfChanged(int newWidth, int newHeight) {
        if (width != newWidth || height != newHeight) {
            width = newWidth;
            height = newHeight;
            updateEffectiveDimensions();
            updateSeries();
        }
    }

    /**
     * Gets the x position for a value.
     *
     * @param value the value
     */
    private int getX(double value) {
        if (value > maxX) {
            value = maxX;
        }
        double percentage = value / maxX;
        return leftBorder + (int) (percentage * effectiveWidth * zoomLevel);
    }

    /**
     * Gets the y position for a value in a chart value series
     *
     * @param chartValueSeries the chart value series
     * @param value            the value
     */
    private int getY(ChartValueSeries chartValueSeries, double value) {
        int effectiveSpread = chartValueSeries.getInterval() * Y_AXIS_INTERVALS;
        double percentage = (value - chartValueSeries.getMinMarkerValue()) / effectiveSpread;
        int rangeHeight = effectiveHeight - 2 * yAxisOffset;
        return topBorder + yAxisOffset + (int) ((1 - percentage) * rangeHeight);
    }

    /**
     * Gets a paint's Rect for a string.
     *
     * @param paint  the paint
     * @param string the string
     */
    private Rect getRect(Paint paint, String string) {
        Rect rect = new Rect();
        paint.getTextBounds(string, 0, string.length(), rect);
        return rect;
    }

    /**
     * Returns true if the index is allowed when the chartData is empty.
     */
    private boolean allowIfEmpty(ChartValueSeries chartValueSeries) {
        if (!chartPoints.isEmpty()) {
            return false;
        }

        return chartValueSeries.drawIfChartPointHasNoData();
    }
}
