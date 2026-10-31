package com.waykeep.app.diagnostics;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import com.waykeep.app.data.models.Distance;
import com.waykeep.app.data.models.Position;
import com.waykeep.app.data.models.TrackPoint;

/** Primitive event payloads shared by recording callbacks and tests. */
public final class RecordingDiagnosticEvents {

    private RecordingDiagnosticEvents() {
    }

    public static Map<String, Object> gpsFix(Position position, long providerTimeMillis, String provider, long elapsedRealtimeNanos,
                                             Distance accuracyThreshold, String decision) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("provider", provider);
        fields.put("fix_time", position.time() != null ? position.time().toString() : null);
        fields.put("provider_time_millis", providerTimeMillis);
        fields.put("elapsed_realtime_nanos", elapsedRealtimeNanos);
        fields.put("latitude", position.latitude());
        fields.put("longitude", position.longitude());
        fields.put("horizontal_accuracy_m", position.hasHorizontalAccuracy() ? position.horizontalAccuracy().toM() : null);
        fields.put("accuracy_threshold_m", accuracyThreshold.toM());
        fields.put("altitude_m", position.hasAltitude() ? position.altitude().toM() : null);
        fields.put("vertical_accuracy_m", position.hasVerticalAccuracy() ? position.verticalAccuracy().toM() : null);
        fields.put("speed_mps", position.hasSpeed() ? position.speed().toMPS() : null);
        fields.put("bearing_degrees", position.bearing());
        fields.put("decision", decision);
        return fields;
    }

    public static Map<String, Object> gpsStatus(String status, boolean hasReceivedFix) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", status);
        fields.put("has_received_fix", hasReceivedFix);
        return fields;
    }

    /** Satellite visibility while acquiring: distinguishes "chip sees nothing" from "chip is working". */
    public static Map<String, Object> gnssStatus(int satellitesVisible, int satellitesUsedInFix,
                                                 @Nullable Double maxSnrDb, long sinceConnectMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("satellites_visible", satellitesVisible);
        fields.put("satellites_used_in_fix", satellitesUsedInFix);
        fields.put("max_snr_db", maxSnrDb);
        fields.put("since_connect_ms", sinceConnectMs);
        return fields;
    }

    /** Time to first fix reported by the GNSS engine itself. */
    public static Map<String, Object> gnssFirstFix(int ttffMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ttff_ms", ttffMs);
        return fields;
    }

    /** Whether the ROM accepted an A-GPS injection command (#2410). */
    public static Map<String, Object> gnssAssistance(String command, boolean accepted, @Nullable String errorClass) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("command", command);
        fields.put("accepted", accepted);
        fields.put("error_class", errorClass);
        return fields;
    }

    public static Map<String, Object> persistenceResult(boolean success, int attempts, @Nullable String errorClass) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("success", success);
        fields.put("attempts", attempts);
        fields.put("error_class", errorClass);
        return fields;
    }

    public static Map<String, Object> trackPoint(TrackPoint point) {
        Position position = point.position();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("time", point.getTime().toString());
        fields.put("type", point.type().name());
        fields.put("latitude", position.latitude());
        fields.put("longitude", position.longitude());
        fields.put("horizontal_accuracy_m", position.hasHorizontalAccuracy() ? position.horizontalAccuracy().toM() : null);
        fields.put("altitude_m", position.hasAltitude() ? position.altitude().toM() : null);
        fields.put("vertical_accuracy_m", position.hasVerticalAccuracy() ? position.verticalAccuracy().toM() : null);
        fields.put("speed_mps", position.hasSpeed() ? position.speed().toMPS() : null);
        fields.put("bearing_degrees", position.bearing());
        fields.put("sensor_distance_m", point.sensorDistance() != null ? point.sensorDistance().toM() : null);
        fields.put("heart_rate_bpm", point.heartRate() != null ? point.heartRate().getBPM() : null);
        fields.put("temperature_c", point.temperature() != null ? point.temperature().getCelsius() : null);
        fields.put("cadence_rpm", point.cadence() != null ? point.cadence().value_rpm() : null);
        fields.put("power_watts", point.power() != null ? point.power().getW() : null);
        fields.put("altitude_gain_m", point.altitudeGainLoss() != null ? point.altitudeGainLoss().gain_m() : null);
        fields.put("altitude_loss_m", point.altitudeGainLoss() != null ? point.altitudeGainLoss().loss_m() : null);
        return fields;
    }

    public static Map<String, Object> deviceState(int batteryPercent, boolean charging, boolean powerSave,
                                                  boolean deviceIdle, boolean screenInteractive,
                                                  String networkTransport, boolean networkConnected,
                                                  boolean fineLocation, boolean backgroundLocation,
                                                  boolean gpsEnabled, String manufacturer, String model,
                                                  int apiLevel) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("battery_percent", batteryPercent);
        fields.put("charging", charging);
        fields.put("power_save", powerSave);
        fields.put("device_idle", deviceIdle);
        fields.put("screen_interactive", screenInteractive);
        fields.put("network_transport", networkTransport);
        fields.put("network_connected", networkConnected);
        fields.put("fine_location_permission", fineLocation);
        fields.put("background_location_permission", backgroundLocation);
        fields.put("gps_provider_enabled", gpsEnabled);
        fields.put("manufacturer", manufacturer);
        fields.put("device_model", model);
        fields.put("android_api", apiLevel);
        return fields;
    }
}
