package com.waykeep.app.services;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.waykeep.app.data.models.HeartRate;

/**
 * One-shot high-heart-rate state machine.
 *
 * <p>Fires once after the heart rate has stayed at or above the alert threshold for
 * {@code aboveFor}. It only arms again after {@code rearmBelowFor} of continuous valid
 * readings below {@code rearmBpm}. Missing, stale, or invalid readings break continuity:
 * they never fire and they reset the running timers.
 *
 * <p>Pure and main-thread friendly: it only compares the supplied {@link Instant} values.
 *
 * <p>Related to Codeberg issue #2350 (heart rate zone alerts); this class implements the
 * narrower beta.6 scope: one high-heart-rate reminder per crossing.
 */
public final class HeartRateAlert {

    private final float thresholdBpm;
    private final float rearmBpm;
    private final Duration aboveFor;
    private final Duration rearmBelowFor;

    private boolean armed = true;
    @Nullable
    private Instant aboveSince;
    @Nullable
    private Instant belowSince;

    public HeartRateAlert(int thresholdBpm, int rearmBpm, Duration aboveFor, Duration rearmBelowFor) {
        this.thresholdBpm = thresholdBpm;
        this.rearmBpm = rearmBpm;
        this.aboveFor = Objects.requireNonNull(aboveFor, "aboveFor");
        this.rearmBelowFor = Objects.requireNonNull(rearmBelowFor, "rearmBelowFor");
    }

    /**
     * Feeds one heart-rate reading.
     *
     * @return true only on the single update that crosses the alert threshold.
     */
    public boolean update(@Nullable HeartRate heartRate, Instant now) {
        Objects.requireNonNull(now, "now");

        if (!isUsable(heartRate)) {
            aboveSince = null;
            belowSince = null;
            return false;
        }

        float bpm = heartRate.getBPM();
        if (!armed) {
            if (bpm >= thresholdBpm) {
                belowSince = null;
                return false;
            }
            if (bpm < rearmBpm) {
                if (belowSince == null) {
                    belowSince = now;
                }
                if (!now.isBefore(belowSince.plus(rearmBelowFor))) {
                    armed = true;
                    belowSince = null;
                }
            } else {
                belowSince = null;
            }
            return false;
        }

        if (bpm < thresholdBpm) {
            aboveSince = null;
            return false;
        }

        if (aboveSince == null) {
            aboveSince = now;
        }
        if (!now.isBefore(aboveSince.plus(aboveFor))) {
            armed = false;
            aboveSince = null;
            belowSince = null;
            return true;
        }
        return false;
    }

    private static boolean isUsable(@Nullable HeartRate heartRate) {
        return heartRate != null
                && heartRate.isValid()
                && Float.isFinite(heartRate.getBPM())
                && heartRate.getBPM() > 0f;
    }
}
