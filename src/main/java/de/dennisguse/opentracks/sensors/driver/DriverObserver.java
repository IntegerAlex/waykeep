package de.dennisguse.opentracks.sensors.driver;


public interface DriverObserver<T> {
    void onConnect();

    void onDisconnect();

    void onData(T value);

    void onRemove();
}
