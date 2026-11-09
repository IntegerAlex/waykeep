package com.waykeep.app.ui.insights;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.waykeep.app.R;

/**
 * The home week lane: a horizontal track that carries the seven-day distance toward the goal.
 *
 * <p>Draws the track, the filled distance, a dashed four-week baseline, a finish line and a
 * runner marker that animates to the current distance. The numeric values live in TextViews
 * around it, so the lane is a visual summary, not the accessible source of truth.</p>
 */
public class WeekLaneView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint averagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF lane = new RectF();

    private double currentM;
    private double goalM;
    private double averageM;
    private float progress;
    @Nullable
    private ValueAnimator animator;

    public WeekLaneView(Context context) {
        this(context, null);
    }

    public WeekLaneView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeekLaneView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;

        trackPaint.setColor(ContextCompat.getColor(context, R.color.track_lane));
        fillPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0));
        surfacePaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, 0));
        averagePaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        averagePaint.setAlpha(150);
        averagePaint.setStyle(Paint.Style.STROKE);
        averagePaint.setStrokeWidth(2 * density);
        averagePaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));
        tickPaint.setColor(ContextCompat.getColor(context, R.color.track_outline_variant));
    }

    /**
     * Updates the lane and animates the runner to the new position. Safe to call repeatedly.
     */
    public void setValues(double currentM, double goalM, double averageM) {
        this.currentM = sanitize(currentM);
        this.goalM = sanitize(goalM);
        this.averageM = sanitize(averageM);

        float target = goalM > 0 ? (float) Math.max(0, Math.min(1, currentM / goalM)) : 0f;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (goalM <= 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float laneHeight = 10 * density;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = (getHeight() - laneHeight) / 2f;
        float bottom = top + laneHeight;
        float radius = laneHeight / 2f;
        float width = Math.max(0, right - left);

        lane.set(left, top, right, bottom);
        canvas.drawRoundRect(lane, radius, radius, trackPaint);

        if (progress > 0) {
            lane.set(left, top, left + width * progress, bottom);
            canvas.drawRoundRect(lane, radius, radius, fillPaint);
        }

        // Quarter ticks, like lane markings.
        float tickTop = top - 4 * density;
        for (int i = 0; i <= 4; i++) {
            float x = left + width * i / 4f;
            canvas.drawRect(x - density, tickTop, x + density, top, tickPaint);
        }

        // Four-week baseline: a ghost marker, not a second progress bar.
        float averageX = left + width * (float) Math.max(0, Math.min(1, averageM / goalM));
        canvas.drawLine(averageX, top - 8 * density, averageX, bottom + 8 * density, averagePaint);

        // Finish line.
        canvas.drawRect(right - 2 * density, top - 10 * density, right, bottom + 10 * density, fillPaint);

        // Runner marker: a cyan dot sitting in a surface-coloured halo.
        float markerX = left + width * progress;
        float markerY = (top + bottom) / 2f;
        canvas.drawCircle(markerX, markerY, 11 * density, surfacePaint);
        canvas.drawCircle(markerX, markerY, 7 * density, fillPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}
