package de.dennisguse.opentracks;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openContextualActionModeOverflowMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static de.dennisguse.opentracks.util.EspressoUtils.waitFor;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoEditTrackRecordingTest {

    @Rule
    public final LocaleRule mLocaleRule = new LocaleRule(Locale.ENGLISH);

    @Rule
    public ActivityScenarioRule<TrackListActivity> mActivityTestRule = new ActivityScenarioRule<>(TrackListActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    @LargeTest
    @Test
    public void espressoEditTrackRecordingTest() {
        {
            // TrackListActivity: start recording
            onView(withId(R.id.track_list_fab_action))
                    .perform(click());
        }
        {
            // TrackRecordingActivity

            // wait; stay recording
            onView(withId(R.id.track_recording_fab_action))
                    .perform(waitFor(4000));

            // open menu
            openContextualActionModeOverflowMenu();

            // Click the item.
            onView(withText(R.string.menu_edit))
                    .perform(click());

            // change name for "New Name"
            onView(withId(R.id.track_edit_name))
                    .perform(scrollTo(), replaceText("New Name"));

            onView(allOf(withId(R.id.track_edit_name), withText("New Name"), isDisplayed()))
                    .perform(closeSoftKeyboard());

            // save edition
            onView(withId(R.id.track_edit_save))
                    .perform(click());

            // stop;
            onView(withId(R.id.track_recording_fab_action))
                    .perform(longClick());

            // it's on track stopped activity and there are two buttons
            onView(withId(R.id.resume_button))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.finish_button))
                    .check(matches(isDisplayed()));
        }
    }
}
