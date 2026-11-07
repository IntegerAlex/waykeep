package com.waykeep.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waykeep.app.databinding.AboutBinding;
import com.waykeep.app.ui.util.ViewUtils;
import com.waykeep.app.util.SystemUtils;

public class AboutActivity extends AbstractActivity {

    private AboutBinding viewBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.about_preference_title));

        viewBinding.aboutTextDescription.setText(getString(R.string.about_description));
        viewBinding.aboutTextVersionName.setText(getString(R.string.about_version_name, SystemUtils.getAppVersionName(this)));
        viewBinding.aboutTextVersionCode.setText(getString(R.string.about_version_code, SystemUtils.getAppVersionCode(this)));
        viewBinding.aboutAppUrl.setText(getString(R.string.about_url, getString(R.string.app_web_url)));

        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);

        ViewUtils.makeClickableLinks(findViewById(android.R.id.content));

        // The autoLink span on this TextView does not take touches, so bind the whole line.
        viewBinding.aboutMaintainer.setMovementMethod(null);
        viewBinding.aboutMaintainer.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.maintainer_url)))));
    }

    @NonNull
    protected View createRootView() {
        viewBinding = AboutBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
    }
}
