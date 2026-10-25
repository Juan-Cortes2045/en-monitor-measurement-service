package com.energymonitor.measurement.application.exception;

/**
 * Thrown when the device service does not answer which home a device is linked to, or which
 * devices a home has, and no earlier answer was kept to fall back on. The request is refused as
 * unavailable (503).
 */
public class DeviceServiceUnavailableException extends MeasurementApplicationException {

    public DeviceServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
