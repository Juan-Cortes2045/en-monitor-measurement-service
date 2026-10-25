package com.energymonitor.measurement.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the calls from this service to energy-monitor-backend and to the device service
 * ({@code internal-api.*}).
 *
 * @param token            the secret the services share; sent as {@code X-Internal-Token} on
 *                         every internal call
 * @param backendUrl       where energy-monitor-backend answers its internal endpoints
 * @param deviceServiceUrl where the device service answers its internal endpoints
 * @param connectTimeout   how long to wait for the connection to either of them
 * @param readTimeout      how long to wait for their answer
 * @param deviceTtl        how long a device, or the devices of a home, are reused; a device
 *                         linked or unlinked shows here within this time
 * @param deviceStaleTtl   how long the last answer of the device service is kept to serve while it
 *                         does not answer
 */
@ConfigurationProperties("internal-api")
public record InternalApiProperties(
        String token,
        @DefaultValue("http://localhost:8083") URI backendUrl,
        @DefaultValue("http://localhost:8084") URI deviceServiceUrl,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue("PT30S") Duration deviceTtl,
        @DefaultValue("PT1H") Duration deviceStaleTtl) {

    /** The header carrying {@link #token()}. */
    public static final String TOKEN_HEADER = "X-Internal-Token";
}
