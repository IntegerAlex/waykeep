package com.waykeep.app.settings;

import android.text.InputType;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.List;

import com.waykeep.app.R;

public class AnnouncementsSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_announcements);
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_announcements_statistics_title);
    }

    @Override
    public void onResume() {
        super.onResume();

        ListPreference voiceFrequency = findPreference(getString(R.string.voice_announcement_frequency_key));
        voiceFrequency.setEntries(PreferencesUtils.getVoiceAnnouncementFrequencyEntries());

        ListPreference voiceDistance = findPreference(getString(R.string.voice_announcement_distance_key));
        voiceDistance.setEntries(PreferencesUtils.getVoiceAnnouncementDistanceEntries());

        for (int key : List.of(
                R.string.voice_announce_time_schedule_key,
                R.string.voice_announce_total_distance_schedule_key,
                R.string.voice_announce_moving_time_schedule_key,
                R.string.voice_announce_average_speed_pace_schedule_key,
                R.string.voice_announce_heart_rate_current_schedule_key,
                R.string.voice_announce_average_heart_rate_schedule_key,
                R.string.voice_announce_lap_speed_pace_schedule_key,
                R.string.voice_announce_lap_heart_rate_schedule_key,
                R.string.voice_announce_lap_power_schedule_key)) {
            ListPreference schedule = findPreference(getString(key));
            schedule.setEntries(PreferencesUtils.getVoiceAnnouncementScheduleEntries());
        }

        EditTextPreference customDistance = findPreference(getString(R.string.voice_announcement_custom_distance_key));
        customDistance.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        customDistance.setOnBindEditTextListener(editText -> editText.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED));
        customDistance.setOnPreferenceChangeListener((preference, newValue) -> {
            String value = (String) newValue;
            if (PreferencesUtils.isValidCustomVoiceAnnouncementDistance(value)) {
                return true;
            }
            Toast.makeText(requireContext(), R.string.settings_announcements_custom_distance_invalid, Toast.LENGTH_SHORT).show();
            return false;
        });

        EditTextPreference recentPaceDistance = findPreference(
                getString(R.string.voice_announcement_recent_pace_distance_key));
        recentPaceDistance.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        recentPaceDistance.setOnBindEditTextListener(editText -> editText.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED));
        recentPaceDistance.setOnPreferenceChangeListener((preference, newValue) -> {
            String value = (String) newValue;
            if (PreferencesUtils.isValidCustomVoiceAnnouncementDistance(value)) {
                return true;
            }
            Toast.makeText(requireContext(), R.string.settings_announcements_custom_distance_invalid, Toast.LENGTH_SHORT).show();
            return false;
        });
    }
}
