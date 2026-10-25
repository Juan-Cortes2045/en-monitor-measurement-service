package com.energymonitor.measurement.adapter.out.client;

import com.energymonitor.measurement.adapter.out.client.DeviceServiceClient.RemoteDevice;
import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The devices of the device service as this service reads them: the home a device is linked to,
 * and the devices of a home.
 *
 * <p>Answers are reused for a short while, a device that does not exist included, so a dashboard
 * refreshing every few seconds costs one request per {@code ttl}, not one per refresh. A device
 * linked or unlinked in the service shows here at once, when its event arrives and
 * {@link #forget} is called, or at the latest once its entry expires.
 *
 * <p>The last answer is also kept for {@code staleTtl}: when the service does not answer, that is
 * what is served, so a short outage of the device service does not stop the readings of devices
 * already known. Only with nothing kept does a call throw {@link DeviceServiceUnavailableException}.
 */
public class CachedDeviceDirectory implements DeviceChanges {

    private final DeviceServiceClient client;
    private final Cache<String, Optional<RemoteDevice>> devices;
    private final Cache<String, Optional<RemoteDevice>> lastKnownDevices;
    private final Cache<String, List<RemoteDevice>> homes;
    private final Cache<String, List<RemoteDevice>> lastKnownHomes;

    /**
     * @param client   the device service
     * @param ttl      how long an answer is reused before asking again
     * @param staleTtl how long the last answer is kept to serve while the service does not answer
     * @param ticker   the clock of the caches
     */
    public CachedDeviceDirectory(DeviceServiceClient client, Duration ttl, Duration staleTtl, Ticker ticker) {
        this.client = client;
        this.devices = cache(ttl, ticker);
        this.lastKnownDevices = cache(staleTtl, ticker);
        this.homes = cache(ttl, ticker);
        this.lastKnownHomes = cache(staleTtl, ticker);
    }

    private static <V> Cache<String, V> cache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).ticker(ticker).build();
    }

    /**
     * @param deviceId the device
     * @return the home it is linked to, empty when it does not exist or is linked to none
     * @throws DeviceServiceUnavailableException when the service does not answer and nothing was
     *                                           kept from an earlier answer
     */
    public Optional<String> homeOf(String deviceId) {
        if (deviceId == null) {
            return Optional.empty();
        }
        return fresh(devices, lastKnownDevices, deviceId, client::device).map(RemoteDevice::homeId);
    }

    /**
     * @param homeId the home
     * @return the identifiers of the devices linked to it
     * @throws DeviceServiceUnavailableException as {@link #homeOf}
     */
    public List<String> deviceIds(String homeId) {
        if (homeId == null) {
            return List.of();
        }
        return fresh(homes, lastKnownHomes, homeId, client::devicesOfHome).stream()
                .map(RemoteDevice::idDevice)
                .toList();
    }

    /**
     * Drops what is known of a device and of a home, so the next call asks the service again.
     * Called when the device service announces a link or an unlink.
     *
     * @param deviceId the device that changed, may be null
     * @param homeId   the home it joined or left, may be null
     */
    @Override
    public void forget(String deviceId, String homeId) {
        if (deviceId != null) {
            devices.invalidate(deviceId);
        }
        if (homeId != null) {
            homes.invalidate(homeId);
        }
    }

    /**
     * The cached answer, or a new one, which is also kept as the last known; when the service does
     * not answer, the last known one if there is any.
     */
    private static <V> V fresh(Cache<String, V> cache, Cache<String, V> lastKnown, String key,
                               Function<String, V> load) {
        V cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            V loaded = load.apply(key);
            cache.put(key, loaded);
            lastKnown.put(key, loaded);
            return loaded;
        } catch (DeviceServiceUnavailableException unavailable) {
            V stale = lastKnown.getIfPresent(key);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
