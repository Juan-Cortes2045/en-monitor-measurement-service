package com.energymonitor.measurement.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the {@link Clock} bean.
 *
 * <p>The {@code Clock} is injected into application services to make them testable
 * and to avoid direct calls to {@code Instant.now()}.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
