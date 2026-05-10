package de.dennisguse.opentracks;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.dennisguse.opentracks.databinding.ActivityHealthConnectPermissionsRationaleBinding;
import de.dennisguse.opentracks.ui.util.ViewUtils;

public class HealthConnectPermissionsRationaleActivity extends AbstractActivity {

    private ActivityHealthConnectPermissionsRationaleBinding viewBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.health_connect_permissions_rationale_title);
        viewBinding.healthConnectPermissionsRationaleAppUrl.setText(getString(R.string.about_url, getString(R.string.app_web_url)));
        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);

        ViewUtils.makeClickableLinks(findViewById(android.R.id.content));
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = ActivityHealthConnectPermissionsRationaleBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }
}
