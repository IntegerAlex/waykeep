package com.waykeep.app.ui.suggested;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates running loops via the public BRouter server (prototype path; the offline engine
 * with bundled segments is the planned replacement).
 * <p>
 * Round trips use the server's round-trip mode ({@code lonlats=lon,lat,r}): the loop length is
 * dominated by the road network, so three directions are requested in parallel and the
 * measured totals are shown for each option.
 */
public class BrouterClient {

    private static final String ENDPOINT = "https://brouter.de/brouter";

    public interface Callback {
        void onResult(RouteSuggestion suggestion);

        void onError(Exception e);
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public void shutdown() {
        executor.shutdownNow();
    }

    public void suggest(double lon, double lat, double desiredLengthM, String profile, Callback callback) {
        // Round-trip length is network-dominated; this only sizes the via-point circle.
        double roundtripDistance = Math.max(500, Math.min(5000, desiredLengthM / 4));
        int[] directions = {30, 150, 270};
        for (int direction : directions) {
            executor.execute(() -> {
                try {
                    callback.onResult(fetch(lon, lat, roundtripDistance, direction, profile));
                } catch (Exception e) {
                    callback.onError(e);
                }
            });
        }
    }

    private RouteSuggestion fetch(double lon, double lat, double roundtripDistance, int direction, String profile) throws Exception {
        String url = ENDPOINT + "?lonlats=" + lon + "," + lat + ",r"
                + "&profile=" + profile + "&alternativeidx=0&format=geojson"
                + "&roundtripDistance=" + Math.round(roundtripDistance)
                + "&direction=" + direction;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("BRouter HTTP " + code);
            }
            return parse(readFully(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    private RouteSuggestion parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject feature = root.getJSONArray("features").getJSONObject(0);
        JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
        List<GeoPoint> points = new ArrayList<>(coordinates.length());
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray point = coordinates.getJSONArray(i);
            double ele = point.length() > 2 ? point.optDouble(2, 0) : 0;
            points.add(new GeoPoint(point.getDouble(1), point.getDouble(0), ele));
        }
        if (points.size() < 2) {
            throw new IOException("BRouter returned an empty route");
        }
        return new RouteSuggestion(points, measureDistanceM(points), measureGainM(points));
    }

    private double measureDistanceM(List<GeoPoint> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineM(points.get(i - 1), points.get(i));
        }
        return total;
    }

    private double measureGainM(List<GeoPoint> points) {
        double gain = 0;
        for (int i = 1; i < points.size(); i++) {
            double delta = points.get(i).getAltitude() - points.get(i - 1).getAltitude();
            if (delta > 0) {
                gain += delta;
            }
        }
        return gain;
    }

    private double haversineM(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude())) * sinLon * sinLon;
        return 2 * 6371000 * Math.asin(Math.sqrt(h));
    }

    public static String formatDistance(double distanceM) {
        if (distanceM >= 10000) {
            return String.format(Locale.getDefault(), "%.0f km", distanceM / 1000);
        }
        return String.format(Locale.getDefault(), "%.1f km", distanceM / 1000);
    }

    public static String formatGain(double gainM) {
        return String.format(Locale.getDefault(), "+%.0f m", gainM);
    }
}
