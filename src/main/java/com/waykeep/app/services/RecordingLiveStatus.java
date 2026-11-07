package com.waykeep.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.waykeep.app.data.models.Distance;

final class RecordingLiveStatus {

    private static final int LOW_BATTERY_THRESHOLD_PERCENT = 15;

    private RecordingLiveStatus() {}

    @Nullable
    static String formatAccuracy(@Nullable Distance accuracy, @NonNull String format) {
        if (accuracy == null || accuracy.isInvalid() || accuracy.toM() < 0) {
            return null;
        }
        return String.format(Locale.getDefault(), format, Math.round(accuracy.toM()));
    }

    static int warningBatteryPercent(int level, int scale, boolean charging) {
        if (level < 0 || scale <= 0 || charging || (long) level * 100 > (long) scale * LOW_BATTERY_THRESHOLD_PERCENT) {
            return -1;
        }
        return Math.round(level * 100f / scale);
    }
}
