package com.waykeep.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waykeep.app.data.models.Track;

public record RecordingStatus(@Nullable Track.Id trackId, boolean paused) {

    public RecordingStatus(@Nullable Track.Id trackId) {
        this(trackId, false);
    }

    static RecordingStatus record(@NonNull Track.Id trackId) {
        return new RecordingStatus(trackId, false);
    }

    static RecordingStatus paused(@NonNull Track.Id trackId) {
        return new RecordingStatus(trackId, true);
    }

    public boolean isRecording() {
        return trackId != null;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isRecordingAndNotPaused() {
        return isRecording() && !paused;
    }

    RecordingStatus pause() {
        return new RecordingStatus(trackId, true);
    }

    static RecordingStatus notRecording() {
        return new RecordingStatus(null);
    }

    public RecordingStatus stop() {
        return TrackRecordingService.STATUS_DEFAULT;
    }
}
