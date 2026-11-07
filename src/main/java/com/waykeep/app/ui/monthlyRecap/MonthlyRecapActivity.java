package com.waykeep.app.ui.monthlyRecap;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.TrackRecordedActivity;
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
import com.waykeep.app.util.IntentUtils;
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
    private PopupWindow dayPopup;

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
        if (dayPopup != null) {
            dayPopup.dismiss();
            dayPopup = null;
        }
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

        viewBinding.monthlyCalendar.setAdapter(new CalendarAdapter(selected, tracks, this::openDay, this::showDaySummary));
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

    /** A day with one activity opens it; a day with several asks which one. */
    private void openDay(int day, List<Track> tracks) {
        if (tracks.isEmpty() || isFinishing() || viewBinding == null) {
            return;
        }
        if (tracks.size() == 1) {
            openTrack(tracks.get(0));
            return;
        }
        DistanceFormatter formatter = DistanceFormatter.Builder().setUnit(PreferencesUtils.getUnitSystem()).build(this);
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");
        List<String> labels = new ArrayList<>();
        for (Track track : tracks) {
            labels.add(getString(R.string.monthly_recap_day_entry,
                    track.startTime().format(time),
                    formatter.formatDistance(track.statistics().totalDistance())));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(month.atDay(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> openTrack(tracks.get(which)))
                .show();
    }

    /**
     * Long press shows the day's totals right at the cell, like a toast that stays put, instead
     * of navigating away.
     */
    private void showDaySummary(View anchor, int day, List<Track> tracks) {
        if (tracks.isEmpty() || isFinishing() || isDestroyed() || viewBinding == null) {
            return;
        }
        Distance total = Distance.ZERO;
        long movingSeconds = 0;
        for (Track track : tracks) {
            total = total.plus(track.statistics().totalDistance());
            movingSeconds += track.statistics().movingDuration().getSeconds();
        }
        View content = getLayoutInflater().inflate(R.layout.monthly_day_popup, null);
        ((TextView) content.findViewById(R.id.day_popup_title))
                .setText(month.atDay(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy")));
        ((TextView) content.findViewById(R.id.day_popup_activities))
                .setText(getResources().getQuantityString(R.plurals.monthly_recap_day_activities, tracks.size(), tracks.size()));
        String distance = DistanceFormatter.Builder().setUnit(PreferencesUtils.getUnitSystem()).build(this).formatDistance(total);
        ((TextView) content.findViewById(R.id.day_popup_detail))
                .setText(getString(R.string.monthly_recap_day_detail, distance,
                        StringUtils.formatElapsedTime(java.time.Duration.ofSeconds(movingSeconds))));

        if (dayPopup != null) {
            dayPopup.dismiss();
        }
        float density = getResources().getDisplayMetrics().density;
        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(8 * density);

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int x = anchorLocation[0] + (anchor.getWidth() - content.getMeasuredWidth()) / 2;
        int y = anchorLocation[1] - content.getMeasuredHeight() - (int) (8 * density);
        if (y < 0) {
            y = anchorLocation[1] + anchor.getHeight() + (int) (8 * density);
        }
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, Math.max(0, x), y);
        dayPopup = popup;

        View root = viewBinding.getRoot();
        root.postDelayed(() -> {
            if (dayPopup == popup) {
                popup.dismiss();
                dayPopup = null;
            }
        }, 2500);
    }

    private void openTrack(Track track) {
        startActivity(IntentUtils.newIntent(this, TrackRecordedActivity.class)
                .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, track.id()));
    }

    private interface DayClick {
        void onDayClick(int day, List<Track> tracks);
    }

    private interface DayLongClick {
        void onDayLongClick(View anchor, int day, List<Track> tracks);
    }

    private static class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
        private final List<Integer> days;
        private final Map<Integer, List<Track>> tracksByDay = new HashMap<>();
        private final DayClick dayClick;
        private final DayLongClick dayLongClick;

        CalendarAdapter(YearMonth month, List<Track> tracks, DayClick dayClick, DayLongClick dayLongClick) {
            this.dayClick = dayClick;
            this.dayLongClick = dayLongClick;
            for (Track track : tracks) {
                tracksByDay.computeIfAbsent(track.startTime().getDayOfMonth(), day -> new ArrayList<>()).add(track);
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
            holder.bind(days.get(position), tracksByDay, dayClick, dayLongClick);
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

            void bind(Integer day, Map<Integer, List<Track>> tracksByDay, DayClick dayClick, DayLongClick dayLongClick) {
                if (day == null) {
                    binding.calendarDay.setText("");
                    binding.calendarDot.setVisibility(View.INVISIBLE);
                    binding.getRoot().setOnClickListener(null);
                    binding.getRoot().setClickable(false);
                    return;
                }
                List<Track> dayTracks = tracksByDay.get(day);
                binding.calendarDay.setText(String.valueOf(day));
                binding.calendarDot.setVisibility(dayTracks != null ? View.VISIBLE : View.INVISIBLE);
                if (dayTracks == null) {
                    binding.getRoot().setOnClickListener(null);
                    binding.getRoot().setOnLongClickListener(null);
                    binding.getRoot().setClickable(false);
                } else {
                    binding.getRoot().setClickable(true);
                    binding.getRoot().setOnClickListener(v -> dayClick.onDayClick(day, dayTracks));
                    binding.getRoot().setOnLongClickListener(v -> {
                        dayLongClick.onDayLongClick(v, day, dayTracks);
                        return true;
                    });
                }
            }
        }
    }
}
