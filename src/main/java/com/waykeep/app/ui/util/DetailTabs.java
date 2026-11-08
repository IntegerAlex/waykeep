package com.waykeep.app.ui.util;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

/**
 * Keeps the detail segmented control and its pager in sync. Used by the review and recording
 * screens, which share the same four tabs.
 */
public final class DetailTabs {

    private DetailTabs() {
    }

    public static void bind(@NonNull MaterialButtonToggleGroup tabs, @NonNull ViewPager2 pager,
                            int initialIndex) {
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (pager.getCurrentItem() != index) {
                pager.setCurrentItem(index, false);
            }
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                MaterialButton button = (MaterialButton) tabs.getChildAt(position);
                if (button != null && !button.isChecked()) {
                    tabs.check(button.getId());
                }
            }
        });

        int index = Math.max(0, Math.min(tabs.getChildCount() - 1, initialIndex));
        pager.setCurrentItem(index, false);
        MaterialButton button = (MaterialButton) tabs.getChildAt(index);
        if (button != null) {
            tabs.check(button.getId());
        }
    }
}
