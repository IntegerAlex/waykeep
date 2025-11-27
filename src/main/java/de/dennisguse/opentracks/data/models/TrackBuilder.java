/*
 * Copyright 2008 Google Inc.
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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A track.
 *
 * @author Leif Hendrik Wilden
 * @author Rodrigo Damazio
 */
//TODO Do not default initialize attributes; might be confusing for debugging
public class TrackBuilder {

    private Track.Id id;
    private UUID uuid = UUID.randomUUID();

    private String name = "";
    private String description = "";
    private String activityTypeLocalized = "";

    private ActivityType activityType;

    @NonNull
    private ZoneOffset zoneOffset;

    @NonNull
    private Statistics trackStatistics;

    @Deprecated
    @VisibleForTesting
    public TrackBuilder(Statistics statistics) {
        this(ZoneOffset.UTC, statistics);
    }

    @Deprecated
    public TrackBuilder(@NonNull ZoneOffset zoneOffset, @NonNull Statistics trackStatistics) {
        this.zoneOffset = zoneOffset;
        this.trackStatistics = trackStatistics;
    }

    public TrackBuilder(Track track) {
        this.id = track.id();
        this.uuid = track.uuid();
        this.name = track.name();
        this.description = track.description();
        this.activityTypeLocalized = track.activityTypeLocalized();
        this.activityType = track.activityType();
        this.zoneOffset = track.zoneOffset();
        this.trackStatistics = track.statistics();
    }

    /**
     * May be null if the track was not loaded from the database.
     */
    @Nullable
    @Deprecated
    public Track.Id getId() {
        return id;
    }

    public void setId(Track.Id id) {
        this.id = id;
    }

    @Deprecated
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Deprecated
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Deprecated
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Deprecated
    public String getActivityTypeLocalized() {
        return activityTypeLocalized;
    }

    public void setActivityTypeLocalized(String activityTypeLocalized) {
        this.activityTypeLocalized = activityTypeLocalized;
    }

    @Deprecated
    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public void setActivityTypeLocalizedAndUpdateActivityType(Context context, String activityTypeLocalized) {
        setActivityTypeLocalized(activityTypeLocalized);
        setActivityType(ActivityType.findByLocalizedString(context, activityTypeLocalized));
    }

    @Deprecated
    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }

    public void setZoneOffset(@NonNull ZoneOffset zoneOffset) {
        this.zoneOffset = zoneOffset;
    }

    @Deprecated
    public OffsetDateTime getStartTime() {
        return trackStatistics
                .startTime().atOffset(zoneOffset);
    }

    @Deprecated
    public OffsetDateTime getStopTime() {
        return trackStatistics
                .stopTime().atOffset(zoneOffset);
    }

    @Deprecated
    public Statistics getStatistics() {
        return trackStatistics;
    }

    public void setStatistics(Statistics trackStatistics) {
        this.trackStatistics = trackStatistics;
    }

    public Track getTrack() {
        return new Track(
                id,
                uuid,
                name,
                description,
                activityTypeLocalized,
                activityType,
                zoneOffset,
                trackStatistics
        );
    }

    @NonNull
    @Override
    public String toString() {
        return "Track{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", activityTypeLocalized='" + activityTypeLocalized + '\'' +
                ", activityType=" + activityType +
                '}';
    }

}
