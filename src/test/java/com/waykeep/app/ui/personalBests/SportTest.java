package com.waykeep.app.ui.personalBests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Statistics;
import com.waykeep.app.data.models.Track;

public class SportTest {

    @Test
    public void activityTypesMapToTheirSport() {
        assertEquals(Sport.RUNNING, Sport.of(ActivityType.RUNNING));
        assertEquals(Sport.RUNNING, Sport.of(ActivityType.TRAIL_RUNNING));
        assertEquals(Sport.CYCLING, Sport.of(ActivityType.CYCLING));
        assertEquals(Sport.CYCLING, Sport.of(ActivityType.ROAD_BIKING));
        assertEquals(Sport.WALKING, Sport.of(ActivityType.WALKING));
        assertEquals(Sport.WALKING, Sport.of(ActivityType.HIKING));
        assertNull(Sport.of(ActivityType.SWIMMING));
        assertNull(Sport.of(ActivityType.UNKNOWN));
    }

    @Test
    public void runningAndWalkingUsePaceWhileCyclingUsesSpeed() {
        assertFalse(Sport.RUNNING.prefersSpeed());
        assertFalse(Sport.WALKING.prefersSpeed());
        assertTrue(Sport.CYCLING.prefersSpeed());
    }

    @Test
    public void fastestRecordIsChosenWithinTheSportOnly() {
        // The ride is far faster than the run, so it must not become the fastest run.
        Track run = track(1, ActivityType.RUNNING, 10_800, 3_600, 4.0);
        Track ride = track(2, ActivityType.CYCLING, 43_200, 3_600, 15.0);
        List<Track> tracks = List.of(run, ride);

        assertSame(run, Sport.RUNNING.bestsIn(tracks).fastestAverage());
        assertSame(ride, Sport.CYCLING.bestsIn(tracks).fastestAverage());
        assertSame(run, Sport.RUNNING.bestsIn(tracks).topSpeed());
        assertSame(ride, Sport.CYCLING.bestsIn(tracks).topSpeed());
    }

    @Test
    public void sportsWithoutActivitiesHaveNoRecords() {
        List<Track> tracks = List.of(track(1, ActivityType.SWIMMING, 1_000, 1_800, 1.5));

        Sport.BestTracks bests = Sport.RUNNING.bestsIn(tracks);

        assertNull(bests.longestDistance());
        assertNull(bests.longestTime());
        assertNull(bests.fastestAverage());
        assertNull(bests.mostElevation());
        assertNull(bests.topSpeed());
    }

    private static Track track(long id, ActivityType type, double meters, long movingSeconds, double maxSpeedMps) {
        Statistics statistics = new Statistics(
                Instant.EPOCH,
                Instant.EPOCH,
                Duration.ofSeconds(movingSeconds),
                Duration.ofSeconds(movingSeconds),
                Distance.of(meters),
                Speed.of(maxSpeedMps),
                null,
                null,
                null,
                null);
        return new Track(new Track.Id(id), new UUID(0, id), "track " + id, "",
                type.getId(), type, ZoneOffset.UTC, statistics);
    }
}
