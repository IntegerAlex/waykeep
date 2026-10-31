package com.waykeep.app.data.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PositionTest {

    @Test
    public void speedBelowItsOwnUncertaintyIsStandingStill() {
        assertTrue(Position.speedOf(0.49, true, 3.18).isZero());
    }

    @Test
    public void walkingSpeedSurvivesItsUncertainty() {
        assertEquals(1.4, Position.speedOf(1.4, true, 0.3).toMPS(), 0.001);
    }

    @Test
    public void walkingSpeedSurvivesALargeUncertainty() {
        assertEquals(1.05, Position.speedOf(1.05, true, 2.0).toMPS(), 0.001);
    }

    @Test
    public void speedWithoutAccuracyIsKept() {
        assertEquals(0.49, Position.speedOf(0.49, false, 0).toMPS(), 0.001);
    }
}
