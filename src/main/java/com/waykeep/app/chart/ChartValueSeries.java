/*
 * Copyright 2009 Google Inc.
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
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.text.NumberFormat;

import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.data.statistics.ExtremityMonitor;

/**
 * This class encapsulates the meta data for one series of the chart values.
 *
 * @author Sandor Dornbush
 */
abstract class ChartValueSeries {

    private static final float STROKE_WIDTH = 2f;

    private final int absoluteMin;
    private final int absoluteMax;
    private final int[] intervalValues;
    private final int metricTitleId;
    private final int imperialTitleId;
    private final int nauticalTitleId;
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final Paint titlePaint;
    private final Paint markerPaint;
    private final ExtremityMonitor extremityMonitor = new ExtremityMonitor();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
    private final Path path = new Path();

    private int gradientTop = Integer.MIN_VALUE;
    private int gradientBottom = Integer.MIN_VALUE;

    private int interval = 1;
    private int minMarkerValue = 0;
    private int maxMarkerValue = interval * ChartView.Y_AXIS_INTERVALS;
    private boolean enabled = true;

    ChartValueSeries(Context context, int absoluteMin, int absoluteMax, int[] intervalValues, int metricTitleId, int imperialTitleId, int nauticalTitleId, int fillColor, int strokeColor, int fontSizeSmall, int fontSizeMedium) {
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
        this.intervalValues = intervalValues;
        this.metricTitleId = metricTitleId;
        this.imperialTitleId = imperialTitleId;
        this.nauticalTitleId = nauticalTitleId;

        fillPaint = new Paint();
        fillPaint.setStyle(Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, fillColor));
        fillPaint.setAntiAlias(true);

        strokePaint = new Paint();
        strokePaint.setStyle(Style.STROKE);
        strokePaint.setColor(ContextCompat.getColor(context, strokeColor));
        strokePaint.setAntiAlias(true);

        // Make copies of the stroke paint with the default thickness
        titlePaint = new Paint(strokePaint);
        titlePaint.setTextSize(fontSizeMedium);
        titlePaint.setTextAlign(Align.LEFT);
        titlePaint.setStyle(Style.FILL_AND_STROKE);

        markerPaint = new Paint(strokePaint);
        markerPaint.setTextSize(fontSizeSmall);
        markerPaint.setTextAlign(Align.RIGHT);
        markerPaint.setStyle(Style.FILL_AND_STROKE);

        // Set stroke paint thickness
        strokePaint.setStrokeWidth(STROKE_WIDTH);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Returns true if the series is enabled.
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the series enabled value.
     *
     * @param enabled true to enable
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns true if the series has data.
     */
    boolean hasData() {
        return extremityMonitor.hasData();
    }

    /**
     * Updates the series with a new {@link ChartPoint}.
     */
    void update(ChartPoint chartPoint) {
        if (isChartPointValid(chartPoint)) {
            extremityMonitor.update(extractDataFromChartPoint(chartPoint));
        }
    }

    abstract Double extractDataFromChartPoint(@NonNull ChartPoint chartPoint);

    boolean isChartPointValid(@NonNull ChartPoint chartPoint) {
        return extractDataFromChartPoint(chartPoint) != null;
    }

    protected abstract boolean drawIfChartPointHasNoData();

    Path getPath() {
        return path;
    }

    void drawPath(Canvas canvas, boolean shouldFillPathArea) {
        if (shouldFillPathArea) {
            canvas.drawPath(path, fillPaint);
        }
        canvas.drawPath(path, strokePaint);
    }

    /**
     * Rebuilds the area-fill gradient for the given graph bounds. The fill fades from a
     * translucent version of the series color at the top to fully transparent at the baseline.
     * Cached so it is only recreated when the graph height actually changes.
     */
    void updateFillGradient(int top, int bottom) {
        if (top == gradientTop && bottom == gradientBottom && fillPaint.getShader() != null) {
            return;
        }
        gradientTop = top;
        gradientBottom = bottom;
        int rgb = strokePaint.getColor() & 0x00FFFFFF;
        fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
                (0x55 << 24) | rgb, rgb, Shader.TileMode.CLAMP));
    }

    int getStrokeColor() {
        return strokePaint.getColor();
    }

    /**
     * Formats a raw series value for display in the inspection readout.
     * Overridden by series that need more than integer precision.
     */
    String formatValue(double value) {
        return numberFormat.format(value);
    }

    /**
     * Updates the y axis dimension.
     */
    void updateDimension() {
        double min = hasData() ? extremityMonitor.getMin() : 0.0;
        double max = hasData() ? extremityMonitor.getMax() : 1.0;
        min = Math.max(min, absoluteMin);
        max = Math.min(max, absoluteMax);
        interval = getInterval(min, max);
        minMarkerValue = getMinMarkerValue(min, interval);
        maxMarkerValue = minMarkerValue + interval * ChartView.Y_AXIS_INTERVALS;
    }

    /**
     * Gets the interval value.
     *
     * @param min the min value
     * @param max the max value
     */
    private int getInterval(double min, double max) {
        for (int intervalValue : intervalValues) {
            int minValue = getMinMarkerValue(min, intervalValue);
            double targetInterval = (max - minValue) / ChartView.Y_AXIS_INTERVALS;
            if (intervalValue >= targetInterval) {
                return intervalValue;
            }
        }
        // Return the largest interval
        return intervalValues[intervalValues.length - 1];
    }

    /**
     * Gets the min marker value.
     *
     * @param min           the min series value
     * @param intervalValue the interval value
     */
    private int getMinMarkerValue(double min, int intervalValue) {
        // Round down to the nearest intervalValue
        int value = ((int) (min / intervalValue)) * intervalValue;
        // value > min if min is negative
        if (value > min) {
            return value - intervalValue;
        }
        return value;
    }

    /**
     * Gets the interval value.
     */
    int getInterval() {
        return interval;
    }

    /**
     * Gets the min marker value.
     */
    int getMinMarkerValue() {
        return minMarkerValue;
    }

    /**
     * Gets the max marker value.
     */
    int getMaxMarkerValue() {
        return maxMarkerValue;
    }

    int getTitleId(UnitSystem unitSystem) {
        return switch (unitSystem) {
            case METRIC -> metricTitleId;
            case IMPERIAL_FEET, IMPERIAL_METER -> imperialTitleId;
            case NAUTICAL_IMPERIAL -> nauticalTitleId;
        };
    }

    Paint getTitlePaint() {
        return titlePaint;
    }

    Paint getMarkerPaint() {
        return markerPaint;
    }

    String getLargestMarker() {
        String minMarker = numberFormat.format(getMinMarkerValue());
        String maxMarker = numberFormat.format(getMaxMarkerValue());
        return minMarker.length() >= maxMarker.length() ? minMarker : maxMarker;
    }

    String formatMarker(int value) {
        return numberFormat.format(value);
    }
}
