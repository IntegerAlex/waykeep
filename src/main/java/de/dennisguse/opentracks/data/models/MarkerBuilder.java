/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.data.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;
import java.util.Objects;

/**
 * NOTE: A marker is indirectly (via it's {@link Position}) assigned to one {@link TrackPoint} via position.time.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
//TODO All data should be final; no default values.
public final class MarkerBuilder {

    private Marker.Id id;
    private String name;
    private String description;
    private String category;
    private String icon;
    private final Track.Id trackId;

    //Some data might not be used.
    private final Position position;

    private Uri photoUrl = null;

    @VisibleForTesting
    public MarkerBuilder(@Nullable Track.Id trackId, @NonNull TrackPoint trackPoint) {
        this.id = null;
        this.trackId = trackId;
        this.name = "";
        this.description = "";
        this.category = "";
        this.icon = "";
        this.photoUrl = null;

        if (!trackPoint.hasLocation())
            throw new RuntimeException("Marker requires a trackpoint with a location.");

        this.position = trackPoint.getPosition();
    }

    public MarkerBuilder(@Nullable Track.Id trackId, @NonNull TrackPoint trackPoint, String name, String description, String category, String icon, Uri photoUrl) {
        this(trackId, trackPoint);
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.photoUrl = photoUrl;
    }

    public MarkerBuilder(Marker marker) {
        this.id = marker.id();
        this.trackId = marker.trackId();
        this.name = marker.name();
        this.description = marker.description();
        this.category = marker.category();
        this.icon = marker.icon();
        this.position = marker.position();
        this.photoUrl = marker.photoUrl();
    }

    /**
     * May be null if the Marker was not loaded from the database.
     */
    @Nullable
    public Marker.Id getId() {
        return id;
    }

    //TODO Remove
    @VisibleForTesting
    @Deprecated
    public void setId(Marker.Id id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Track.Id getTrackId() {
        return trackId;
    }

    public Uri getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(Uri photoUrl) {
        this.photoUrl = photoUrl;
    }

    public boolean hasPhoto() {
        return photoUrl != null;
    }

    public Marker getMarker() {
        return new Marker(
                id,
                trackId,
                name,
                description,
                category,
                icon,
                position,
                photoUrl
        );
    }
}
