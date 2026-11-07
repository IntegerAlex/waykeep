package com.waykeep.app.data.models;

public record AltitudeGainLoss(float gain_m, float loss_m, Source source) {

    public AltitudeGainLoss(float gain_m, float loss_m) {
        this(gain_m, loss_m, Source.UNKNOWN);
    }

    public AltitudeGainLoss {
        if (source == null) {
            source = Source.UNKNOWN;
        }
    }

    public enum Source {
        BAROMETER,
        GPS_ESTIMATE,
        MIXED,
        UNKNOWN;

        public Source merge(Source other) {
            if (this == UNKNOWN || other == UNKNOWN) return UNKNOWN;
            if (this == other) return this;
            return MIXED;
        }

        public static Source fromDatabase(String value) {
            if (value == null) return UNKNOWN;
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }

        public static Source mergeDatabaseValues(String values) {
            if (values == null || values.isBlank()) return UNKNOWN;
            Source merged = null;
            for (String value : values.split(",")) {
                Source next = fromDatabase(value.trim());
                merged = merged == null ? next : merged.merge(next);
            }
            return merged == null ? UNKNOWN : merged;
        }
    }
}
