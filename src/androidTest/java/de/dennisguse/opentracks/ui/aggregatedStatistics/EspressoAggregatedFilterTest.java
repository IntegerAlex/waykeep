package de.dennisguse.opentracks.ui.aggregatedStatistics;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForView;
import static de.dennisguse.opentracks.util.EspressoUtils.waitForResumedView;
import static org.hamcrest.Matchers.allOf;

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
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(ApplicationProvider.getApplicationContext());
        contentProviderUtils.deleteAllTracks(ApplicationProvider.getApplicationContext());

        Pair<Track, List<TrackPoint>> pair = TestDataUtil.createTrack(new Track.Id(System.currentTimeMillis()), 20);
        TrackBuilder trackBuilder = new TrackBuilder(pair.first);
        trackBuilder.setActivityTypeLocalized(ACTIVITY_TYPE_LOCALIZED);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, trackBuilder.getTrack(), pair.second);

        scenario = ActivityScenario.launch(AggregatedStatisticsActivity.class);
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void espressoAggregatedFilterTest() {
        waitUntilDisplayed(withId(R.id.bottom_app_bar));
        waitUntilDisplayed(withId(R.id.aggregated_statistics_filter));

        // open FilterDialogFragment through the current bottom app bar action item
        onView(withId(R.id.bottom_app_bar))
                .check(matches(isDisplayed()));
        onView(withId(R.id.bottom_app_bar))
                .check(matches(hasDescendant(withId(R.id.aggregated_statistics_filter))));
        onView(withId(R.id.aggregated_statistics_filter))
                .perform(click());

        // check there's a checkbox with ACTIVITY_TYPE_LOCALIZED text
        Matcher<View> activityTypeFilter = allOf(withId(R.id.filter_dialog_check_button), withText(ACTIVITY_TYPE_LOCALIZED),
                withParent(allOf(withId(R.id.filter_items),
                        withParent(IsInstanceOf.instanceOf(android.view.ViewGroup.class)))),
                isDisplayed());
        waitUntilDisplayedInCurrentRoot(activityTypeFilter);
        onView(activityTypeFilter)
                .check(matches(isDisplayed()));

        // check there's an edit text for "from date"
        onView(withId(R.id.filter_date_edit_text_from))
                .check(matches(isDisplayed()));

        // check there's an edit text for "to date"
        onView(withId(R.id.filter_date_edit_text_to))
                .check(matches(isDisplayed()));
    }

    private void waitUntilDisplayed(Matcher<View> viewMatcher) {
        waitForResumedView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT);
    }

    private void waitUntilDisplayedInCurrentRoot(Matcher<View> viewMatcher) {
        onView(isRoot()).perform(waitForView(allOf(viewMatcher, isDisplayed()), UI_TIMEOUT));
    }
}
