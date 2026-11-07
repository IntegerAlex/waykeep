package com.waykeep.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class PreferencesUtilsTest {

    @Test
    public void parsesCustomDistanceInCurrentLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertEquals(1.25, PreferencesUtils.parseCustomVoiceAnnouncementDistance("1.25"), 0.001);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void blankCustomDistanceUsesPreset() {
        assertEquals(2.0f, PreferencesUtils.resolveVoiceAnnouncementDistance(" ", 2.0f), 0.001f);
    }

    @Test
    public void malformedCustomDistanceUsesPreset() {
        assertEquals(2.0f, PreferencesUtils.resolveVoiceAnnouncementDistance("1.2km", 2.0f), 0.001f);
    }

    @Test
    public void rejectsNonPositiveAndNonFiniteValues() {
        assertNull(PreferencesUtils.parseCustomVoiceAnnouncementDistance("0"));
        assertNull(PreferencesUtils.parseCustomVoiceAnnouncementDistance("-1"));
        assertNull(PreferencesUtils.parseCustomVoiceAnnouncementDistance("NaN"));
        assertNull(PreferencesUtils.parseCustomVoiceAnnouncementDistance("Infinity"));
        assertFalse(PreferencesUtils.isValidCustomVoiceAnnouncementDistance("NaN"));
        assertTrue(PreferencesUtils.isValidCustomVoiceAnnouncementDistance(""));
    }

    @Test
    public void parsesLocaleDecimalSeparatorAndRestoresDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals(1.25, PreferencesUtils.parseCustomVoiceAnnouncementDistance("1,25"), 0.001);
        } finally {
            Locale.setDefault(original);
        }
    }
}
