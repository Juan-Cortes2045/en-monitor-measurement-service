package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.adapter.out.client.HomeServiceClient;
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
import org.springframework.core.annotation.Order;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/**
 * The calls between this service and the other services of the platform.
 *
 * <p>Inbound, {@code /internal/measurements/**} and {@code /internal/consumption-levels/**} get
 * their own filter chain, evaluated before the API chain: energy-monitor-backend presents no JWT,
 * so it is trusted by network and shared token instead (see {@link InternalCallerCheck}). Anything
 * else is answered 403. The reverse proxy and the gateway must not forward {@code /internal/**} at
 * all, since behind them every request seems to come from the proxy.
 *
 * <p>Outbound, to the services that own homes and devices: whether a caller belongs to a home and
 * its limits, to energy-monitor-backend; which home a device is linked to and the devices of a
 * home, to the device service, cached. Every call carries the shared token, which each of them
 * checks together with this service's network.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(InternalApiConfiguration.class);

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http, InternalApiProperties properties)
            throws Exception {
        InternalCallerCheck callers = new InternalCallerCheck(properties);
        return http
                .securityMatcher("/internal/measurements/**", "/internal/consumption-levels/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .anyRequest().access((authentication, context) ->
                                new AuthorizationDecision(callers.trusts(context.getRequest()))))
                .build();
    }

    @Bean
    public HomeServiceClient homeServiceClient(InternalApiProperties properties) {
        warnWithoutToken(properties);
        return new HomeServiceClient(client(properties.homeServiceUrl(), properties));
    }

    @Bean
    public CachedDeviceDirectory deviceDirectory(InternalApiProperties properties) {
        return new CachedDeviceDirectory(
                new DeviceServiceClient(client(properties.deviceServiceUrl(), properties)),
                properties.deviceTtl(), properties.deviceStaleTtl(), Ticker.systemTicker());
    }

    @Bean
    public HomeAccessPort homeAccess(HomeServiceClient homes, CachedDeviceDirectory devices) {
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
            LOG.warn("internal-api.token is empty: every internal call is refused, both ways, so no "
                    + "home or device can be read and the backend cannot read the readings");
        }
    }
}
