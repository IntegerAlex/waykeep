package com.waykeep.app.ui.customRecordingLayout;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Only the first visible primary field is rendered as the hero register of the recording
 * plate: a second one would compete with it. Hidden fields never render, so their flag is
 * inert and must not be rewritten.
 */
public class RecordingLayoutPrimaryTest {

    @Test
    public void normalizePrimaryKeepsFirstVisiblePrimary() {
        RecordingLayout layout = new RecordingLayout("test", 2);
        DataField speed = new DataField("speed", true, true, false);
        DataField hiddenPrimary = new DataField("distance", false, true, false);
        DataField gain = new DataField("gain", true, true, false);
        layout.addField(speed);
        layout.addField(hiddenPrimary);
        layout.addField(gain);

        layout.normalizePrimary();

        assertTrue(speed.isPrimary());
        assertTrue("hidden fields never render, their flag is inert", hiddenPrimary.isPrimary());
        assertFalse("second visible primary would compete with the hero", gain.isPrimary());
    }

    @Test
    public void normalizePrimaryLeavesSinglePrimaryAlone() {
        RecordingLayout layout = new RecordingLayout("test", 2);
        DataField distance = new DataField("distance", true, true, false);
        DataField gain = new DataField("gain", true, false, false);
        layout.addField(distance);
        layout.addField(gain);

        layout.normalizePrimary();

        assertTrue(distance.isPrimary());
        assertFalse(gain.isPrimary());
    }
}
