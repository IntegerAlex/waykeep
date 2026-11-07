package com.waykeep.app.io.file.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GpxRouteParserTest {

    private static List<RoutePoint> parse(String gpx) throws IOException, SAXException {
        return parse(gpx, 20_000);
    }

    private static List<RoutePoint> parse(String gpx, int maxPoints) throws IOException, SAXException {
        try (InputStream input = new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8))) {
            return GpxRouteParser.parse(input, maxPoints);
        }
    }

    @Test
    public void parsesTrackPointsAndIgnoresTime() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <trk>
                    <trkseg>
                      <trkpt lat="48.1374" lon="11.5755">
                        <ele>519.0</ele>
                        <time>2026-11-07T08:00:00Z</time>
                      </trkpt>
                      <trkpt lat="48.1380" lon="11.5760">
                        <time>2026-11-07T08:00:10Z</time>
                      </trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """);

        assertEquals(2, points.size());
        assertEquals(48.1374, points.get(0).latitude(), 1e-9);
        assertEquals(11.5755, points.get(0).longitude(), 1e-9);
        assertEquals(48.1380, points.get(1).latitude(), 1e-9);
        assertEquals(11.5760, points.get(1).longitude(), 1e-9);
    }

    @Test
    public void parsesRoutePoints() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <rte>
                    <rtept lat="-33.8688" lon="151.2093"/>
                    <rtept lat="-33.8700" lon="151.2100"/>
                  </rte>
                </gpx>
                """);

        assertEquals(2, points.size());
        assertEquals(-33.8688, points.get(0).latitude(), 1e-9);
        assertEquals(151.2093, points.get(0).longitude(), 1e-9);
    }

    @Test
    public void parsesMixedTrackAndRoutePointsInDocumentOrder() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <rte>
                    <rtept lat="1.0" lon="10.0"/>
                  </rte>
                  <trk>
                    <trkseg>
                      <trkpt lat="2.0" lon="20.0"/>
                    </trkseg>
                  </trk>
                  <rte>
                    <rtept lat="3.0" lon="30.0"/>
                  </rte>
                </gpx>
                """);

        assertEquals(3, points.size());
        assertEquals(1.0, points.get(0).latitude(), 1e-9);
        assertEquals(2.0, points.get(1).latitude(), 1e-9);
        assertEquals(3.0, points.get(2).latitude(), 1e-9);
    }

    @Test
    public void skipsPointsWithMissingCoordinates() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <trk>
                    <trkseg>
                      <trkpt lon="10.0"/>
                      <trkpt lat="1.0"/>
                      <trkpt lat="2.0" lon="20.0"/>
                    </trkseg>
                  </trk>
                </gpx>
                """);

        assertEquals(1, points.size());
        assertEquals(2.0, points.get(0).latitude(), 1e-9);
        assertEquals(20.0, points.get(0).longitude(), 1e-9);
    }

    @Test
    public void skipsPointsWithInvalidCoordinates() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test">
                  <trk>
                    <trkseg>
                      <trkpt lat="not-a-number" lon="10.0"/>
                      <trkpt lat="91.0" lon="10.0"/>
                      <trkpt lat="1.0" lon="181.0"/>
                      <trkpt lat="NaN" lon="10.0"/>
                      <trkpt lat="3.0" lon="30.0"/>
                    </trkseg>
                  </trk>
                </gpx>
                """);

        assertEquals(1, points.size());
        assertEquals(3.0, points.get(0).latitude(), 1e-9);
    }

    @Test
    public void acceptsNamespacePrefixedElements() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx:gpx xmlns:gpx="http://www.topografix.com/GPX/1/1" version="1.1">
                  <gpx:trk>
                    <gpx:trkseg>
                      <gpx:trkpt lat="4.0" lon="40.0"/>
                    </gpx:trkseg>
                  </gpx:trk>
                </gpx:gpx>
                """);

        assertEquals(1, points.size());
        assertEquals(4.0, points.get(0).latitude(), 1e-9);
    }

    @Test
    public void capsAtMaxPoints() throws Exception {
        StringBuilder gpx = new StringBuilder("<gpx><trk><trkseg>");
        for (int i = 0; i < 10; i++) {
            gpx.append("<trkpt lat=\"").append(i).append("\" lon=\"").append(i).append("\"/>");
        }
        gpx.append("</trkseg></trk></gpx>");

        List<RoutePoint> points = parse(gpx.toString(), 3);

        assertEquals(3, points.size());
        assertEquals(0.0, points.get(0).latitude(), 1e-9);
        assertEquals(2.0, points.get(2).latitude(), 1e-9);
    }

    @Test
    public void returnsEmptyListForGpxWithoutPoints() throws Exception {
        List<RoutePoint> points = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="test"></gpx>
                """);

        assertTrue(points.isEmpty());
    }

    @Test
    public void throwsOnMalformedXml() throws Exception {
        try {
            parse("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <gpx><trk><trkseg><trkpt lat="1.0" lon="2.0">
                    """);
            fail("Expected a SAXException for malformed XML");
        } catch (SAXException expected) {
            // Malformed input must fail safely, not silently return a partial route.
        }
    }

    @Test
    public void refusesExternalEntities() throws Exception {
        try {
            parse("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
                    <gpx><trk><trkseg><trkpt lat="&xxe;" lon="0.0"/></trkseg></trk></gpx>
                    """);
            // Some parsers may instead skip the unreadable entity; either way no file data
            // may end up in the route. A throwing parser is the expected JVM behaviour.
            fail("External entities must not be expanded into a route");
        } catch (SAXException expected) {
            // XXE blocked by the external-entity features.
        }
    }
}
