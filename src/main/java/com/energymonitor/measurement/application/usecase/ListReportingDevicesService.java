package com.energymonitor.measurement.application.usecase;

import com.energymonitor.measurement.application.port.in.ListReportingDevices;
import com.energymonitor.measurement.application.port.out.MeasurementPersistencePort;
import java.time.Instant;
import java.util.List;

/**
 * The devices with at least one reading since an instant.
 *
 * <p>This service contains no Spring annotations.
 */
public class ListReportingDevicesService implements ListReportingDevices {

    private final MeasurementPersistencePort measurements;

    public ListReportingDevicesService(MeasurementPersistencePort measurements) {
        this.measurements = measurements;
    }

    @Override
    public List<String> since(Instant since) {
        if (since == null) {
            throw new IllegalArgumentException("since is required");
        }
        return measurements.devicesReportingSince(since);
    }
}
