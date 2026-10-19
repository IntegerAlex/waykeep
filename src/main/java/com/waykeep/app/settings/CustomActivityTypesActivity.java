package com.waykeep.app.settings;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.data.models.ActivityType;
import com.waykeep.app.databinding.ActivityCustomActivityTypesBinding;
import com.waykeep.app.databinding.RowCustomActivityTypeBinding;
import com.waykeep.app.fragments.ChooseActivityTypeDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages user-defined activity types (#1879, #2284): add, rename, delete and pick an icon
 * from the built-in set. Names are free text stored on each track, so nothing else migrates.
 */
public class CustomActivityTypesActivity extends AbstractActivity implements ChooseActivityTypeDialogFragment.ChooseActivityTypeCaller {

    private ActivityCustomActivityTypesBinding viewBinding;
    private List<String> types;
    private Adapter adapter;
    private String pendingIconName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding.customActivityTypesToolbar.setTitle(R.string.settings_custom_activity_types_title);
        setSupportActionBar(viewBinding.customActivityTypesToolbar);
        viewBinding.customActivityTypesToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        viewBinding.customActivityTypesToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        types = new ArrayList<>(PreferencesUtils.getCustomActivityTypes());
        adapter = new Adapter();
        viewBinding.customActivityTypesList.setAdapter(adapter);
        viewBinding.customActivityTypesList.setOnItemClickListener((parent, view, position, id) -> showActions(types.get(position)));
        viewBinding.customActivityTypesFab.setOnClickListener(v -> showNameDialog(null));
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = ActivityCustomActivityTypesBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    private void refresh() {
        types = new ArrayList<>(PreferencesUtils.getCustomActivityTypes());
        adapter.notifyDataSetChanged();
    }

    private void showNameDialog(@Nullable String existingName) {
        EditText input = new EditText(this);
        input.setHint(R.string.custom_activity_types_name_hint);
        if (existingName != null) {
            input.setText(existingName);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(existingName == null ? R.string.custom_activity_types_add : R.string.custom_activity_types_edit)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    if (existingName == null) {
                        PreferencesUtils.addCustomActivityType(name, null);
                    } else {
                        PreferencesUtils.renameCustomActivityType(existingName, name);
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActions(@NonNull String name) {
        String[] actions = {
                getString(R.string.custom_activity_types_edit),
                getString(R.string.custom_activity_types_set_icon),
                getString(R.string.custom_activity_types_delete)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showNameDialog(name);
                    } else if (which == 1) {
                        pendingIconName = name;
                        ChooseActivityTypeDialogFragment.showDialog(getSupportFragmentManager(), this, name);
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setMessage(getString(R.string.custom_activity_types_delete) + " " + name + "?")
                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                    PreferencesUtils.removeCustomActivityType(name);
                                    Toast.makeText(this, getString(R.string.custom_activity_types_deleted, name), Toast.LENGTH_SHORT).show();
                                    refresh();
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onChooseActivityTypeDone(ActivityType activityType) {
        if (pendingIconName != null) {
            PreferencesUtils.setCustomActivityTypeIcon(pendingIconName, activityType.getId());
            pendingIconName = null;
            refresh();
        }
    }

    private int iconFor(String name) {
        String iconName = PreferencesUtils.getCustomActivityTypeIconName(name);
        ActivityType type = iconName == null || iconName.isEmpty() ? ActivityType.UNKNOWN : ActivityType.findBy(iconName);
        return type.getIconDrawableId();
    }

    private class Adapter extends ArrayAdapter<String> {

        Adapter() {
            super(CustomActivityTypesActivity.this, 0, types);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            RowCustomActivityTypeBinding row = convertView == null
                    ? RowCustomActivityTypeBinding.inflate(getLayoutInflater(), parent, false)
                    : RowCustomActivityTypeBinding.bind(convertView);
            String name = getItem(position);
            TextView text = row.customActivityTypeName;
            text.setText(name);
            ImageView icon = row.customActivityTypeIcon;
            icon.setImageResource(iconFor(name));
            return row.getRoot();
        }
    }
}
