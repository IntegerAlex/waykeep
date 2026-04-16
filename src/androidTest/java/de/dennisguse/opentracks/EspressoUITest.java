package de.dennisguse.opentracks;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.ActivityTestUtils.waitForResumedActivity;
import static de.dennisguse.opentracks.util.EspressoUtils.selectTabAtIndex;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedViewCondition;
import static de.dennisguse.opentracks.util.EspressoUtils.withSelectedTab;
import static de.dennisguse.opentracks.util.EspressoUtils.withViewPagerCurrentItem;
import static de.dennisguse.opentracks.util.TrackRecordingServiceTestUtils.waitForRecordingState;
import static org.hamcrest.Matchers.allOf;

import android.util.Pair;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewInteraction;
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
import java.util.List;
import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoUITest {

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

    @LargeTest
    @Test
    public void record_stop_resume_stop_finish() {
        {
            // TrackListActivity: start recording
            onView(withId(R.id.track_list_fab_action))
                    .perform(click());
            waitForResumedActivity(TrackRecordingActivity.class, UI_TIMEOUT);
            waitForRecordingState(true, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_recording_fab_action));
        }
        {
            // TrackRecordingActivity: stop
            onView(withId(R.id.track_recording_fab_action))
                    .perform(longClick());
            waitForResumedActivity(TrackStoppedActivity.class, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_edit_name));
            waitUntilDisplayed(withId(R.id.resume_button));
            onView(withId(R.id.track_edit_name))
                    .perform(closeSoftKeyboard());

            // TrackStoppedActivity: resume
            onView(allOf(withId(R.id.resume_button), isClickable()))
                    .perform(click());
            waitForResumedActivity(TrackRecordingActivity.class, UI_TIMEOUT);
            waitForRecordingState(true, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_recording_fab_action));

            // TrackRecordingActivity: stop again
            onView(withId(R.id.track_recording_fab_action))
                    .perform(longClick());
            waitForResumedActivity(TrackStoppedActivity.class, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_edit_name));
            waitUntilDisplayed(withId(R.id.finish_button));
            onView(withId(R.id.track_edit_name))
                    .perform(closeSoftKeyboard());

            // TrackStoppedActivity
            onView(withId(R.id.finish_button))
                    .perform(click());
            waitForResumedActivity(TrackListActivity.class, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_list));
        }
    }

    @LargeTest
    @Test
    public void record_move_through_tabs() {
        {
            // TrackListActivity: start recording
            onView(withId(R.id.track_list_fab_action))
                    .perform(click());
            waitForResumedActivity(TrackRecordingActivity.class, UI_TIMEOUT);
            waitForRecordingState(true, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.track_detail_activity_tablayout));
            waitUntilDisplayed(withId(R.id.track_detail_activity_view_pager));
        }
        {
            // TrackRecordingActivity
            ViewInteraction tabLayout = onView(withId(R.id.track_detail_activity_tablayout));

            tabLayout.perform(selectTabAtIndex(1));
            waitUntilTabSelectionSettlesAt(1);

            tabLayout.perform(selectTabAtIndex(2));
            waitUntilTabSelectionSettlesAt(2);

            tabLayout.perform(selectTabAtIndex(3));
            waitUntilTabSelectionSettlesAt(3);

            tabLayout.perform(selectTabAtIndex(0));
            waitUntilTabSelectionSettlesAt(0);

            // stop
            onView(withId(R.id.track_recording_fab_action))
                    .perform(longClick());
            waitForResumedActivity(TrackStoppedActivity.class, UI_TIMEOUT);
            waitUntilDisplayed(withId(R.id.resume_button));
        }
    }

    @LargeTest
    @Test
    public void selectAndDeleteTrack() {
        Pair<Track, List<TrackPoint>> pair = TestDataUtil.createTrack(new Track.Id(System.currentTimeMillis()), 5);
        String uniqueTrackName = "SelectAndDeleteTrack-" + System.currentTimeMillis();
        TrackBuilder trackBuilder = new TrackBuilder(pair.first);
        trackBuilder.setName(uniqueTrackName);
        TestDataUtil.insertTrackWithLocations(new ContentProviderUtils(ApplicationProvider.getApplicationContext()), trackBuilder.getTrack(), pair.second);
        mActivityTestRule.getScenario().recreate();
        waitUntilDisplayed(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName)));

        onView(withId(R.id.track_list)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.track_list_item_name), withText(uniqueTrackName))).perform(longClick());
    }

    private void waitUntilDisplayed(Matcher<View> viewMatcher) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT);
    }

    private void waitUntilTabSelectionSettlesAt(int index) {
        waitForResumedViewCondition(
                withId(R.id.track_detail_activity_tablayout),
                view -> withSelectedTab(index).matches(view),
                "selected tab to be " + index,
                UI_TIMEOUT);
        waitForResumedViewCondition(
                withId(R.id.track_detail_activity_view_pager),
                view -> withViewPagerCurrentItem(index).matches(view),
                "ViewPager current item to be " + index,
                UI_TIMEOUT);
    }
}
