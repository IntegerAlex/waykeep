package com.waykeep.app.data.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AltitudeGainLossSourceTest {

    @Test
    public void mergesAggregatedDatabaseSourcesWithoutGuessingUnknownRows() {
        assertEquals(AltitudeGainLoss.Source.MIXED,
                AltitudeGainLoss.Source.mergeDatabaseValues("GPS_ESTIMATE,BAROMETER"));
        assertEquals(AltitudeGainLoss.Source.UNKNOWN,
                AltitudeGainLoss.Source.mergeDatabaseValues("GPS_ESTIMATE,UNKNOWN"));
        assertEquals(AltitudeGainLoss.Source.GPS_ESTIMATE,
                AltitudeGainLoss.Source.mergeDatabaseValues("GPS_ESTIMATE,GPS_ESTIMATE"));
        assertEquals(AltitudeGainLoss.Source.UNKNOWN,
                AltitudeGainLoss.Source.mergeDatabaseValues(null));
    }
}
