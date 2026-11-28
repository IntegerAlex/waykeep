package com.waykeep.app.ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.HapticFeedbackConstants;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class UiHapticsTest {

    @Test
    public void tapIgnoresSwipesAndLongPresses() {
        int slop = 12;

        assertTrue(UiHaptics.isTap(2, 3, 120, slop));
        assertFalse(UiHaptics.isTap(20, 0, 120, slop));
        assertFalse(UiHaptics.isTap(2, 3, 1200, slop));
    }

    @Test
    public void semanticFeedbacksAreDistinct() {
        Set<Integer> constants = new HashSet<>();
        for (UiHaptics.Action action : UiHaptics.Action.values()) {
            if (action != UiHaptics.Action.TAP) {
                constants.add(UiHaptics.semanticFeedback(action));
            }
        }

        assertEquals(3, constants.size());
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, UiHaptics.semanticFeedback(UiHaptics.Action.TAP));
        assertEquals(HapticFeedbackConstants.TOGGLE_ON, UiHaptics.semanticFeedback(UiHaptics.Action.START));
        assertEquals(HapticFeedbackConstants.CONFIRM, UiHaptics.semanticFeedback(UiHaptics.Action.PAUSE));
        assertEquals(HapticFeedbackConstants.TOGGLE_OFF, UiHaptics.semanticFeedback(UiHaptics.Action.STOP));
    }
}
