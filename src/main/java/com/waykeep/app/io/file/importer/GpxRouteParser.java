package com.waykeep.app.io.file.importer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * Reads coordinates from a GPX file for route guidance. Unlike the track importer this does not
 * need timestamps: it accepts {@code trkpt} and {@code rtept} elements and keeps their order.
 */
public final class GpxRouteParser {

    public static final int MAX_POINTS = 20_000;

    private GpxRouteParser() {
    }

    /**
     * Parses GPX coordinates from {@code input}. Points with missing or invalid coordinates are
     * skipped. Parsing stops as soon as {@code maxPoints} valid points have been read.
     *
     * @throws IOException  if the stream cannot be read
     * @throws SAXException if the document is not well-formed XML
     */
    public static List<RoutePoint> parse(InputStream input, int maxPoints) throws IOException, SAXException {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        if (maxPoints <= 0) {
            return new ArrayList<>();
        }

        RouteHandler handler = new RouteHandler(maxPoints);
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.newSAXParser().parse(input, handler);
        } catch (ParserConfigurationException e) {
            throw new SAXException(e);
        } catch (StopParsing ignored) {
            // The point cap was reached; the handler holds the points read so far.
        }
        return handler.points;
    }

    private static final class RouteHandler extends DefaultHandler {

        private final int maxPoints;
        private final List<RoutePoint> points = new ArrayList<>();

        private RouteHandler(int maxPoints) {
            this.maxPoints = maxPoints;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            String name = localName.isEmpty() ? qName : localName;
            if (!"trkpt".equals(name) && !"rtept".equals(name)) {
                return;
            }

            RoutePoint point = readPoint(attributes.getValue("lat"), attributes.getValue("lon"));
            if (point == null) {
                return;
            }
            points.add(point);
            if (points.size() >= maxPoints) {
                throw new StopParsing();
            }
        }

        private static RoutePoint readPoint(String latitude, String longitude) {
            if (latitude == null || longitude == null) {
                return null;
            }
            try {
                double lat = Double.parseDouble(latitude.trim());
                double lon = Double.parseDouble(longitude.trim());
                if (!Double.isFinite(lat) || !Double.isFinite(lon)
                        || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    return null;
                }
                return new RoutePoint(lat, lon);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Aborts the SAX parse once the point cap is reached. It never escapes {@link #parse}.
     */
    private static final class StopParsing extends SAXException {

        private StopParsing() {
            super("point cap reached");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
