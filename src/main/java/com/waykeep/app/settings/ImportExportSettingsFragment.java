package com.waykeep.app.settings;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Map;

import com.waykeep.app.R;
import com.waykeep.app.io.file.TrackFileFormat;
import com.waykeep.app.io.file.TrackFilenameGenerator;
import com.waykeep.app.io.file.importer.ImportActivity;
import com.waykeep.app.io.healthconnect.exporter.HealthConnectUtils;
import com.waykeep.app.util.IntentUtils;


public class ImportExportSettingsFragment extends PreferenceFragmentCompat {

    private final ActivityResultLauncher<String[]> importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris == null || uris.isEmpty()) {
                    return;
                }
                Intent intent = IntentUtils.newIntent(requireContext(), ImportActivity.class);
                if (uris.size() == 1) {
                    intent.setData(uris.get(0));
                } else {
                    ClipData clipData = new ClipData("tracks", new String[]{"*/*"}, new ClipData.Item(uris.get(0)));
                    for (int i = 1; i < uris.size(); i++) {
                        clipData.addItem(new ClipData.Item(uris.get(i)));
                    }
                    intent.setClipData(clipData);
                }
                startActivity(intent);
            });

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_import_export);

        findPreference(getString(R.string.import_file_key))
                .setOnPreferenceClickListener(preference -> {
                    importFileLauncher.launch(new String[]{
                            TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES.getMimeType(),
                            TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.getMimeType(),
                            TrackFileFormat.GPX.getMimeType()});
                    return true;
                });

        setExportTrackFileFormatOptions();
        setExportDirectorySummary();
        setFilenameTemplate();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((SettingsActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_import_export_title);
    }

    @Override
    public void onResume() {
        super.onResume();

        setExportDirectorySummary();
        findPreference(getString(R.string.post_workout_export_enabled_key)).setEnabled(PreferencesUtils.isDefaultExportDirectoryUri());

        setHealthConnectPreference();
    }

    private void setHealthConnectPreference() {
        boolean healthConnectAvailable = HealthConnectUtils.isAvailable(requireContext());
        findPreference("healthConnectCategory")
                .setEnabled(healthConnectAvailable);

        if (!healthConnectAvailable) {
            findPreference(getString(R.string.post_workout_export_health_connect_enabled_key))
                    .setSummary(R.string.health_connect_not_available);
        }
    }

    private void setExportTrackFileFormatOptions() {
        Map<String, String> options = TrackFileFormat.toPreferenceIdLabelMap(getResources(),
                TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES,
                TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA,
                TrackFileFormat.GPX,
                TrackFileFormat.CSV);
        ListPreference listPreference = findPreference(getString(R.string.export_trackfileformat_key));
        listPreference.setEntries(options.values().toArray(new String[0]));
        listPreference.setEntryValues(options.keySet().toArray(new String[0]));
    }

    private void setExportDirectorySummary() {
        Preference instantExportDirectoryPreference = findPreference(getString(R.string.settings_default_export_directory_key));
        instantExportDirectoryPreference.setSummaryProvider(preference -> {
            Uri directoryUri = PreferencesUtils.getDefaultExportDirectoryUri();
            DocumentFile directory = IntentUtils.toDocumentFile(getContext(), directoryUri);
            //Use same value for not set as Androidx ListPreference and EditTextPreference
            if (directory == null) {
                return getString(androidx.preference.R.string.not_set);
            }

            return directoryUri + (directory.canWrite() ? "" : getString(R.string.export_dir_not_writable));
        });
    }

    private void setFilenameTemplate() {
        EditTextPreference preference = findPreference(getString(R.string.export_filename_format_key));
        preference.setOnBindEditTextListener(t -> t.setHint(getString(R.string.export_filename_format_default)));
        preference.setDialogMessage(TrackFilenameGenerator.getAllOptions());

        preference.setOnPreferenceChangeListener((p, newValue) -> new TrackFilenameGenerator(newValue.toString()).isValid());

        preference.setSummaryProvider(p ->
                PreferencesUtils.getTrackFileformatGenerator()
                        .getExample()
        );
    }
}
