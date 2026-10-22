package com.waykeep.app.sensors;

import com.waykeep.app.R;

/**
 * For each status value set an icon and id message.
 */
public enum GpsStatusValue {
    GPS_NONE(R.drawable.ic_gps_off_24dp, R.string.gps_disabled_msg),
    GPS_ENABLED(R.drawable.ic_gps_not_fixed_24dp, R.string.gps_wait_for_signal),
    GPS_DISABLED(R.drawable.ic_gps_off_24dp, R.string.gps_disabled_msg),
    GPS_SIGNAL_FIX(R.drawable.ic_gps_fixed_24dp, R.string.gps_fixed_and_ready),
    GPS_SIGNAL_BAD(R.drawable.ic_gps_ready_animation, R.string.gps_wait_for_better_signal),
    GPS_SIGNAL_LOST(R.drawable.ic_gps_not_fixed_24dp, R.string.gps_wait_for_signal);

    public final int icon;
    public final int message;

    GpsStatusValue(int icon, int message) {
        this.icon = icon;
        this.message = message;
    }

    public boolean isGpsStarted() {
        return this != GpsStatusValue.GPS_NONE && this != GpsStatusValue.GPS_DISABLED;
    }

    /**
     * @return true if the position data cannot be used for recording. The distance is not
     * counting then: either the accuracy is too poor (the positions are dropped) or no
     * position arrived at all.
     */
    public boolean isSignalUnusable() {
        return this == GpsStatusValue.GPS_SIGNAL_BAD || this == GpsStatusValue.GPS_SIGNAL_LOST;
    }

    /**
     * @return true if the received positions are good enough to be recorded.
     */
    public boolean hasGoodSignal() {
        return this == GpsStatusValue.GPS_SIGNAL_FIX;
    }
}
