package de.dennisguse.opentracks.ui.personalBests;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.dennisguse.opentracks.AbstractActivity;
import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TrackRecordedActivity;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.DistanceFormatter;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.SpeedFormatter;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.databinding.ActivityPersonalBestsBinding;
import de.dennisguse.opentracks.databinding.PersonalBestListItemBinding;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.util.IntentUtils;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Shows all-time personal bests (longest, fastest, most elevation) across all tracks.
 * Everything is computed and formatted off the UI thread; each row opens the record track.
 */
public class PersonalBestsActivity extends AbstractActivity {

    public record PersonalBest(
            int labelResId,
            @NonNull String value,
            @NonNull String unit,
            @NonNull Track.Id trackId,
            @NonNull String trackLine) {
    }

    private ActivityPersonalBestsBinding viewBinding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewBinding.personalBestsList.setLayoutManager(new LinearLayoutManager(this));
        loadBests();
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

    private void loadBests() {
        executor.execute(() -> {
            List<PersonalBest> bests;
            try {
                bests = computeBests(this, new ContentProviderUtils(this).getTracks());
            } catch (RuntimeException e) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || viewBinding == null) {
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

    private static List<PersonalBest> computeBests(@NonNull Context context, @NonNull List<Track> tracks) {
        Track longest = null;
        Track longestTime = null;
        Track fastest = null;
        Track mostElevation = null;
        Track topSpeed = null;
        for (Track track : tracks) {
            if (longest == null || track.statistics().totalDistance().greaterThan(longest.statistics().totalDistance())) {
                longest = track;
            }
            if (longestTime == null || track.statistics().movingDuration().compareTo(longestTime.statistics().movingDuration()) > 0) {
                longestTime = track;
            }
            if (fastest == null || track.statistics().getAverageMovingSpeed().greaterThan(fastest.statistics().getAverageMovingSpeed())) {
                fastest = track;
            }
            if (gainOf(track) != null && (mostElevation == null || gainOf(track) > gainOf(mostElevation))) {
                mostElevation = track;
            }
            if (topSpeed == null || track.statistics().maxSpeed().greaterThan(topSpeed.statistics().maxSpeed())) {
                topSpeed = track;
            }
        }

        UnitSystem unitSystem = PreferencesUtils.getUnitSystem();
        List<PersonalBest> bests = new ArrayList<>();
        if (longest != null) {
            bests.add(new PersonalBest(R.string.personal_best_longest_distance,
                    DistanceFormatter.Builder().setUnit(unitSystem).build(context).formatDistance(longest.statistics().totalDistance()),
                    "", longest.id(), trackLine(context, longest)));
        }
        if (longestTime != null) {
            bests.add(new PersonalBest(R.string.personal_best_longest_time,
                    StringUtils.formatElapsedTime(longestTime.statistics().movingDuration()),
                    "", longestTime.id(), trackLine(context, longestTime)));
        }
        if (fastest != null) {
            bests.add(new PersonalBest(R.string.personal_best_fastest,
                    formatSpeed(context, fastest, fastest.statistics().getAverageMovingSpeed(), unitSystem),
                    "", fastest.id(), trackLine(context, fastest)));
        }
        if (mostElevation != null) {
            android.util.Pair<String, String> parts = StringUtils.getAltitudeParts(context,
                    mostElevation.statistics().altitudeGainLoss().gain_m(), unitSystem);
            bests.add(new PersonalBest(R.string.personal_best_most_elevation,
                    context.getString(R.string.altitude_with_unit, parts.first, parts.second),
                    "", mostElevation.id(), trackLine(context, mostElevation)));
        }
        if (topSpeed != null) {
            bests.add(new PersonalBest(R.string.personal_best_top_speed,
                    formatSpeed(context, topSpeed, topSpeed.statistics().maxSpeed(), unitSystem),
                    "", topSpeed.id(), trackLine(context, topSpeed)));
        }
        return bests;
    }

    private static String formatSpeed(Context context, Track track, Speed speed, UnitSystem unitSystem) {
        return SpeedFormatter.Builder().setUnit(unitSystem).setReportSpeedOrPace(PreferencesUtils.isReportSpeed(track))
                .build(context).formatSpeed(speed);
    }

    private static String trackLine(Context context, Track track) {
        return context.getString(R.string.personal_best_track, track.name(),
                StringUtils.formatDateTimeWithOffset(track.startTime()));
    }

    private static Float gainOf(Track track) {
        return track.statistics().altitudeGainLoss() != null ? track.statistics().altitudeGainLoss().gain_m() : null;
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
                binding.personalBestLabel.setText(best.labelResId());
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
