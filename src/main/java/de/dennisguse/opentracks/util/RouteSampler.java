package de.dennisguse.opentracks.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Downsamples a track's points to a plottable route.
 * Shared by the feed thumbnail and the share-image generator so both draw the same line.
 */
public class RouteSampler {

    private RouteSampler() {
    }

    public record LatLon(double latitude, double longitude) {
    }

    public record Route(@NonNull List<LatLon> points) {
        public boolean isEmpty() {
            return points.size() < 2;
        }
    }

    public static Route sample(@NonNull ContentProviderUtils contentProviderUtils, @NonNull Track.Id trackId, int maxPoints) {
        List<LatLon> points = new ArrayList<>();
        try (TrackPointIterator iterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            int total = Math.max(iterator.getCount(), 1);
            int step = Math.max(1, total / Math.max(maxPoints, 1));
            int index = 0;
            while (iterator.hasNext()) {
                TrackPoint trackPoint = iterator.next();
                if (index++ % step != 0) {
                    continue;
                }
                if (!trackPoint.position().hasLocation()) {
                    continue;
                }
                points.add(new LatLon(trackPoint.position().latitude(), trackPoint.position().longitude()));
            }
        } catch (RuntimeException e) {
            return new Route(List.of());
        }
        return new Route(points);
    }
}
