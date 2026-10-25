package com.energymonitor.measurement.application.port.out;

/**
 * Whether a device is registered: a reading is only stored for a device the device service knows.
 */
public interface DeviceRegistryPort {

    /**
     * @param deviceId the device a reading claims to come from
     * @return whether the device service knows it, linked to a home or not
     * @throws com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException
     *         when the device service does not answer and nothing was kept from an earlier answer
     */
    boolean exists(String deviceId);
}
