package com.energymonitor.measurement.adapter.out.client;

import com.energymonitor.measurement.application.exception.HomeServiceUnavailableException;
import com.energymonitor.measurement.application.port.out.HomeAccessPort.Limits;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks energy-monitor-backend, owner of the homes, through its internal API:
 *
 * <ul>
 *   <li>{@code GET /internal/homes/{homeId}/access?userId=...}, always 200 with
 *       {@code {"exists":..,"member":..,"owner":..}};</li>
 *   <li>{@code GET /internal/homes/{homeId}/thresholds}, 200 with
 *       {@code {"dailyLimit":..,"monthlyLimit":..,"limitPeriod":..}} or 404 when the home has
 *       none.</li>
 * </ul>
 *
 * <p>Anything else, or no answer within the timeouts, is a
 * {@link HomeServiceUnavailableException}: whether a caller belongs to a home is never guessed.
 * Nothing is cached, so a member removed from a home loses access at once.
 */
public class BackendHomeClient {

    private final RestClient backend;

    /**
     * @param backend client with the backend's base URL, the timeouts and the internal token
     */
    public BackendHomeClient(RestClient backend) {
        this.backend = backend;
    }

    /**
     * @return whether the home exists and the caller is an active member of it
     */
    public boolean isMember(String userId, String homeId) {
        if (userId == null || homeId == null) {
            return false;
        }
        try {
            return backend.get()
                    .uri("/internal/homes/{homeId}/access?userId={userId}", homeId, userId)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                            throw new HomeServiceUnavailableException(
                                    "home access answered " + response.getStatusCode().value());
                        }
                        Access access = response.bodyTo(Access.class);
                        if (access == null) {
                            throw new HomeServiceUnavailableException("home access answered no body");
                        }
                        return access.exists() && access.member();
                    });
        } catch (RestClientException unreachable) {
            throw new HomeServiceUnavailableException("home access failed", unreachable);
        }
    }

    /**
     * @return the consumption limits of the home, empty when it has none
     */
    public Optional<Limits> limits(String homeId) {
        if (homeId == null) {
            return Optional.empty();
        }
        try {
            return backend.get()
                    .uri("/internal/homes/{homeId}/thresholds", homeId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return Optional.<Limits>empty();
                        }
                        if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                            throw new HomeServiceUnavailableException(
                                    "home thresholds answered " + response.getStatusCode().value());
                        }
                        Thresholds thresholds = response.bodyTo(Thresholds.class);
                        if (thresholds == null) {
                            throw new HomeServiceUnavailableException("home thresholds answered no body");
                        }
                        return Optional.of(new Limits(thresholds.dailyLimit(), thresholds.monthlyLimit(),
                                thresholds.limitPeriod()));
                    });
        } catch (RestClientException unreachable) {
            throw new HomeServiceUnavailableException("home thresholds failed", unreachable);
        }
    }

    /** The body of the access answer. */
    record Access(boolean exists, boolean member, boolean owner) {
    }

    /** The body of the thresholds answer. */
    record Thresholds(double dailyLimit, double monthlyLimit, String limitPeriod) {
    }
}
