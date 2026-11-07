package com.waykeep.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.waykeep.app.R;
import com.waykeep.app.data.models.AltitudeGainLoss;

public class AltitudeGainLossSourceLabelsTest {

    @Test
    public void labelsGpsAndMixedAndKeepsUnknownExplicit() {
        assertEquals(R.string.gps_altitude_source_estimate,
                AltitudeGainLossSourceLabels.resource(AltitudeGainLoss.Source.GPS_ESTIMATE));
        assertEquals(R.string.gps_altitude_source_mixed,
                AltitudeGainLossSourceLabels.resource(AltitudeGainLoss.Source.MIXED));
        assertEquals(R.string.gps_altitude_source_unknown,
                AltitudeGainLossSourceLabels.resource(AltitudeGainLoss.Source.UNKNOWN));
    }
}
