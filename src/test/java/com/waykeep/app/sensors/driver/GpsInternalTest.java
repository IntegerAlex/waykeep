package com.waykeep.app.sensors.driver;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GpsInternalTest {

    @Test
    public void sendsBothAssistanceCommandsInOrder() {
        List<String> commands = new ArrayList<>();

        GpsInternal.sendAssistanceCommands(commands::add);

        assertEquals(List.of("force_xtra_injection", "force_time_injection"), commands);
    }
}
