package com.waykeep.app.ui.personalBests;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.waykeep.app.R;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Track;

/**
 * Groups the activity types into the sports whose records are tracked separately.
 *
 * <p>Records are only ever compared within one sport: a ride can never be the fastest run.
 * Keeping this logic free of Android UI permits unit testing.
 */
public enum Sport {

    RUNNING(R.string.sport_running, false,
            ActivityType.RUNNING, ActivityType.STREET_RUNNING, ActivityType.TRAIL_RUNNING,
            ActivityType.TRACK_RUNNING),
    CYCLING(R.string.sport_cycling, true,
            ActivityType.BIKING, ActivityType.CYCLING, ActivityType.GRAVEL_BIKING,
            ActivityType.MOUNTAIN_BIKING, ActivityType.ROAD_BIKING, ActivityType.TRACK_CYCLING),
    WALKING(R.string.sport_walking, false,
            ActivityType.WALKING, ActivityType.SPEED_WALKING, ActivityType.HIKING,
            ActivityType.TRAIL_HIKING, ActivityType.OFF_TRAIL_HIKING);

    /**
     * The best track of a sport for each record. Fields are null when no track qualifies.
     */
    public record BestTracks(
            @Nullable Track longestDistance,
            @Nullable Track longestTime,
            @Nullable Track fastestAverage,
            @Nullable Track mostElevation,
            @Nullable Track topSpeed) {
    }

    private final int labelResId;
    private final boolean speedPreferred;
    private final List<ActivityType> activityTypes;

    Sport(int labelResId, boolean speedPreferred, ActivityType... activityTypes) {
        this.labelResId = labelResId;
        this.speedPreferred = speedPreferred;
        this.activityTypes = List.of(activityTypes);
    }

    public int getLabelResId() {
        return labelResId;
    }

    /**
     * Whether this sport reads better as speed (km/h) than as pace (min/km).
     */
    public boolean prefersSpeed() {
        return speedPreferred;
    }

    /**
     * @return the sport the activity type belongs to, or null if records are not tracked for it.
     */
    @Nullable
    public static Sport of(@NonNull ActivityType activityType) {
        for (Sport sport : values()) {
            if (sport.activityTypes.contains(activityType)) {
                return sport;
            }
        }
        return null;
    }

    /**
     * Computes the record tracks among the given tracks, ignoring all other sports.
     */
    @NonNull
    public BestTracks bestsIn(@NonNull List<Track> tracks) {
        Track longestDistance = null;
        Track longestTime = null;
        Track fastestAverage = null;
        Track mostElevation = null;
        Track topSpeed = null;
        for (Track track : tracks) {
            if (of(track.activityType()) != this) {
                continue;
            }
            if (longestDistance == null || track.statistics().totalDistance().greaterThan(longestDistance.statistics().totalDistance())) {
                longestDistance = track;
            }
            if (longestTime == null || track.statistics().movingDuration().compareTo(longestTime.statistics().movingDuration()) > 0) {
                longestTime = track;
            }
            if (fastestAverage == null || track.statistics().getAverageMovingSpeed().greaterThan(fastestAverage.statistics().getAverageMovingSpeed())) {
                fastestAverage = track;
            }
            if (gainOf(track) != null && (mostElevation == null || gainOf(track) > gainOf(mostElevation))) {
                mostElevation = track;
            }
            if (topSpeed == null || track.statistics().maxSpeed().greaterThan(topSpeed.statistics().maxSpeed())) {
                topSpeed = track;
            }
        }
        return new BestTracks(longestDistance, longestTime, fastestAverage, mostElevation, topSpeed);
    }

    private static Float gainOf(Track track) {
        return track.statistics().altitudeGainLoss() != null ? track.statistics().altitudeGainLoss().gain_m() : null;
    }
}
