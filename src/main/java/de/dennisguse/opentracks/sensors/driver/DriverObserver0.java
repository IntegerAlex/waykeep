package de.dennisguse.opentracks.sensors.driver;

import de.dennisguse.opentracks.sensors.sensorData.Aggregator;

//TODO Make onChange parametrized
public interface DriverObserver0 {

    //TODO Pass SensorName as parameter only
    void onConnect(Aggregator<?,?> emptySensorData);

    //TODO Remove parameter
    void onDisconnect(Aggregator<?,?> emptySensorData);

    //TODO Remove parameter
    void onRemove(Aggregator<?,?> emptySensorData);

    void onChange(Record value);

}
