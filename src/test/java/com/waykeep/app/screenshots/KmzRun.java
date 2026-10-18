package com.waykeep.app.screenshots;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Test-only reader for the user's real run (test.kmz, exported by Waykeep itself).
 * Plain JVM only: no Android classes, so it runs inside Paparazzi unit tests.
 * KML coordinates are "lon,lat,alt".
 */
final class KmzRun {

    record Point(Instant time, double lat, double lon, double alt, boolean hasLocation) {
    }

    final String name;
    final String activityType;
    final List<Point> points;

    private KmzRun(String name, String activityType, List<Point> points) {
        this.name = name;
        this.activityType = activityType;
        this.points = points;
    }

    static KmzRun load() throws Exception {
        byte[] kml;
        try (InputStream in = KmzRun.class.getResourceAsStream("/test.kmz");
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] found = null;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".kml")) {
                    found = readAll(zip);
                    break;
                }
            }
            if (found == null) {
                throw new IllegalStateException("No doc.kml in test.kmz");
            }
            kml = found;
        }

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(kml));
        doc.getDocumentElement().normalize();

        String name = text(doc, "Document/name");
        String activityType = "run";
        NodeList data = doc.getElementsByTagName("Data");
        for (int i = 0; i < data.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) data.item(i);
            if ("activityType".equals(el.getAttribute("name"))) {
                activityType = el.getElementsByTagName("value").item(0).getTextContent();
            }
        }

        NodeList whens = doc.getElementsByTagName("when");
        NodeList coords = doc.getElementsByTagName("coord");
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < whens.getLength(); i++) {
            Instant time = OffsetDateTime.parse(whens.item(i).getTextContent().trim()).toInstant();
            String coord = i < coords.getLength() ? coords.item(i).getTextContent().trim() : "";
            if (coord.isEmpty()) {
                points.add(new Point(time, 0, 0, 0, false));
            } else {
                String[] parts = coord.split("[,\\s]+");
                points.add(new Point(time, Double.parseDouble(parts[1]), Double.parseDouble(parts[0]),
                        parts.length > 2 ? Double.parseDouble(parts[2]) : 0, true));
            }
        }
        return new KmzRun(name, activityType, points);
    }

    /** Haversine total over located points, meters. */
    double distance_m() {
        double total = 0;
        Point prev = null;
        for (Point p : points) {
            if (!p.hasLocation()) {
                continue;
            }
            if (prev != null) {
                double dLat = Math.toRadians(p.lat() - prev.lat());
                double dLon = Math.toRadians(p.lon() - prev.lon());
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(prev.lat())) * Math.cos(Math.toRadians(p.lat()))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
                total += 2 * 6371000 * Math.asin(Math.sqrt(a));
            }
            prev = p;
        }
        return total;
    }

    java.time.Duration duration() {
        return java.time.Duration.between(points.get(0).time(), points.get(points.size() - 1).time());
    }

    Instant startTime() {
        return points.get(0).time();
    }

    /** Sum of positive altitude deltas, meters. */
    double gain_m() {
        double gain = 0;
        Double prev = null;
        for (Point p : points) {
            if (!p.hasLocation()) {
                continue;
            }
            if (prev != null && p.alt() > prev) {
                gain += p.alt() - prev;
            }
            prev = p.alt();
        }
        return gain;
    }

    List<double[]> locatedLatLon() {
        List<double[]> out = new ArrayList<>();
        for (Point p : points) {
            if (p.hasLocation()) {
                out.add(new double[]{p.lat(), p.lon()});
            }
        }
        return out;
    }

    private static String text(Document doc, String path) {
        String[] tags = path.split("/");
        org.w3c.dom.Element el = doc.getDocumentElement();
        for (String tag : tags) {
            NodeList nodes = el.getElementsByTagName(tag);
            if (nodes.getLength() == 0) {
                return "";
            }
            el = (org.w3c.dom.Element) nodes.item(0);
        }
        return el.getTextContent().trim();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
