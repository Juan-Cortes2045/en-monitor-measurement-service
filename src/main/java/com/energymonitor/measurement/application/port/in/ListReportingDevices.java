package com.energymonitor.measurement.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Input port for the devices that published a reading recently; energy-monitor-backend evaluates
 * its recommendations for them.
 */
public interface ListReportingDevices {

    /**
     * @param since the earliest reading that counts
     * @return the devices with at least one reading since then
     * @throws IllegalArgumentException when {@code since} is missing
     */
    List<String> since(Instant since);
}
