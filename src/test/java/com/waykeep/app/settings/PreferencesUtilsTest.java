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
    public void customDistanceCannotBeSmallerThanTheSmallestPreset() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertNull(PreferencesUtils.parseCustomVoiceAnnouncementDistance("0.099"));
            assertFalse(PreferencesUtils.isValidCustomVoiceAnnouncementDistance("0.099"));
            assertEquals(2.0, PreferencesUtils.resolveVoiceAnnouncementDistance("0.099", 2.0), 0.001);
            assertEquals(0.1, PreferencesUtils.parseCustomVoiceAnnouncementDistance("0.1"), 0.001);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void weeklyGoalIsOffOrAtLeastOneKilometre() {
        assertTrue(PreferencesUtils.isValidWeeklyGoalMeters("0"));
        assertTrue(PreferencesUtils.isValidWeeklyGoalMeters("1000"));
        assertTrue(PreferencesUtils.isValidWeeklyGoalMeters("20000"));
        assertFalse(PreferencesUtils.isValidWeeklyGoalMeters("999"));
        assertFalse(PreferencesUtils.isValidWeeklyGoalMeters("-1"));
        assertFalse(PreferencesUtils.isValidWeeklyGoalMeters("abc"));
        assertFalse(PreferencesUtils.isValidWeeklyGoalMeters(null));
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
