package com.waykeep.app.ui.util;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Click-time haptics that follow the system touch-feedback setting.
 *
 * <p>Action-oriented constants let the platform pick an effect appropriate for
 * the device and give older devices a single virtual-key fallback.</p>
 */
public final class UiHaptics {

    public enum Action {
        TAP, START, PAUSE, STOP
    }

    private static boolean customFeedbackPlayed;

    private UiHaptics() {
    }

    /** Called on ACTION_DOWN so one gesture can play at most one feedback. */
    public static void beginTap() {
        customFeedbackPlayed = false;
    }

    public static boolean wasCustomFeedbackPlayed() {
        return customFeedbackPlayed;
    }

    public static boolean isTap(float dx, float dy, long durationMillis, int touchSlop) {
        return durationMillis <= 500 && Math.hypot(dx, dy) <= touchSlop;
    }

    static int semanticFeedback(@NonNull Action action) {
        return switch (action) {
            case TAP -> HapticFeedbackConstants.VIRTUAL_KEY;
            case START -> HapticFeedbackConstants.TOGGLE_ON;
            case PAUSE -> HapticFeedbackConstants.CONFIRM;
            case STOP -> HapticFeedbackConstants.TOGGLE_OFF;
        };
    }

    public static void perform(@NonNull View view, @NonNull Action action) {
        int constant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? semanticFeedback(action)
                : HapticFeedbackConstants.VIRTUAL_KEY;
        view.performHapticFeedback(constant);
        if (action != Action.TAP) {
            customFeedbackPlayed = true;
        }
    }
}
