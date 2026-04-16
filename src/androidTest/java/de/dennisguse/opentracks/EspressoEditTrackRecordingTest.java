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
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.ActivityTestUtils.waitForResumedActivity;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedView;
import static de.dennisguse.opentracks.util.TrackRecordingServiceTestUtils.waitForRecordingState;
import static org.hamcrest.Matchers.allOf;

import android.view.View;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Locale;
import de.dennisguse.opentracks.data.ContentProviderUtils;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoEditTrackRecordingTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    @Rule
    public final LocaleRule mLocaleRule = new LocaleRule(Locale.ENGLISH);

    @Rule
    public ActivityScenarioRule<TrackListActivity> mActivityTestRule = new ActivityScenarioRule<>(TrackListActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.stopService(new Intent(context, de.dennisguse.opentracks.services.TrackRecordingService.class));
        new ContentProviderUtils(context).deleteAllTracks(context);
    }

    @LargeTest
    @Test
    public void espressoEditTrackRecordingTest() {
        {
            // TrackListActivity: start recording
            onView(withId(R.id.track_list_fab_action))
                    .perform(click());
            waitForResumedActivity(TrackRecordingActivity.class, UI_TIMEOUT);
            waitForRecordingState(true, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_recording_fab_action));
        }
        {
            // TrackRecordingActivity

            // open menu
            openContextualActionModeOverflowMenu();
            waitUntilDisplayedInCurrentRoot(withText(R.string.menu_edit));

            // Click the item.
            onView(withText(R.string.menu_edit))
                    .perform(click());
            waitUntilDisplayed(withId(R.id.track_edit_name));

            // change name for "New Name"
            onView(withId(R.id.track_edit_name))
                    .perform(scrollTo(), replaceText("New Name"));

            onView(allOf(withId(R.id.track_edit_name), withText("New Name"), isDisplayed()))
                    .perform(closeSoftKeyboard());

            // save edition
            onView(withId(R.id.track_edit_save))
                    .perform(click());
            waitUntilDisplayed(withId(R.id.track_recording_fab_action));

            // stop;
            onView(withId(R.id.track_recording_fab_action))
                    .perform(longClick());
            waitForResumedActivity(TrackStoppedActivity.class, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.resume_button));
            waitUntilDisplayed(withId(R.id.finish_button));

            // it's on track stopped activity and there are two buttons
            onView(withId(R.id.resume_button))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.finish_button))
                    .check(matches(isDisplayed()));
        }
    }

    private void waitUntilDisplayed(Matcher<View> viewMatcher) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT);
    }

    private void waitUntilDisplayedInCurrentRoot(Matcher<View> viewMatcher) {
        onView(isRoot()).perform(waitForView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT));
    }
}
