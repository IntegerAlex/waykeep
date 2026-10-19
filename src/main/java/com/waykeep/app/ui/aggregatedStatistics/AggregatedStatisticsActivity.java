package com.waykeep.app.ui.aggregatedStatistics;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.data.TrackSelection;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.AggregatedStatsBinding;

public class AggregatedStatisticsActivity extends AbstractActivity implements FilterDialogFragment.FilterDialogListener {

    public static final String EXTRA_TRACK_IDS = "track_ids";

    static final String STATE_ARE_FILTERS_APPLIED = "areFiltersApplied";

    private AggregatedStatsBinding viewBinding;

    private AggregatedStatisticsAdapter adapter;

    private AggregatedStatisticsModel viewModel;
    private final TrackSelection selection = new TrackSelection();

    private boolean areFiltersApplied;
    private MenuItem filterItem;
    private MenuItem clearFilterItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        areFiltersApplied = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ARE_FILTERS_APPLIED);

        List<Track.Id> trackIds = getIntent().getParcelableArrayListExtra(EXTRA_TRACK_IDS);
        if (trackIds != null && !trackIds.isEmpty()) {
            trackIds.forEach(selection::addTrackId);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        adapter = new AggregatedStatisticsAdapter(this, null);
        viewBinding.aggregatedStatsList.setLayoutManager(layoutManager);
        viewBinding.aggregatedStatsList.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AggregatedStatisticsModel.class);
        viewModel.getAggregatedStats(selection).observe(this, aggregatedStatistics -> {
            adapter.swapData(aggregatedStatistics);
            checkListEmpty();
        });

        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);
    }

    private void checkListEmpty() {
        if (adapter.getItemCount() == 0) {
            viewBinding.aggregatedStatsList.setVisibility(View.GONE);
            viewBinding.aggregatedStatsEmptyView.setVisibility(View.VISIBLE);
            if (!selection.isEmpty()) {
                viewBinding.aggregatedStatsEmptyView.setText(getString(R.string.aggregated_stats_filter_no_results));
            }
        } else {
            viewBinding.aggregatedStatsList.setVisibility(View.VISIBLE);
            viewBinding.aggregatedStatsEmptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ARE_FILTERS_APPLIED, areFiltersApplied);
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = AggregatedStatsBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aggregated_statistics, menu);
        clearFilterItem = menu.findItem(R.id.aggregated_statistics_clear_filter);
        filterItem = menu.findItem(R.id.aggregated_statistics_filter);
        setMenuVisibility(areFiltersApplied);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.aggregated_statistics_filter) {
            ArrayList<FilterDialogFragment.FilterItem> filterItems = new ArrayList<>();
            adapter.getCategories().forEach(activityType -> filterItems.add(new FilterDialogFragment.FilterItem(activityType, activityType, true)));
            FilterDialogFragment.showDialog(getSupportFragmentManager(), filterItems);
            return true;
        }

        if (item.getItemId() == R.id.aggregated_statistics_clear_filter) {
            setMenuVisibility(false);
            viewModel.clearSelection();
            return true;
        }

        int id = item.getItemId();
        if (id == R.id.aggregated_statistics_period_day
                || id == R.id.aggregated_statistics_period_week
                || id == R.id.aggregated_statistics_period_month
                || id == R.id.aggregated_statistics_period_year) {
            applyPeriod(id);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * #1872: one-tap day/week/month/year instead of picking dates in the filter dialog.
     */
    private void applyPeriod(int menuItemId) {
        LocalDate today = LocalDate.now();
        LocalDate from;
        if (menuItemId == R.id.aggregated_statistics_period_day) {
            from = today;
        } else if (menuItemId == R.id.aggregated_statistics_period_week) {
            from = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        } else if (menuItemId == R.id.aggregated_statistics_period_month) {
            from = today.withDayOfMonth(1);
        } else {
            from = today.withDayOfYear(1);
        }

        selection.addDateRange(
                from.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                Instant.now());
        setMenuVisibility(true);
        viewModel.updateSelection(selection);
    }

    private void setMenuVisibility(boolean areFiltersApplied) {
        this.areFiltersApplied = areFiltersApplied;
        if (clearFilterItem != null && filterItem != null) {
            clearFilterItem.setVisible(this.areFiltersApplied);
            filterItem.setVisible(!this.areFiltersApplied);
        }
    }

    @Override
    public void onFilterDone(ArrayList<FilterDialogFragment.FilterItem> filterItems, LocalDateTime from, LocalDateTime to) {
        setMenuVisibility(true);
        selection.addDateRange(from.atZone(ZoneId.systemDefault()).toInstant(), to.atZone(ZoneId.systemDefault()).toInstant());
        filterItems.stream().filter(fi -> fi.isChecked).forEach(fi -> selection.addActivityType(fi.value));
        viewModel.updateSelection(selection);
    }
}
