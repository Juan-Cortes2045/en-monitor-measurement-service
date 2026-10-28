package com.energymonitor.measurement.adapter.out.client;

import com.energymonitor.measurement.application.port.out.HomeAccessPort;
import java.util.List;
import java.util.Optional;

/**
 * What the measurement use cases need to know about homes and devices, asked to the services that
 * own them: membership and limits to energy-monitor-backend, device links to the device service.
 */
public class RemoteHomeAccessAdapter implements HomeAccessPort {

    private final HomeServiceClient homes;
    private final CachedDeviceDirectory devices;

    public RemoteHomeAccessAdapter(HomeServiceClient homes, CachedDeviceDirectory devices) {
        this.homes = homes;
        this.devices = devices;
    }

    @Override
    public boolean isMember(String userId, String homeId) {
        return homes.isMember(userId, homeId);
    }

    @Override
    public List<String> deviceIds(String homeId) {
        return devices.deviceIds(homeId);
    }

    @Override
    public Optional<String> homeOf(String deviceId) {
        return devices.homeOf(deviceId);
    }

    @Override
    public Optional<Limits> limits(String homeId) {
        return homes.limits(homeId);
    }
}
