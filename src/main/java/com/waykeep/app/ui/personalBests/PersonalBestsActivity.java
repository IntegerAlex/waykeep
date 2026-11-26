package com.waykeep.app.ui.personalBests;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.waykeep.app.AbstractActivity;
import com.waykeep.app.R;
import com.waykeep.app.TrackRecordedActivity;
import com.waykeep.app.data.ContentProviderUtils;
import com.waykeep.app.data.models.DistanceFormatter;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.SpeedFormatter;
import com.waykeep.app.data.models.Track;
import com.waykeep.app.databinding.ActivityPersonalBestsBinding;
import com.waykeep.app.databinding.PersonalBestListItemBinding;
import com.waykeep.app.settings.PreferencesUtils;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.util.IntentUtils;
import com.waykeep.app.util.StringUtils;
import com.waykeep.app.util.TrackNameUtils;

/**
 * Shows all-time personal bests (longest, fastest, most elevation) for one sport at a time.
 * Records from different sports are never compared with each other.
 * Everything is computed and formatted off the UI thread; each row opens the record track.
 */
public class PersonalBestsActivity extends AbstractActivity {

    public record PersonalBest(
            @NonNull String label,
            @NonNull String value,
            @NonNull String unit,
            @NonNull Track.Id trackId,
            @NonNull String trackLine) {
    }

    private ActivityPersonalBestsBinding viewBinding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Sport selectedSport = Sport.RUNNING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewBinding.personalBestsList.setLayoutManager(new LinearLayoutManager(this));
        viewBinding.personalBestsSportGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            Sport sport = sportOf(checkedId);
            if (sport == selectedSport) {
                return;
            }
            selectedSport = sport;
            loadBests(sport);
        });
        loadBests(selectedSport);
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
        viewBinding = ActivityPersonalBestsBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }

    private static Sport sportOf(int checkedId) {
        if (checkedId == R.id.personal_bests_sport_cycling) {
            return Sport.CYCLING;
        }
        if (checkedId == R.id.personal_bests_sport_walking) {
            return Sport.WALKING;
        }
        return Sport.RUNNING;
    }

    private void loadBests(Sport sport) {
        executor.execute(() -> {
            List<PersonalBest> bests;
            try {
                bests = computeBests(this, sport, new ContentProviderUtils(this).getTracks());
            } catch (RuntimeException e) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || viewBinding == null || sport != selectedSport) {
                    return;
                }
                if (bests.isEmpty()) {
                    viewBinding.personalBestsList.setVisibility(View.GONE);
                    viewBinding.personalBestsEmptyView.setVisibility(View.VISIBLE);
                } else {
                    viewBinding.personalBestsList.setVisibility(View.VISIBLE);
                    viewBinding.personalBestsEmptyView.setVisibility(View.GONE);
                    viewBinding.personalBestsList.setAdapter(new Adapter(bests));
                }
            });
        });
    }

    private static List<PersonalBest> computeBests(@NonNull Context context, @NonNull Sport sport, @NonNull List<Track> tracks) {
        Sport.BestTracks best = sport.bestsIn(tracks);

        UnitSystem unitSystem = PreferencesUtils.getUnitSystem();
        List<PersonalBest> bests = new ArrayList<>();
        if (best.longestDistance() != null) {
            Track track = best.longestDistance();
            bests.add(new PersonalBest(label(context, sport, R.string.personal_best_longest_distance),
                    DistanceFormatter.Builder().setUnit(unitSystem).build(context).formatDistance(track.statistics().totalDistance()),
                    "", track.id(), trackLine(context, track)));
        }
        if (best.longestTime() != null) {
            Track track = best.longestTime();
            bests.add(new PersonalBest(label(context, sport, R.string.personal_best_longest_time),
                    StringUtils.formatElapsedTime(track.statistics().movingDuration()),
                    "", track.id(), trackLine(context, track)));
        }
        if (best.fastestAverage() != null) {
            Track track = best.fastestAverage();
            bests.add(new PersonalBest(label(context, sport, R.string.personal_best_fastest),
                    formatSpeed(context, sport, track.statistics().getAverageMovingSpeed(), unitSystem),
                    "", track.id(), trackLine(context, track)));
        }
        if (best.mostElevation() != null) {
            Track track = best.mostElevation();
            android.util.Pair<String, String> parts = StringUtils.getAltitudeParts(context,
                    track.statistics().altitudeGainLoss().gain_m(), unitSystem);
            bests.add(new PersonalBest(label(context, sport, R.string.personal_best_most_elevation),
                    context.getString(R.string.altitude_with_unit, parts.first, parts.second),
                    "", track.id(), trackLine(context, track)));
        }
        if (best.topSpeed() != null) {
            Track track = best.topSpeed();
            bests.add(new PersonalBest(label(context, sport, R.string.personal_best_top_speed),
                    formatSpeed(context, sport, track.statistics().maxSpeed(), unitSystem),
                    "", track.id(), trackLine(context, track)));
        }
        return bests;
    }

    private static String label(Context context, Sport sport, int recordLabelResId) {
        return context.getString(R.string.personal_best_label_with_sport,
                context.getString(sport.getLabelResId()), context.getString(recordLabelResId));
    }

    private static String formatSpeed(Context context, Sport sport, Speed speed, UnitSystem unitSystem) {
        return SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(sport.prefersSpeed())
                .build(context).formatSpeed(speed);
    }

    private static String trackLine(Context context, Track track) {
        return formatTrackLine(track.name(), track.startTime(), Locale.getDefault(),
                context.getString(R.string.personal_best_track));
    }

    static String formatTrackDateTime(OffsetDateTime startTime, Locale locale) {
        return DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", locale).format(startTime);
    }

    static String formatTrackLine(String name, OffsetDateTime startTime, Locale locale,
                                  String namedTrackFormat) {
        String date = formatTrackDateTime(startTime, locale);
        if (TrackNameUtils.isAutoGeneratedName(name, startTime)) return date;
        return String.format(locale, namedTrackFormat, name, date);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        private final List<PersonalBest> bests;

        Adapter(List<PersonalBest> bests) {
            this.bests = bests;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(PersonalBestListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(bests.get(position));
        }

        @Override
        public int getItemCount() {
            return bests.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            private final PersonalBestListItemBinding binding;
            private Track.Id trackId;

            ViewHolder(PersonalBestListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.getRoot().setOnClickListener(this);
            }

            void bind(PersonalBest best) {
                trackId = best.trackId();
                binding.personalBestLabel.setText(best.label());
                binding.personalBestValue.setText(best.value());
                binding.personalBestUnit.setText(best.unit());
                binding.personalBestUnit.setVisibility(best.unit().isEmpty() ? View.GONE : View.VISIBLE);
                binding.personalBestTrack.setText(best.trackLine());
            }

            @Override
            public void onClick(View v) {
                Intent intent = IntentUtils.newIntent(PersonalBestsActivity.this, TrackRecordedActivity.class)
                        .putExtra(TrackRecordedActivity.EXTRA_TRACK_ID, trackId);
                startActivity(intent);
            }
        }
    }
}
