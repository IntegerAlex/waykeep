package com.waykeep.app.data.models;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.waykeep.app.R;
import com.waykeep.app.ui.util.ThemeUtils;

public class HeartRateZones {

    private final HeartRate max;

    public HeartRateZones(@NonNull HeartRate max) {
        this.max = max;
    }

    public HeartRate getMax() {
        return max;
    }

    /**
     * Returns the zone (1-5) for a heart rate, or 0 when unknown.
     * Thresholds match {@link #getTextColorForZone}.
     */
    public int getZone(HeartRate current) {
        if (current == null || !current.isValid() || max == null || !max.isValid()) {
            return 0;
        }
        if (current.getBPM() >= max.getBPM() * 0.9) {
            return 5;
        }
        if (current.getBPM() >= max.getBPM() * 0.8) {
            return 4;
        }
        if (current.getBPM() >= max.getBPM() * 0.7) {
            return 3;
        }
        if (current.getBPM() >= max.getBPM() * 0.6) {
            return 2;
        }
        return 1;
    }

    public int getTextColorForZone(Context context, HeartRate current) {
        if (current != null) {
            if (current.getBPM() >= max.getBPM() * 0.9) {
                return ContextCompat.getColor(context, R.color.heart_rate_zone_textcolor_5);
            }
            if (current.getBPM() >= max.getBPM() * 0.8) {
                return ContextCompat.getColor(context, R.color.heart_rate_zone_textcolor_4);
            }
            if (current.getBPM() >= max.getBPM() * 0.7) {
                return ContextCompat.getColor(context, R.color.heart_rate_zone_textcolor_3);
            }
            if (current.getBPM() >= max.getBPM() * 0.6) {
                return ContextCompat.getColor(context, R.color.heart_rate_zone_textcolor_2);
            }
        }

        // Zone 1
        return ThemeUtils.getTextColorPrimary(context);
    }
}
