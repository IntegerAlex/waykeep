package de.dennisguse.opentracks;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.ActivityTestUtils.waitForResumedActivity;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedViewCondition;
import static de.dennisguse.opentracks.util.EspressoUtils.withRecyclerViewItemCount;
import static de.dennisguse.opentracks.util.TrackRecordingServiceTestUtils.waitForRecordingState;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.content.Intent;
import android.view.View;

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
import de.dennisguse.opentracks.data.ContentProviderUtils;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoDeleteTrackTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

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

    @Test
    public void deleteTrackFromTrackStoppedActivity() {
        recordTrackAndOpenTrackStoppedActivity();

        // TrackStoppedActivity: discard the just-recorded track directly
        onView(withId(R.id.discard_button))
                .perform(click());
        waitUntilDisplayedInCurrentRoot(withText(android.R.string.ok));

        onView(withText(android.R.string.ok))
                .perform(click());
        waitForResumedActivity(TrackListActivity.class, UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_list));
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 0);

        // the recycler is empty again after discarding the only recorded track
        onView(withId(R.id.track_list))
                .check(matches(withRecyclerViewItemCount(0)));
    }

    @Test
    public void deleteTrackFromSelectedTrackInTrackList() {
        String uniqueTrackName = "DeleteFromTrackList-" + System.currentTimeMillis();

        recordTrackAndOpenTrackStoppedActivity();
        onView(withId(R.id.track_edit_name))
                .perform(scrollTo(), replaceText(uniqueTrackName), closeSoftKeyboard());

        onView(withId(R.id.finish_button))
                .perform(click());
        waitForResumedActivity(TrackListActivity.class, UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_list));
        waitUntilDisplayed(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)));
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 1);

        onView(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)))
                .perform(longClick());

        clickDeleteSelectedTrackAction();
        waitUntilDisplayedInCurrentRoot(withText(android.R.string.ok));

        onView(withText(android.R.string.ok))
                .perform(click());
        waitUntilRecyclerViewItemCount(withId(R.id.track_list), 0);

        onView(withId(R.id.track_list))
                .check(matches(withRecyclerViewItemCount(0)));
    }

    private void recordTrackAndOpenTrackStoppedActivity() {
        // TrackListActivity: start recording
        onView(withId(R.id.track_list_fab_action))
                .perform(click());
        waitForResumedActivity(TrackRecordingActivity.class, UI_TIMEOUT);
        waitForRecordingState(true, UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_recording_fab_action));

        // TrackRecordingActivity: stop recording and open TrackStoppedActivity
        onView(withId(R.id.track_recording_fab_action))
                .perform(longClick());
        waitForResumedActivity(TrackStoppedActivity.class, UI_TIMEOUT);
        waitUntilDisplayed(withId(R.id.track_edit_name));
        waitUntilDisplayed(withId(R.id.discard_button));
        waitUntilDisplayed(withId(R.id.finish_button));
        onView(withId(R.id.track_edit_name))
                .perform(closeSoftKeyboard());
    }

    private void clickDeleteSelectedTrackAction() {
        try {
            waitUntilDisplayed(withId(R.id.list_context_menu_delete));
            onView(withId(R.id.list_context_menu_delete))
                    .perform(click());
        } catch (AssertionError ignored) {
            onView(allOf(
                    withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    isDescendantOfA(withId(androidx.appcompat.R.id.action_mode_bar))))
                    .perform(click());
            waitUntilDisplayedInCurrentRoot(withText(R.string.menu_delete));
            onView(withText(R.string.menu_delete))
                    .perform(click());
        }
    }

    private void waitUntilDisplayed(Matcher<View> viewMatcher) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT);
    }

    private void waitUntilDisplayedInCurrentRoot(Matcher<View> viewMatcher) {
        onView(isRoot()).perform(waitForView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT));
    }

    private void waitUntilRecyclerViewItemCount(Matcher<View> viewMatcher, int expectedCount) {
        waitForResumedViewCondition(
                viewMatcher,
                view -> withRecyclerViewItemCount(expectedCount).matches(view),
                "RecyclerView item count to be " + expectedCount,
                UI_TIMEOUT);
    }
}
