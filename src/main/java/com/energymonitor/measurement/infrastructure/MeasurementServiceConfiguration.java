package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.application.port.out.ConsumptionLevelPersistencePort;
import com.energymonitor.measurement.application.port.out.HomeAccessPort;
import com.energymonitor.measurement.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.measurement.application.port.out.MeasurementEventPort;
import com.energymonitor.measurement.application.port.out.MeasurementPersistencePort;
import com.energymonitor.measurement.application.usecase.HomeConsumptionUseCase;
import com.energymonitor.measurement.application.usecase.RegisterMeasurementService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Configuration for the Monitoring and Measurements module.
 *
 * <p>Registers the application use cases as Spring beans without annotating them, so the
 * application layer stays framework-free. Recording a reading is the exception: it is wired below,
 * wrapped in its transaction.
 */
@Configuration
@ComponentScan(
    basePackages = "com.energymonitor.measurement.application.usecase",
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*Service"),
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = RegisterMeasurementService.class))
public class MeasurementServiceConfiguration {

    /**
     * Recording a reading, wrapped so every reading is one transaction: the measurement row and
     * its outbox message commit together.
     */
    @Bean
    public TransactionalRegisterMeasurement registerMeasurement(
            MeasurementPersistencePort measurements,
            IdentifierGeneratorPort identifiers,
            MeasurementEventPort events,
            Clock clock,
            PlatformTransactionManager transactions) {
        return new TransactionalRegisterMeasurement(
                new RegisterMeasurementService(measurements, identifiers, events, clock),
                new TransactionTemplate(transactions));
    }

    /**
     * A reading older than {@code measurement.current-power-freshness} no longer counts as current
     * power: the same threshold after which the device service marks a device offline.
     */
    @Bean
    public HomeConsumptionUseCase homeConsumptionUseCase(
            MeasurementPersistencePort measurements,
            ConsumptionLevelPersistencePort levels,
            HomeAccessPort homes,
            Clock clock,
            @Value("${measurement.current-power-freshness:PT150S}") Duration freshness) {
        return new HomeConsumptionUseCase(measurements, levels, homes, clock, freshness);
    }
}
