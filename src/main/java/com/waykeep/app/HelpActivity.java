package com.waykeep.app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.waykeep.app.databinding.HelpBinding;
import com.waykeep.app.ui.util.ViewUtils;

public class HelpActivity extends AbstractActivity {

    private HelpBinding helpBinding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setSupportActionBar(helpBinding.bottomAppBarLayout.bottomAppBar);
        ViewUtils.makeClickableLinks(findViewById(android.R.id.content));
    }

    @NonNull
    @Override
    protected View createRootView() {
        helpBinding = HelpBinding.inflate(getLayoutInflater());
        return helpBinding.getRoot();
    }
}
