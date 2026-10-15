package com.waykeep.app.ui.monthlyRecap;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.ActivityMonthlyRecapBinding;
import com.waykeep.app.databinding.MonthlyCalendarCellBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.share.ShareImageStore;
import com.waykeep.app.share.TrackShareImageGenerator;
import com.waykeep.app.util.StringUtils;

/**
 * Month-in-sport view: totals, activity calendar, and a shareable recap card.
 * All data comes from {@link ContentProviderUtils#getTracks()} filtered in memory.
 */
public class MonthlyRecapActivity extends AbstractActivity {

    private static final int SHARE_WIDTH_PX = 1080;
    private static final int SHARE_HEIGHT_PX = 1350;

    private ActivityMonthlyRecapBinding viewBinding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private YearMonth month = YearMonth.now();
    private List<Track> monthTracks = List.of();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewBinding.monthlyCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        viewBinding.monthlyPrev.setOnClickListener(v -> {
            month = month.minusMonths(1);
            loadMonth();
        });
        viewBinding.monthlyNext.setOnClickListener(v -> {
            month = month.plusMonths(1);
            loadMonth();
        });
        viewBinding.monthlyShare.setOnClickListener(v -> shareRecap());
        loadMonth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        viewBinding = null;
    }

    @NonNull
    @Override
    protected View createRootView() {
        viewBinding = ActivityMonthlyRecapBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    private void loadMonth() {
        YearMonth selected = month;
        viewBinding.monthlyTitle.setText(monthTitle(selected));
        executor.execute(() -> {
            List<Track> tracks;
            try {
                tracks = tracksInMonth(new ContentProviderUtils(this).getTracks(), selected);
            } catch (RuntimeException e) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || viewBinding == null || !selected.equals(month)) {
                    return;
                }
                monthTracks = tracks;
                renderMonth(selected, tracks);
            });
        });
    }

    private void renderMonth(YearMonth selected, List<Track> tracks) {
        viewBinding.monthlyTitle.setText(monthTitle(selected));
        UnitSystem unitSystem = PreferencesUtils.getUnitSystem();

        Distance distance = Distance.ZERO;
        long movingSeconds = 0;
        double gain = 0;
        boolean hasGain = false;
        for (Track track : tracks) {
            distance = distance.plus(track.statistics().totalDistance());
            movingSeconds += track.statistics().movingDuration().getSeconds();
            if (track.statistics().altitudeGainLoss() != null) {
                gain += track.statistics().altitudeGainLoss().gain_m();
                hasGain = true;
            }
        }

        viewBinding.monthlyActivities.setText(getString(R.string.monthly_recap_activities_value, tracks.size()));
        Pair<String, String> distanceParts = DistanceFormatter.Builder().setUnit(unitSystem).build(this)
                .getDistanceParts(distance);
        viewBinding.monthlyDistance.setText(getString(R.string.distance_with_unit, distanceParts.first, distanceParts.second));
        viewBinding.monthlyTime.setText(StringUtils.formatElapsedTime(java.time.Duration.ofSeconds(movingSeconds)));
        if (hasGain) {
            Pair<String, String> gainParts = StringUtils.getAltitudeParts(this, (float) gain, unitSystem);
            viewBinding.monthlyElevation.setText(getString(R.string.altitude_with_unit, gainParts.first, gainParts.second));
        } else {
            viewBinding.monthlyElevation.setText(getString(R.string.value_unknown));
        }

        viewBinding.monthlyCalendar.setAdapter(new CalendarAdapter(selected, tracks));
        viewBinding.monthlyEmpty.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
        viewBinding.monthlyShare.setEnabled(!tracks.isEmpty());
    }

    private void shareRecap() {
        if (monthTracks.isEmpty() || viewBinding == null) {
            return;
        }
        List<Track> tracks = new ArrayList<>(monthTracks);
        YearMonth selected = month;
        viewBinding.monthlyProgress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<TrackShareImageGenerator.StatCard> cards = recapCards(tracks);
            Bitmap bitmap;
            try {
                bitmap = TrackShareImageGenerator.renderStats(this,
                        getString(R.string.monthly_recap_title),
                        monthTitle(selected) + "  ·  " + getString(R.string.monthly_recap_activities_value, tracks.size()),
                        cards, TrackShareImageGenerator.loadBackground(this), SHARE_WIDTH_PX, SHARE_HEIGHT_PX);
            } catch (RuntimeException e) {
                reportShareError();
                return;
            }
            File file = ShareImageStore.save(this, bitmap, "share-monthly-" + selected);
            bitmap.recycle();
            if (file == null) {
                reportShareError();
                return;
            }
            Uri uri = ShareImageStore.uri(this, file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || viewBinding == null) {
                    return;
                }
                viewBinding.monthlyProgress.setVisibility(View.GONE);
                Intent intent = new Intent(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_image_subject))
                        .putExtra(Intent.EXTRA_TEXT, getString(R.string.monthly_recap_share_body, monthTitle(selected)));
                startActivity(Intent.createChooser(intent, null));
            });
        });
    }

    private void reportShareError() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && viewBinding != null) {
                viewBinding.monthlyProgress.setVisibility(View.GONE);
                Toast.makeText(this, R.string.share_image_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private List<TrackShareImageGenerator.StatCard> recapCards(List<Track> tracks) {
        UnitSystem unitSystem = PreferencesUtils.getUnitSystem();
        Distance distance = Distance.ZERO;
        long movingSeconds = 0;
        double gain = 0;
        boolean hasGain = false;
        for (Track track : tracks) {
            distance = distance.plus(track.statistics().totalDistance());
            movingSeconds += track.statistics().movingDuration().getSeconds();
            if (track.statistics().altitudeGainLoss() != null) {
                gain += track.statistics().altitudeGainLoss().gain_m();
                hasGain = true;
            }
        }
        List<TrackShareImageGenerator.StatCard> cards = new ArrayList<>();
        cards.add(new TrackShareImageGenerator.StatCard(String.valueOf(tracks.size()),
                getString(R.string.monthly_recap_activities)));
        Pair<String, String> distanceParts = DistanceFormatter.Builder().setUnit(unitSystem).build(this)
                .getDistanceParts(distance);
        cards.add(new TrackShareImageGenerator.StatCard(
                getString(R.string.distance_with_unit, distanceParts.first, distanceParts.second),
                getString(R.string.stats_distance)));
        cards.add(new TrackShareImageGenerator.StatCard(
                StringUtils.formatElapsedTime(java.time.Duration.ofSeconds(movingSeconds)),
                getString(R.string.stats_moving_time)));
        if (hasGain) {
            Pair<String, String> gainParts = StringUtils.getAltitudeParts(this, (float) gain, unitSystem);
            cards.add(new TrackShareImageGenerator.StatCard(
                    getString(R.string.altitude_with_unit, gainParts.first, gainParts.second),
                    getString(R.string.share_image_stat_elevation_gain)));
        }
        return cards;
    }

    private static List<Track> tracksInMonth(@NonNull List<Track> tracks, @NonNull YearMonth month) {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (YearMonth.from(track.startTime().toLocalDate()).equals(month)) {
                result.add(track);
            }
        }
        return result;
    }

    private static String monthTitle(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
    }

    private static class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
        private final List<Integer> days;
        private final java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        CalendarAdapter(YearMonth month, List<Track> tracks) {
            for (Track track : tracks) {
                int day = track.startTime().getDayOfMonth();
                counts.put(day, counts.getOrDefault(day, 0) + 1);
            }
            days = new ArrayList<>();
            int leadingBlanks = month.atDay(1).getDayOfWeek().getValue() % 7; // Sunday-first.
            for (int i = 0; i < leadingBlanks; i++) {
                days.add(null);
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                days.add(day);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(MonthlyCalendarCellBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Integer day = days.get(position);
            holder.bind(day, day == null ? 0 : counts.getOrDefault(day, 0));
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final MonthlyCalendarCellBinding binding;

            ViewHolder(MonthlyCalendarCellBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(Integer day, int count) {
                if (day == null) {
                    binding.calendarDay.setText("");
                    binding.calendarDot.setVisibility(View.INVISIBLE);
                    return;
                }
                binding.calendarDay.setText(String.valueOf(day));
                binding.calendarDot.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }
}
