package de.dennisguse.opentracks;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.dennisguse.opentracks.databinding.ActivityPermissionsRationaleBinding;
import de.dennisguse.opentracks.ui.util.ViewUtils;

public class PermissionsRationaleActivity extends AbstractActivity {

    private ActivityPermissionsRationaleBinding viewBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.health_connect_permissions_rationale_title);
        viewBinding.permissionsRationaleAppUrl.setText(getString(R.string.about_url, getString(R.string.app_web_url)));
        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);

        ViewUtils.makeClickableLinks(findViewById(android.R.id.content));
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = ActivityPermissionsRationaleBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }
}
