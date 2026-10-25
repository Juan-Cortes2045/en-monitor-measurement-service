package com.energymonitor.measurement.adapter.out.client;

import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the internal API of the device service: a device by id, with the home it is linked to,
 * and the devices of a home. Every call carries the shared token in {@code X-Internal-Token}.
 *
 * <p>No answer within the timeouts, or an error, is a {@link DeviceServiceUnavailableException};
 * nothing is retried here, so a caller waiting on it fails fast.
 */
public class DeviceServiceClient {

    private final RestClient devices;

    /**
     * @param devices client with the service's base URL, the timeouts and the token
     */
    public DeviceServiceClient(RestClient devices) {
        this.devices = devices;
    }

    /**
     * @param deviceId the device
     * @return the device with its home; empty when the service answers 404
     */
    public Optional<RemoteDevice> device(String deviceId) {
        try {
            return Optional.ofNullable(devices.get()
                    .uri("/internal/devices/{deviceId}", deviceId)
                    .retrieve()
                    .body(RemoteDevice.class));
        } catch (HttpClientErrorException.NotFound unknown) {
            return Optional.empty();
        } catch (RestClientException failure) {
            throw unavailable("device", failure);
        }
    }

    /**
     * @param homeId the home
     * @return the devices linked to it, empty when it has none
     */
    public List<RemoteDevice> devicesOfHome(String homeId) {
        try {
            RemoteDevice[] body = devices.get()
                    .uri("/internal/homes/{homeId}/devices", homeId)
                    .retrieve()
                    .body(RemoteDevice[].class);
            return body == null ? List.of() : List.of(body);
        } catch (RestClientException failure) {
            throw unavailable("devices of a home", failure);
        }
    }

    private static DeviceServiceUnavailableException unavailable(String what, RestClientException failure) {
        String status = failure instanceof RestClientResponseException response
                ? " (" + response.getStatusCode().value() + ")" : "";
        return new DeviceServiceUnavailableException(
                "device service did not answer the " + what + " request" + status, failure);
    }

    /**
     * What this service reads of a device; the rest of the answer is ignored.
     *
     * @param homeId the home it is linked to, or null when it is linked to none
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteDevice(String idDevice, String homeId) {
    }
}
