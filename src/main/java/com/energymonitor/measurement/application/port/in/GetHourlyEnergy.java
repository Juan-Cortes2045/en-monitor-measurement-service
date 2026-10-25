package com.energymonitor.measurement.application.port.in;

import com.energymonitor.measurement.application.result.HourlyEnergyResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Input port for the energy of several devices hour by hour, computed from the cumulative counter
 * the same way as the consumption of a home. energy-monitor-backend asks it for the consumption
 * limits of its alerts and for its recommendations.
 */
public interface GetHourlyEnergy {

    /**
     * @param deviceIds the devices
     * @param from      inclusive start of the range
     * @param to        inclusive end of the range
     * @return one entry per device and UTC hour with readings, oldest first; empty for no device
     * @throws IllegalArgumentException when the range is missing or ends before it starts
     */
    List<HourlyEnergyResult> hourly(Collection<String> deviceIds, Instant from, Instant to);
}
