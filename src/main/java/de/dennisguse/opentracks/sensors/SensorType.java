package de.dennisguse.opentracks.sensors;

public enum SensorType {
    NONE("NONE"),
    INTERNAL("INTERNAL"),

    //NOTE: preferenceValue of REMOTE should not be used anywhere.
    REMOTE("*");

    private final String preferenceValue;

    SensorType(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }
}
