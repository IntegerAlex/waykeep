package com.waykeep.app.share;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.ShareContentProvider;
import com.waykeep.app.data.models.Marker;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.io.file.TrackFileFormat;
import com.waykeep.app.settings.PreferencesUtils;

public class ShareUtils {

    private static final String TAG = ShareUtils.class.getSimpleName();

    private ShareUtils() {
    }


    /**
     * Creates an intent to share a track file with an app.
     *
     * @param context  the context
     * @param trackIds the track ids
     */
    public static Intent newShareFileIntent(Context context, Track.Id... trackIds) {
        if (trackIds.length == 0) {
            throw new RuntimeException("Need to share at least one track.");
        }

        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);

        String trackDescription = "";
        if (trackIds.length == 1) {
            Track track = contentProviderUtils.getTrack(trackIds[0]);
            trackDescription = track == null ? "" : new TrackShareSummaryGenerator(context).generateTrackDescription(track);
        }

        String mime = "";
        ArrayList<Uri> uris = new ArrayList<>();
        for (Track.Id trackId : trackIds) {
            Track track = contentProviderUtils.getTrack(trackId);
            if (track == null) {
                Log.e(TAG, "TrackId " + trackId.id() + " could not be resolved.");
                continue;
            }

            TrackFileFormat format = PreferencesUtils.getExportTrackFileFormat();
            String trackName = PreferencesUtils.getTrackFileformatGenerator().format(track, format);
            Pair<Uri, String> uriAndMime = ShareContentProvider.createURI(trackId, trackName, format);

            uris.add(uriAndMime.first);
            mime = uriAndMime.second;
        }

        // #2405: single track must use ACTION_SEND with one EXTRA_STREAM, otherwise apps whose
        // filter only accepts ACTION_SEND_MULTIPLE for application/* (e.g. Signal) are excluded
        // from the chooser.
        Intent intent = uris.size() == 1
                ? new Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_STREAM, uris.get(0))
                : new Intent(Intent.ACTION_SEND_MULTIPLE)
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        return intent
                .setType(mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_track_subject))
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_track_share_file_body, trackDescription));
    }

    /**
     * Creates an intent to share a track with an AI app for analysis.
     * Always exports GPX (most parseable format); the analysis prompt travels in EXTRA_TEXT,
     * and callers should also copy it to the clipboard since some apps drop text extras
     * when a stream is present.
     */
    public static Intent newShareAiIntent(Context context, Track.Id trackId) {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        Track track = contentProviderUtils.getTrack(trackId);
        if (track == null) {
            throw new RuntimeException("TrackId " + trackId.id() + " could not be resolved.");
        }

        TrackFileFormat format = TrackFileFormat.GPX;
        String trackName = PreferencesUtils.getTrackFileformatGenerator().format(track, format);
        Pair<Uri, String> uriAndMime = ShareContentProvider.createURI(trackId, trackName, format);

        return new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, uriAndMime.first)
                .setType(uriAndMime.second)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_track_subject))
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_ai_prompt));
    }

    /**
     * Creates an intent to share a track file with an app.
     *
     * @param context   the context
     * @param markerIds the marker ids
     * @return an Intent or null (if nothing can be shared).
     */
    @Nullable
    public static Intent newShareFileIntent(Context context, Marker.Id... markerIds) {
        if (markerIds.length == 0) {
            throw new RuntimeException("Need to share at least one marker.");
        }

        String mime = null;

        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        ArrayList<Uri> uris = new ArrayList<>();
        for (Marker.Id markerId : markerIds) {
            Marker marker = contentProviderUtils.getMarker(markerId);
            if (marker == null) {
                Log.e(TAG, "MarkerId " + markerId.id() + " could not be resolved.");
                continue;
            }
            if (marker.photoUrl() == null) {
                Log.e(TAG, "MarkerId " + markerId.id() + " has no picture.");
                continue;
            }

            mime = context.getContentResolver().getType(marker.photoUrl());

            uris.add(marker.photoUrl());
        }

        if (uris.isEmpty()) {
            return null;
        }

        /*
         * Because the #166 bug, when you import KMZ tracks then it creates file:/// from markers with photo.
         * The photos should be content:/// not file:/// because getType(uri) always returns null for file:///
         * In .setType, to avoid side effects because the #166 bug described above it checks if mime is null.
         * If it is then it hardcode "images/*".
         */
        return new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(mime != null ? mime : "image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_image_subject))
                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_image_body));
    }

}
