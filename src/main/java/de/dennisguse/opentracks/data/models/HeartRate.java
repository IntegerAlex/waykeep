package de.dennisguse.opentracks.data.models;

public record HeartRate(float value) {
    public static HeartRate INVALID = HeartRate.of(0.0f);

    public static HeartRate of(float value) {
        return new HeartRate(value);
    }

    public float getBPM() {
        return value;
    }

    public boolean isValid() {
        return !this.equals(INVALID);
    }
}
