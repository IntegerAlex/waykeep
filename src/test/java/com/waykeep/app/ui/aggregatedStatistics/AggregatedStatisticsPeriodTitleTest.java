package com.waykeep.app.ui.aggregatedStatistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.waykeep.app.R;

import java.time.LocalDate;
import java.util.Locale;

/**
 * U8: the month period shows the actual month/year, while the other periods keep
 * their existing "Today" / "This week" / "This year" resource titles.
 */
public class AggregatedStatisticsPeriodTitleTest {

    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 26);

    @Test
    public void monthPeriodShowsMonthAndYearInsteadOfThisMonth() {
        assertEquals("November 2026", AggregatedStatisticsActivity.periodTitle(
                NOVEMBER, R.id.aggregated_statistics_period_month, Locale.US));
    }

    @Test
    public void monthPeriodTitleUsesRequestedLocale() {
        assertEquals("novembre 2026", AggregatedStatisticsActivity.periodTitle(
                NOVEMBER, R.id.aggregated_statistics_period_month, Locale.FRENCH));
    }

    @Test
    public void dayWeekAndYearPeriodsKeepTheirExistingResourceTitles() {
        assertNull(AggregatedStatisticsActivity.periodTitle(
                NOVEMBER, R.id.aggregated_statistics_period_day, Locale.US));
        assertNull(AggregatedStatisticsActivity.periodTitle(
                NOVEMBER, R.id.aggregated_statistics_period_week, Locale.US));
        assertNull(AggregatedStatisticsActivity.periodTitle(
                NOVEMBER, R.id.aggregated_statistics_period_year, Locale.US));
    }
}
