package com.waykeep.app.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Checks the share-card shrink-to-fit math: text shrinks before it is chopped.
 */
public class TrackShareImageGeneratorSizeTest {

    @Test
    public void fittingTextKeepsItsSize() {
        assertEquals(52f, TrackShareImageGenerator.fittedSize(52f, 100f, 300f, 32f), 0f);
    }

    @Test
    public void overflowingTextShrinksJustEnough() {
        // Linear text: width scales with size, so the fitted size is floor(52 * 300 / 390).
        float fitted = TrackShareImageGenerator.fittedSize(52f, 390f, 300f, 32f);
        assertEquals(40f, fitted, 0f);
        assertTrue(fitted * 390f / 52f <= 300f);
    }

    @Test
    public void shrinkNeverGoesBelowTheFloor() {
        assertEquals(32f, TrackShareImageGenerator.fittedSize(52f, 4000f, 300f, 32f), 0f);
    }
}
