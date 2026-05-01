package de.dennisguse.opentracks.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.dennisguse.opentracks.AbstractActivity;
import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.databinding.ActivityHealthConnectSettingsBinding;
import de.dennisguse.opentracks.io.file.exporter.ExportUtils;
import de.dennisguse.opentracks.io.healthconnect.HealthConnectExporter;
import de.dennisguse.opentracks.io.healthconnect.HealthConnectWorker;

public class HealthConnectSettingsActivity extends AbstractActivity {

    private static final String TAG = HealthConnectSettingsActivity.class.getSimpleName();
    private static final long SYNC_ALL_TIME_MILLIS = -1L;
    private static final long UNSELECTED_TIME_MILLIS = Long.MIN_VALUE;

    private ActivityHealthConnectSettingsBinding viewBinding;
    private ActivityResultLauncher<Set<String>> healthConnectPermissionLauncher;
    private boolean pendingEnableExport;
    private boolean pendingEnableDetailed;
    private boolean pendingShowInitialSyncDialog;
    private Set<String> pendingPermissions = Set.of();
    private boolean updatingUi;
    private long customInitialSyncStartTimeMillis = UNSELECTED_TIME_MILLIS;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences, key) -> runOnUiThread(this::bindState);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(viewBinding.topAppBar);
        viewBinding.topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        healthConnectPermissionLauncher = registerForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
                grantedPermissions -> {
                    boolean exportGranted = grantedPermissions.contains(HealthConnectExporter.WRITE_EXERCISE_PERMISSION);
                    boolean routeGranted = !pendingPermissions.contains(HealthConnectExporter.WRITE_EXERCISE_ROUTE_PERMISSION)
                            || grantedPermissions.contains(HealthConnectExporter.WRITE_EXERCISE_ROUTE_PERMISSION);
                    boolean exportEnabled = pendingEnableExport && exportGranted;

                    PreferencesUtils.setBoolean(R.string.health_connect_export_enabled_key, exportEnabled);
                    PreferencesUtils.setBoolean(
                            R.string.health_connect_detailed_sync_key,
                            pendingEnableDetailed && exportGranted && routeGranted
                    );

                    if (!exportEnabled) {
                        ExportUtils.cancelHealthConnectExport(this);
                    }

                    bindState();

                    if (exportEnabled && pendingShowInitialSyncDialog) {
                        showInitialSyncDialog();
                    }
                }
        );

        setTitle(R.string.health_connect_screen_title);
        bindActions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferencesUtils.registerOnSharedPreferenceChangeListenerSilent(preferenceChangeListener);
    }

    @Override
    protected void onStop() {
        PreferencesUtils.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindState();
    }

    private void bindActions() {
        viewBinding.healthConnectExportRow.setOnClickListener(v ->
                viewBinding.healthConnectExportSwitch.performClick());
        viewBinding.healthConnectDetailedRow.setOnClickListener(v -> {
            if (viewBinding.healthConnectDetailedSwitch.isEnabled()) {
                viewBinding.healthConnectDetailedSwitch.performClick();
            }
        });
        viewBinding.healthConnectManageDataRow.setOnClickListener(v ->
                openIntent(HealthConnectClient.getHealthConnectManageDataIntent(this)));
        viewBinding.healthConnectSettingsRow.setOnClickListener(v ->
                openIntent(new Intent(HealthConnectClient.getHealthConnectSettingsAction())));
        viewBinding.healthConnectManualSyncButton.setOnClickListener(v -> startManualSyncFlow());

        viewBinding.healthConnectExportSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingUi) return;

            if (isChecked) {
                requestPermissions(true, PreferencesUtils.shouldExportDetailedHealthConnectWorkout());
            } else {
                PreferencesUtils.setBoolean(R.string.health_connect_export_enabled_key, false);
                ExportUtils.cancelHealthConnectExport(this);
                bindState();
            }
        });

        viewBinding.healthConnectDetailedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingUi) return;

            if (isChecked) {
                requestPermissions(true, true);
            } else {
                PreferencesUtils.setBoolean(R.string.health_connect_detailed_sync_key, false);
                bindState();
            }
        });
    }

    private void bindState() {
        boolean available = HealthConnectExporter.isAvailable(this);
        boolean exportEnabled = available && PreferencesUtils.shouldExportToHealthConnect();
        boolean detailedEnabled = available && PreferencesUtils.shouldExportDetailedHealthConnectWorkout();

        updatingUi = true;
        viewBinding.healthConnectExportSwitch.setChecked(exportEnabled);
        viewBinding.healthConnectDetailedSwitch.setChecked(detailedEnabled);
        updatingUi = false;

        viewBinding.healthConnectExportSwitch.setEnabled(available);
        viewBinding.healthConnectDetailedSwitch.setEnabled(available && exportEnabled);
        viewBinding.healthConnectManualSyncButton.setEnabled(available && exportEnabled);

        viewBinding.healthConnectManageDataRow.setEnabled(available);
        viewBinding.healthConnectSettingsRow.setEnabled(available);
        viewBinding.healthConnectManageDataRow.setAlpha(available ? 1f : 0.5f);
        viewBinding.healthConnectSettingsRow.setAlpha(available ? 1f : 0.5f);

        viewBinding.healthConnectAvailabilityNotice.setVisibility(available ? View.GONE : View.VISIBLE);
        viewBinding.healthConnectExportSummary.setText(
                available ? R.string.health_connect_export_enabled_summary : R.string.health_connect_not_available
        );
        viewBinding.healthConnectDetailedSummary.setText(
                exportEnabled ? R.string.health_connect_detailed_sync_summary : R.string.health_connect_detailed_sync_requires_export
        );
        viewBinding.healthConnectManualSyncSummary.setText(
                exportEnabled ? R.string.health_connect_manual_sync_summary : R.string.health_connect_manual_sync_requires_export
        );
        viewBinding.healthConnectSyncStatusSummary.setText(PreferencesUtils.getHealthConnectLastSyncStatus());
    }

    private void startManualSyncFlow() {
        if (!HealthConnectExporter.isAvailable(this) || !PreferencesUtils.shouldExportToHealthConnect()) {
            bindState();
            return;
        }

        ListenableFuture<List<WorkInfo>> workInfosFuture =
                WorkManager.getInstance(this).getWorkInfosForUniqueWork(HealthConnectWorker.INITIAL_SYNC_WORK_NAME);
        workInfosFuture.addListener(() -> {
            try {
                boolean syncRunning = workInfosFuture.get().stream().anyMatch(workInfo -> !workInfo.getState().isFinished());
                runOnUiThread(() -> {
                    if (syncRunning) {
                        Toast.makeText(this, R.string.health_connect_sync_already_running, Toast.LENGTH_SHORT).show();
                    } else {
                        showInitialSyncDialog();
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Unable to determine Health Connect sync state", e);
                runOnUiThread(this::showInitialSyncDialog);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void requestPermissions(boolean enableExport, boolean enableDetailed) {
        if (!HealthConnectExporter.isAvailable(this)) {
            bindState();
            return;
        }

        pendingEnableExport = enableExport;
        pendingEnableDetailed = enableDetailed;
        pendingShowInitialSyncDialog = enableExport
                && !PreferencesUtils.shouldExportToHealthConnect()
                && !PreferencesUtils.hasHealthConnectInitialSyncConfigured()
                && !PreferencesUtils.isHealthConnectInitialSyncCompleted();

        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        permissions.add(HealthConnectExporter.WRITE_EXERCISE_PERMISSION);
        if (enableDetailed) {
            permissions.add(HealthConnectExporter.WRITE_EXERCISE_ROUTE_PERMISSION);
        }
        pendingPermissions = permissions;

        healthConnectPermissionLauncher.launch(permissions);
    }

    private void showInitialSyncDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_health_connect_initial_sync, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_group_sync_period);
        RadioButton radioPreset = dialogView.findViewById(R.id.radio_preset);
        Spinner spinnerPreset = dialogView.findViewById(R.id.spinner_sync_period);
        Button buttonSelectDate = dialogView.findViewById(R.id.button_select_date);

        String[] options = new String[]{
                getString(R.string.health_connect_initial_sync_last_3_days),
                getString(R.string.health_connect_initial_sync_last_7_days),
                getString(R.string.health_connect_initial_sync_last_30_days),
                getString(R.string.health_connect_initial_sync_all),
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPreset.setAdapter(adapter);
        spinnerPreset.setSelection(1);

        customInitialSyncStartTimeMillis = UNSELECTED_TIME_MILLIS;
        buttonSelectDate.setText(R.string.health_connect_initial_sync_select_date);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean presetSelected = checkedId == R.id.radio_preset;
            spinnerPreset.setEnabled(presetSelected);
            buttonSelectDate.setEnabled(checkedId == R.id.radio_custom_date);
        });
        radioPreset.setChecked(true);
        spinnerPreset.setEnabled(true);
        buttonSelectDate.setEnabled(false);
        buttonSelectDate.setOnClickListener(v -> showInitialSyncDatePicker(buttonSelectDate));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.health_connect_initial_sync_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        handleInitialSyncSelection(radioPreset.isChecked(), spinnerPreset.getSelectedItemPosition()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showInitialSyncDatePicker(Button buttonSelectDate) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.health_connect_initial_sync_select_date)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            customInitialSyncStartTimeMillis = Instant.ofEpochMilli(selection)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            buttonSelectDate.setText(describeInitialSyncSelection(customInitialSyncStartTimeMillis));
        });

        datePicker.show(getSupportFragmentManager(), "health_connect_initial_sync_date");
    }

    private void handleInitialSyncSelection(boolean presetSelected, int selectedPresetPosition) {
        if (presetSelected) {
            switch (selectedPresetPosition) {
                case 0 -> showInitialSyncConfirmation(daysAgoMillis(3));
                case 1 -> showInitialSyncConfirmation(daysAgoMillis(7));
                case 2 -> showInitialSyncConfirmation(daysAgoMillis(30));
                case 3 -> showInitialSyncConfirmation(SYNC_ALL_TIME_MILLIS);
                default -> showInitialSyncConfirmation(daysAgoMillis(7));
            }
            return;
        }

        if (customInitialSyncStartTimeMillis != UNSELECTED_TIME_MILLIS) {
            showInitialSyncConfirmation(customInitialSyncStartTimeMillis);
            return;
        }

        showInitialSyncConfirmation(daysAgoMillis(7));
    }

    private void showInitialSyncConfirmation(long startTimeMillis) {
        String selectionDescription = describeInitialSyncSelection(startTimeMillis);

        PreferencesUtils.resetHealthConnectInitialSyncState(startTimeMillis);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.health_connect_sync_now_title)
                .setMessage(getString(R.string.health_connect_sync_now_message, selectionDescription))
                .setPositiveButton(R.string.health_connect_sync_now, (dialog, which) -> {
                    PreferencesUtils.setHealthConnectLastSyncStatus(
                            prefixedStatus(getString(R.string.health_connect_sync_status_initial_starting, selectionDescription))
                    );
                    ExportUtils.startHealthConnectInitialSync(this, startTimeMillis);
                })
                .setNegativeButton(R.string.health_connect_sync_later, (dialog, which) -> PreferencesUtils.setHealthConnectLastSyncStatus(
                        prefixedStatus(getString(R.string.health_connect_sync_status_initial_pending, selectionDescription))
                ))
                .show();
    }

    private String describeInitialSyncSelection(long startTimeMillis) {
        if (startTimeMillis == SYNC_ALL_TIME_MILLIS) {
            return getString(R.string.health_connect_initial_sync_all);
        }

        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(startTimeMillis));
    }

    private long daysAgoMillis(int days) {
        return Instant.now()
                .minusSeconds(days * 24L * 60L * 60L)
                .toEpochMilli();
    }

    private String prefixedStatus(String message) {
        String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date());
        return timestamp + ": " + message;
    }

    private void openIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.health_connect_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = ActivityHealthConnectSettingsBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }
}
