package de.dennisguse.opentracks.ui.aggregatedStatistics;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Track;

public class AggregatedStatistics {

    private final List<AggregatedStatistic> dataList;

    public AggregatedStatistics(@NonNull List<Track> tracks) {
        Map<String, List<Track>> groupedByActivityType = tracks.stream().collect(Collectors.groupingBy(Track::getActivityTypeLocalized));

        List<Pair<String, List<Track>>> groupedByActivityTypeList = new ArrayList<>();
        groupedByActivityType.forEach((activityTypeLocalized, trackList) -> groupedByActivityTypeList.add(new Pair<>(activityTypeLocalized, trackList)));

        dataList = groupedByActivityTypeList.parallelStream()
                .map(i -> new AggregatedStatistic(i.first, i.second))
                .sorted((o1, o2) -> {
                    if (o1.getCountTracks() == o2.getCountTracks()) {
                        return o1.getActivityTypeLocalized().compareTo(o2.getActivityTypeLocalized());
                    }
                    return (o1.getCountTracks() < o2.getCountTracks() ? 1 : -1);
                }).toList();
    }

    public int getCount() {
        return dataList.size();
    }

    @VisibleForTesting
    public AggregatedStatistic get(String activityTypeLocalized) {
        return dataList.stream().filter(i -> i.getActivityTypeLocalized().equals(activityTypeLocalized)).findFirst().get();
    }

    public AggregatedStatistic getItem(int position) {
        return dataList.get(position);
    }

    public static class AggregatedStatistic {
        final String activityTypeLocalized;
        final int countTracks;

        final Duration totalMovingTime;

        final Distance totalDistance;

        final Speed avgMovingSpeed;

        final Speed maxSpeed;

        public AggregatedStatistic(String activityTypeLocalized, List<Track> tracks) {
            this.activityTypeLocalized = activityTypeLocalized;
            this.countTracks = tracks.size();

            totalMovingTime = tracks.stream().map(t -> t.getStatistics().movingTime()).reduce(Duration.ZERO, Duration::plus);
            totalDistance = tracks.stream().map(t -> t.getStatistics().totalDistance()).reduce(Distance.ZERO, Distance::plus);
            maxSpeed = tracks.stream().map(t -> t.getStatistics().maxSpeed()).max((o1, o2) -> (int) (o1.speed_mps() - o2.speed_mps())).orElse(Speed.ZERO);
            avgMovingSpeed = Speed.of(totalDistance, totalMovingTime);
        }

        public String getActivityTypeLocalized() {
            return activityTypeLocalized;
        }

        public int getCountTracks() {
            return countTracks;
        }
    }
}
