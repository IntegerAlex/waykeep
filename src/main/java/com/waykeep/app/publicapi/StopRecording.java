package com.waykeep.app.publicapi;

import com.waykeep.app.data.models.Track;
import com.waykeep.app.io.file.exporter.ExportUtils;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.services.TrackRecordingService;

public class StopRecording extends AbstractAPIActivity {

    protected void execute(TrackRecordingService service) {
        RecordingData recordingData = service.getRecordingDataObservable().getValue();
        Track.Id trackId = null;
        if (recordingData != null && recordingData.track() != null) {
            trackId = recordingData.track().id();
        }

        service.endCurrentTrack();

        if (trackId != null) {
            ExportUtils.postWorkoutExport(this, trackId);
        }
    }

    @Override
    protected boolean isPostExecuteStopService() {
        return true;
    }
}
