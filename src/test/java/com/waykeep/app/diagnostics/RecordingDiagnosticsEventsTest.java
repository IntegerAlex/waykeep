package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Altitude;
import com.waykeep.app.data.models.AltitudeGainLoss;
import com.waykeep.app.data.models.Cadence;
import com.waykeep.app.data.models.HeartRate;
import com.waykeep.app.data.models.Power;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.Speed;
import com.waykeep.app.data.models.Temperature;
import com.waykeep.app.data.models.TrackPoint;

public class RecordingDiagnosticsEventsTest {

    @Test
    public void gpsFixIncludesRawPositionAndAccuracyDecision() {
        Position position = new Position(
                Instant.parse("2026-10-30T01:44:00Z"), 18.5, 73.8,
                Distance.of(82), null, null, 91f, Speed.of(2.8));

        Map<String, Object> fields = RecordingDiagnosticEvents.gpsFix(
                position, 1793324679000L, "gps", 123456789L, Distance.of(50), "rejected_accuracy");

        assertEquals(18.5, fields.get("latitude"));
        assertEquals(73.8, fields.get("longitude"));
        assertEquals(82.0, fields.get("horizontal_accuracy_m"));
        assertEquals(50.0, fields.get("accuracy_threshold_m"));
        assertEquals(1793324679000L, fields.get("provider_time_millis"));
        assertEquals(123456789L, fields.get("elapsed_realtime_nanos"));
        assertEquals("rejected_accuracy", fields.get("decision"));
    }

    @Test
    public void logsGpsStatusWithoutAnyFix() {
        Map<String, Object> fields = RecordingDiagnosticEvents.gpsStatus("GPS_SIGNAL_LOST", false);

        assertEquals("GPS_SIGNAL_LOST", fields.get("status"));
        assertEquals(false, fields.get("has_received_fix"));
    }

    @Test
    public void trackpointWriteFailureIncludesErrorClass() {
        Map<String, Object> fields = RecordingDiagnosticEvents.persistenceResult(false, 2, "SQLiteException");

        assertEquals(false, fields.get("success"));
        assertEquals(2, fields.get("attempts"));
        assertEquals("SQLiteException", fields.get("error_class"));
    }

    @Test
    public void trackPointDiagnosticIncludesSensorFields() {
        Position position = new Position(Instant.parse("2026-10-30T01:44:00Z"),
                18.5, 73.8, Distance.of(8), Altitude.WGS84.of(500), Distance.of(12), 90f, Speed.of(3.2));
        TrackPoint point = new TrackPoint(null, TrackPoint.Type.TRACKPOINT, position,
                 Distance.of(100), HeartRate.of(140), Temperature.of(23), Cadence.of(80),
                 Power.of(200), new AltitudeGainLoss(5, 2, AltitudeGainLoss.Source.GPS_ESTIMATE));

        Map<String, Object> fields = RecordingDiagnosticEvents.trackPoint(point);

        assertEquals("TRACKPOINT", fields.get("type"));
        assertEquals(18.5, fields.get("latitude"));
        assertEquals(140f, (Float) fields.get("heart_rate_bpm"), 0.01f);
        assertEquals(80f, (Float) fields.get("cadence_rpm"), 0.01f);
        assertEquals(200f, (Float) fields.get("power_watts"), 0.01f);
        assertEquals(5f, (Float) fields.get("altitude_gain_m"), 0.01f);
        assertEquals("GPS_ESTIMATE", fields.get("altitude_gain_loss_source"));
    }

    @Test
    public void trackPointDecisionOmitsCandidatePositionAndSensorFields() {
        Position position = new Position(Instant.parse("2026-10-30T01:44:00Z"),
                18.5, 73.8, Distance.of(8), Altitude.WGS84.of(500), Distance.of(12), 90f, Speed.of(3.2));
        TrackPoint point = new TrackPoint(null, TrackPoint.Type.TRACKPOINT, position,
                Distance.of(100), HeartRate.of(140), Temperature.of(23), Cadence.of(80),
                Power.of(200), new AltitudeGainLoss(5, 2));

        Map<String, Object> fields = RecordingDiagnosticEvents.trackPointDecision(point, "below_distance_interval");

        assertEquals(Map.of(
                "time", "2026-10-30T01:44:00Z",
                "type", "TRACKPOINT",
                "decision", "below_distance_interval"), fields);
    }

    @Test
    public void gnssStatusIncludesSatelliteCounts() {
        Map<String, Object> fields = RecordingDiagnosticEvents.gnssStatus(14, 6, 41.5, 12345L);

        assertEquals(14, fields.get("satellites_visible"));
        assertEquals(6, fields.get("satellites_used_in_fix"));
        assertEquals(41.5, fields.get("max_snr_db"));
        assertEquals(12345L, fields.get("since_connect_ms"));
    }

    @Test
    public void gnssFirstFixIncludesTtff() {
        Map<String, Object> fields = RecordingDiagnosticEvents.gnssFirstFix(201_611);

        assertEquals(201_611, fields.get("ttff_ms"));
    }

    @Test
    public void gnssAssistanceRecordsRejectionAndError() {
        Map<String, Object> accepted = RecordingDiagnosticEvents.gnssAssistance("force_xtra_injection", true, null);
        Map<String, Object> rejected = RecordingDiagnosticEvents.gnssAssistance("force_time_injection", false, "SecurityException");

        assertEquals("force_xtra_injection", accepted.get("command"));
        assertEquals(true, accepted.get("accepted"));
        assertEquals(null, accepted.get("error_class"));
        assertEquals("force_time_injection", rejected.get("command"));
        assertEquals(false, rejected.get("accepted"));
        assertEquals("SecurityException", rejected.get("error_class"));
    }

    @Test
    public void deviceStateOmitsNetworkIdentifiers() {
        Map<String, Object> fields = RecordingDiagnosticEvents.deviceState(
                41, true, true, false, true,
                "wifi", true, true, true, true,
                "POCO", "25028PC03I", 35);

        assertEquals("wifi", fields.get("network_transport"));
        assertEquals(true, fields.get("network_connected"));
        assertFalse(fields.containsKey("ssid"));
        assertFalse(fields.containsKey("bssid"));
        assertFalse(fields.containsKey("cell_id"));
    }
}
