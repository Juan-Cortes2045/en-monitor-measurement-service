package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.adapter.out.client.BackendHomeClient;
import com.energymonitor.measurement.adapter.out.client.CachedDeviceDirectory;
import com.energymonitor.measurement.adapter.out.client.DeviceServiceClient;
import com.energymonitor.measurement.adapter.out.client.RemoteHomeAccessAdapter;
import com.energymonitor.measurement.application.port.out.HomeAccessPort;
import com.github.benmanes.caffeine.cache.Ticker;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The calls from this service to the services that own homes and devices: whether a caller
 * belongs to a home and its limits, to energy-monitor-backend; which home a device is linked to
 * and the devices of a home, to the device service, cached. Every call carries the shared token,
 * which each of them checks together with this service's network.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(InternalApiConfiguration.class);

    @Bean
    public BackendHomeClient backendHomeClient(InternalApiProperties properties) {
        warnWithoutToken(properties);
        return new BackendHomeClient(client(properties.backendUrl(), properties));
    }

    @Bean
    public CachedDeviceDirectory deviceDirectory(InternalApiProperties properties) {
        return new CachedDeviceDirectory(
                new DeviceServiceClient(client(properties.deviceServiceUrl(), properties)),
                properties.deviceTtl(), properties.deviceStaleTtl(), Ticker.systemTicker());
    }

    @Bean
    public HomeAccessPort homeAccess(BackendHomeClient homes, CachedDeviceDirectory devices) {
        return new RemoteHomeAccessAdapter(homes, devices);
    }

    private static RestClient client(URI baseUrl, InternalApiProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requests)
                .defaultHeader(InternalApiProperties.TOKEN_HEADER,
                        properties.token() == null ? "" : properties.token())
                .build();
    }

    private static void warnWithoutToken(InternalApiProperties properties) {
        if (properties.token() == null || properties.token().isBlank()) {
            LOG.warn("internal-api.token is empty: energy-monitor-backend and the device service "
                    + "will refuse every call, so no home or device can be read");
        }
    }
}
