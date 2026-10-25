package com.energymonitor.measurement.application.usecase;

import com.energymonitor.measurement.application.port.in.GetHourlyEnergy;
import com.energymonitor.measurement.application.port.out.MeasurementPersistencePort;
import com.energymonitor.measurement.application.result.HourlyEnergyResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The energy of several devices hour by hour, with the same {@link HourlyEnergyCalculator} the
 * consumption of a home uses, so every figure of a device agrees wherever it is read.
 *
 * <p>This service contains no Spring annotations.
 */
public class GetHourlyEnergyService implements GetHourlyEnergy {

    private final HourlyEnergyCalculator calculator;

    public GetHourlyEnergyService(MeasurementPersistencePort measurements) {
        this.calculator = new HourlyEnergyCalculator(measurements);
    }

    @Override
    public List<HourlyEnergyResult> hourly(Collection<String> deviceIds, Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("from and to are required, and to must not be before from");
        }
        if (deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        return calculator.compute(deviceIds, from, to).entrySet().stream()
                .flatMap(device -> device.getValue().stream()
                        .map(hour -> new HourlyEnergyResult(device.getKey(), hour.start(),
                                hour.averagePower(), hour.energy())))
                .toList();
    }
}
