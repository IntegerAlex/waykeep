package com.waykeep.app.util;

import android.content.Context;

import com.waykeep.app.R;
import com.waykeep.app.data.models.AltitudeGainLoss;

public final class AltitudeGainLossSourceLabels {

    private AltitudeGainLossSourceLabels() {
    }

    public static int resource(AltitudeGainLoss.Source source) {
        if (source == null) source = AltitudeGainLoss.Source.UNKNOWN;
        return switch (source) {
            case BAROMETER -> R.string.gps_altitude_source_barometer;
            case GPS_ESTIMATE -> R.string.gps_altitude_source_estimate;
            case MIXED -> R.string.gps_altitude_source_mixed;
            case UNKNOWN -> R.string.gps_altitude_source_unknown;
        };
    }

    public static String text(Context context, AltitudeGainLoss.Source source) {
        return context.getString(resource(source));
    }
}
