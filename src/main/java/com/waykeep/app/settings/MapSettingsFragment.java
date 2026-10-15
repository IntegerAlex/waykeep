package com.waykeep.app.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.R;
import com.waykeep.app.ui.trackmap.MapTileCache;

public class MapSettingsFragment extends PreferenceFragmentCompat {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_map);

        Preference clearCache = findPreference(getString(R.string.map_clear_cache_key));
        clearCache.setOnPreferenceClickListener(preference -> {
            executor.execute(() -> {
                MapTileCache.clear(MapTileCache.dir(requireContext()));
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.map_cache_cleared, Toast.LENGTH_SHORT).show();
                            refreshCacheSummary();
                        }
                    });
                }
            });
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((SettingsActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_map_title);
        refreshCacheSummary();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void refreshCacheSummary() {
        executor.execute(() -> {
            File dir = MapTileCache.dir(requireContext());
            long bytes = MapTileCache.sizeBytes(dir);
            String summary = getString(R.string.map_clear_cache_summary)
                    + " (" + getString(R.string.map_cache_size, formatBytes(bytes)) + ")";
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Preference clearCache = findPreference(getString(R.string.map_clear_cache_key));
                        if (clearCache != null) {
                            clearCache.setSummary(summary);
                        }
                    }
                });
            }
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
