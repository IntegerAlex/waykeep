/*
 * Copyright 2010 Google Inc.
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
package de.dennisguse.opentracks.stats;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;

import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;

/**
 * Tests for {@link SegmentStatisticUpdater}.
 * This only tests non-trivial pieces of that class.
 *
 * @author Rodrigo Damazio
 */
@RunWith(AndroidJUnit4.class)
public class SegmentStatisticUpdaterTest {

    @Deprecated //TODO SegmentStatisticsUpdater should always have data, right?
    @Test
    public void testMerge_no_data() {
        // given
        SegmentStatisticUpdater subject = new SegmentStatisticUpdater();
        SegmentStatisticUpdater append = new SegmentStatisticUpdater();

        // when
        subject.merge(append);
        Statistics result = subject.getStatistics();

        // then
        assertEquals(
                new Statistics(
                        null,
                        null,
                        Duration.ZERO,
                        Duration.ZERO,
                        Distance.ZERO,
                        Speed.ZERO,
                        null,
                        null,
                        null,
                        null
                ),
                result
        );
    }

    @Test
    public void testMerge() {
        // given
        SegmentStatisticUpdater subject = new SegmentStatisticUpdater(
                new Statistics(
                        Instant.ofEpochMilli(1000),
                        Instant.ofEpochMilli(2500),
                        Duration.ofMillis(1500),
                        Duration.ofMillis(700),
                        Distance.of(750.0),
                        Speed.of(60.0),
                        new AltitudeExtremities(1200, 1250),
                        new AltitudeGainLoss(50, 0),
                        HeartRate.of(100),
                        null
                )
        );

        SegmentStatisticUpdater append = new SegmentStatisticUpdater(
                new Statistics(
                        Instant.ofEpochMilli(3000),
                        Instant.ofEpochMilli(4000),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(600),
                        Distance.of(350.0),
                        Speed.of(30.0),
                        new AltitudeExtremities(2800.0, 3575.0),
                        new AltitudeGainLoss(850, 0),
                        HeartRate.of(200),
                        null
                )
        );

        // when
        subject.merge(append);
        Statistics result = subject.getStatistics();

        // then
        assertEquals(
                new Statistics(
                        Instant.ofEpochMilli(1000),
                        Instant.ofEpochMilli(4000),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(1300),
                        Distance.of(1100),
                        Speed.of(846.1538461538461), //TODO Check why this is not 60
                        new AltitudeExtremities(1200, 3575),
                        new AltitudeGainLoss(900, 0),
                        HeartRate.of(150),
                        null
                )
                , result
        );
    }
}
