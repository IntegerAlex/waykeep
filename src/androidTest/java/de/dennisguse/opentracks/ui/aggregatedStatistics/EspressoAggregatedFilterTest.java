package de.dennisguse.opentracks.ui.aggregatedStatistics;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.EspressoUtils.performClick;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedViewCondition;
import static de.dennisguse.opentracks.util.EspressoUtils.withRecyclerViewItemCount;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TestUtil;
import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.services.TrackRecordingService;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoAggregatedFilterTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    private final String ACTIVITY_TYPE_LOCALIZED = "activityTypeLocalized";

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    private ActivityScenario<AggregatedStatisticsActivity> scenario;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.stopService(new Intent(context, TrackRecordingService.class));

        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        contentProviderUtils.deleteAllTracks(context);

        Pair<Track, List<TrackPoint>> pair = TestDataUtil.createTrack(new Track.Id(System.currentTimeMillis()), 20);
        TrackBuilder trackBuilder = new TrackBuilder(pair.first);
        trackBuilder.setActivityTypeLocalized(ACTIVITY_TYPE_LOCALIZED);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, trackBuilder.getTrack(), pair.second);

        scenario = ActivityScenario.launch(AggregatedStatisticsActivity.class);
    }

    @After
    public void tearDown() {
        Context context = ApplicationProvider.getApplicationContext();
        context.stopService(new Intent(context, TrackRecordingService.class));
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void espressoAggregatedFilterTest() {
        waitUntilDisplayed(withId(R.id.bottom_app_bar));
        waitUntilDisplayed(withId(R.id.aggregated_statistics_filter));
        waitUntilRecyclerViewItemCount(withId(R.id.aggregated_stats_list), 1);
        waitUntilDisplayed(allOf(withId(R.id.aggregated_stats_type_label), withText(ACTIVITY_TYPE_LOCALIZED)));

        // open FilterDialogFragment through the current bottom app bar action item
        onView(withId(R.id.bottom_app_bar))
                .check(matches(isDisplayed()));
        onView(withId(R.id.bottom_app_bar))
                .check(matches(hasDescendant(withId(R.id.aggregated_statistics_filter))));
        onView(withId(R.id.aggregated_statistics_filter))
                .perform(performClick());

        // check there's a checkbox with ACTIVITY_TYPE_LOCALIZED text
        Matcher<View> activityTypeFilter = allOf(withId(R.id.filter_dialog_check_button), withText(ACTIVITY_TYPE_LOCALIZED),
                withParent(allOf(withId(R.id.filter_items),
                        withParent(IsInstanceOf.instanceOf(android.view.ViewGroup.class)))),
                isDisplayed());
        onView(activityTypeFilter)
                .inRoot(isDialog())
                .check(matches(isDisplayed()));

        // check there's an edit text for "from date"
        onView(withId(R.id.filter_date_edit_text_from))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));

        // check there's an edit text for "to date"
        onView(withId(R.id.filter_date_edit_text_to))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));
    }

    private void waitUntilDisplayed(Matcher<View> viewMatcher) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT);
    }

    private void waitUntilRecyclerViewItemCount(Matcher<View> viewMatcher, int expectedCount) {
        waitForResumedViewCondition(
                allOf(viewMatcher, isDisplayed()),
                view -> withRecyclerViewItemCount(expectedCount).matches(view),
                "RecyclerView item count to be " + expectedCount,
                UI_TIMEOUT);
    }
}
