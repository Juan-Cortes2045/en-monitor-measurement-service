package com.energymonitor.measurement.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the calls between this service and the other services ({@code internal-api.*}):
 * outbound to energy-monitor-backend and the device service, inbound from energy-monitor-backend.
 *
 * @param token            the secret the services share; sent as {@code X-Internal-Token} on
 *                         every internal call, and required on every inbound one. Empty refuses
 *                         every inbound internal call
 * @param allowedNetworks  the networks an inbound internal call may come from: loopback and the
 *                         private ranges Docker uses
 * @param homeServiceUrl   where the home service answers its internal endpoints
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
        @DefaultValue({"127.0.0.1/32", "::1/128", "172.16.0.0/12"}) List<String> allowedNetworks,
        @DefaultValue("http://localhost:8086") URI homeServiceUrl,
        @DefaultValue("http://localhost:8084") URI deviceServiceUrl,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue("PT30S") Duration deviceTtl,
        @DefaultValue("PT1H") Duration deviceStaleTtl) {

    /** The header carrying {@link #token()}. */
    public static final String TOKEN_HEADER = "X-Internal-Token";
}
