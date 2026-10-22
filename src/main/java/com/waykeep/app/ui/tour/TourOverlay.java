package com.waykeep.app.ui.tour;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

import com.waykeep.app.R;

/**
 * Driver.js-style spotlight tour: dims the screen, cuts a hole around a target view and shows
 * a tooltip next to it. Used once to point out the GPS button and the record button.
 *
 * <p>Plain {@link View}/{@link Canvas} drawing on purpose: no tour dependency.
 */
public class TourOverlay extends FrameLayout {

    public record Step(int targetViewId, String title, String body) {
    }

    public interface OnFinish {
        void onFinish();
    }

    private static final int SCRIM_COLOR = 0xB3000000;

    private final Activity activity;
    private final List<Step> steps;
    private final OnFinish onFinish;

    private final SpotlightView spotlightView;
    private final MaterialCardView tooltip;
    private final TextView title;
    private final TextView body;
    private final MaterialButton next;

    private int index = 0;

    public TourOverlay(@NonNull Activity activity, @NonNull List<Step> steps, @Nullable OnFinish onFinish) {
        super(activity);
        this.activity = activity;
        this.steps = steps;
        this.onFinish = onFinish;

        setClickable(true); // modal: taps outside the tooltip are swallowed

        spotlightView = new SpotlightView(activity);
        addView(spotlightView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View.inflate(activity, R.layout.tour_tooltip, this);
        tooltip = findViewById(R.id.tour_tooltip);
        title = tooltip.findViewById(R.id.tour_title);
        body = tooltip.findViewById(R.id.tour_body);
        next = tooltip.findViewById(R.id.tour_next);
        MaterialButton skip = tooltip.findViewById(R.id.tour_skip);

        next.setOnClickListener(v -> advance());
        skip.setOnClickListener(v -> finish());
    }

    public void show() {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) {
            finish();
            return;
        }
        root.addView(this, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // The targets need a laid-out view hierarchy to have a position.
        post(() -> showStep(0));
    }

    private void showStep(int i) {
        if (i >= steps.size()) {
            finish();
            return;
        }
        index = i;
        Step step = steps.get(i);
        View target = activity.findViewById(step.targetViewId());
        if (target == null) {
            finish();
            return;
        }

        Rect spotlight = targetRect(target);
        spotlightView.setSpotlight(spotlight);
        title.setText(step.title());
        body.setText(step.body());
        next.setText(i == steps.size() - 1 ? getContext().getString(R.string.tour_done) : getContext().getString(R.string.tour_next));
        positionTooltip(spotlight);
    }

    private void advance() {
        if (index + 1 < steps.size()) {
            showStep(index + 1);
        } else {
            finish();
        }
    }

    private void finish() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
        if (onFinish != null) {
            onFinish.onFinish();
        }
    }

    private Rect targetRect(@NonNull View target) {
        int[] targetLocation = new int[2];
        int[] overlayLocation = new int[2];
        target.getLocationInWindow(targetLocation);
        getLocationInWindow(overlayLocation);

        int padding = dp(8);
        int left = targetLocation[0] - overlayLocation[0];
        int top = targetLocation[1] - overlayLocation[1];
        return new Rect(left - padding, top - padding, left + target.getWidth() + padding, top + target.getHeight() + padding);
    }

    private void positionTooltip(@NonNull Rect spotlight) {
        int margin = dp(16);
        int gap = dp(12);
        int maxWidth = getWidth() - 2 * margin;

        tooltip.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int tooltipWidth = tooltip.getMeasuredWidth();
        int tooltipHeight = tooltip.getMeasuredHeight();

        int x = Math.max(margin, (getWidth() - tooltipWidth) / 2);
        int y = spotlight.top - tooltipHeight - gap;
        if (y < margin) {
            // No room above the target: put the tooltip below it.
            y = spotlight.bottom + gap;
        }
        y = Math.min(y, getHeight() - margin - tooltipHeight);

        LayoutParams params = (LayoutParams) tooltip.getLayoutParams();
        params.leftMargin = x;
        params.topMargin = y;
        tooltip.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Dims everything except a rounded hole around the current target.
     */
    private static class SpotlightView extends View {

        private final Rect spotlight = new Rect();
        private final RectF spotlightRect = new RectF();
        private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cornerRadius;

        SpotlightView(Context context) {
            super(context);
            cornerRadius = 16 * context.getResources().getDisplayMetrics().density;

            holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2 * context.getResources().getDisplayMetrics().density);
            strokePaint.setColor(0xFFFFFFFF);
        }

        void setSpotlight(Rect rect) {
            spotlight.set(rect);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (spotlight.isEmpty()) {
                canvas.drawColor(SCRIM_COLOR);
                return;
            }
            spotlightRect.set(spotlight);

            // Draw the scrim, punch the hole, then stroke its outline.
            int layer = canvas.saveLayer(null, null);
            canvas.drawColor(SCRIM_COLOR);
            canvas.drawRoundRect(spotlightRect, cornerRadius, cornerRadius, holePaint);
            canvas.restoreToCount(layer);

            canvas.drawRoundRect(spotlightRect, cornerRadius, cornerRadius, strokePaint);
        }
    }
}