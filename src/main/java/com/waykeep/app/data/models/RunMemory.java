package com.waykeep.app.data.models;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * How a recorded run felt: one mood (0 rough .. 4 great, -1 unset) and optional
 * photos (file names inside the track's photo directory; first is the cover).
 * One memory per track.
 */
public record RunMemory(Track.Id trackId, int mood, @NonNull List<String> photoNames) {

    public static final int MOOD_UNSET = -1;
    public static final int MOOD_MIN = 0;
    public static final int MOOD_MAX = 4;
}
