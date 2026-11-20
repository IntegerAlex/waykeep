package com.waykeep.app.data.models;

import androidx.annotation.Nullable;

/**
 * How a recorded run felt: one mood (0 rough .. 4 great, -1 unset) and an optional
 * photo (file name inside the track's photo directory). One memory per track.
 */
public record RunMemory(Track.Id trackId, int mood, @Nullable String photoName) {

    public static final int MOOD_UNSET = -1;
    public static final int MOOD_MIN = 0;
    public static final int MOOD_MAX = 4;
}
