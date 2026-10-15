package com.waykeep.app.data.models;

public record BatteryLevel(int percentage) {

    public static BatteryLevel of(int percentage) {
        return new BatteryLevel(percentage);
    }
}
