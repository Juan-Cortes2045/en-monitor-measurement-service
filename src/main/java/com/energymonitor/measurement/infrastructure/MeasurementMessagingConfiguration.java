package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.adapter.out.event.MeasurementEventAdapter;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementOutboxPublisher;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementOutboxPurge;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementOutboxRelay;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementOutboxWriter;
import com.energymonitor.measurement.adapter.out.messaging.RabbitMeasurementOutboxPublisher;
import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import com.energymonitor.measurement.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.measurement.application.port.out.MeasurementEventPort;
import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The outbox of the readings and its broker side: the writer the event adapter uses, the
 * exchange this service publishes to, the relay and the purge of delivered messages.
 *
 * <p>Only the exchange is declared here. The queues belong to the consumers, which declare and
 * bind their own; until one exists for a message type, {@link RabbitMeasurementOutboxPublisher}
 * gets the message back and the relay keeps it pending.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MeasurementMessagingProperties.class)
public class MeasurementMessagingConfiguration {

    @Bean
    public MeasurementOutboxWriter measurementOutboxWriter(MeasurementOutboxRepository outbox,
                                                           IdentifierGeneratorPort identifiers, ObjectMapper json,
                                                           Clock clock, MeasurementMessagingProperties properties) {
        return new MeasurementOutboxWriter(outbox, identifiers, json, clock, properties.discardWritten());
    }

    @Bean
    public MeasurementEventPort measurementEvents(MeasurementOutboxWriter outbox) {
        return new MeasurementEventAdapter(outbox);
    }

    @Bean
    public TopicExchange measurementExchange(MeasurementMessagingProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /** A template of its own, mandatory, so a message no queue takes comes back. */
    @Bean
    public MeasurementOutboxPublisher measurementOutboxPublisher(ConnectionFactory connections,
                                                                 MeasurementMessagingProperties properties) {
        return new RabbitMeasurementOutboxPublisher(new RabbitTemplate(connections), properties.exchange(),
                properties.confirmTimeout());
    }

    @Bean
    public MeasurementOutboxRelay measurementOutboxRelay(MeasurementOutboxRepository outbox,
                                                         MeasurementOutboxPublisher publisher,
                                                         MeasurementMessagingProperties properties, Clock clock) {
        return new MeasurementOutboxRelay(outbox, publisher, clock, properties.batchSize());
    }

    @Bean
    public MeasurementOutboxPurge measurementOutboxPurge(MeasurementOutboxRepository outbox,
                                                         PlatformTransactionManager transactions,
                                                         MeasurementMessagingProperties properties, Clock clock) {
        return new MeasurementOutboxPurge(outbox, new TransactionTemplate(transactions), clock,
                properties.retention());
    }

    /**
     * Runs the relay and the purge on a fixed delay, so a slow run never overlaps the next one.
     * Both stay off with the relay, so the test profile neither publishes nor deletes.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "messaging.relay-enabled", havingValue = "true", matchIfMissing = true)
    static class RelaySchedule implements SchedulingConfigurer {

        private final MeasurementOutboxRelay relay;
        private final MeasurementOutboxPurge purge;
        private final MeasurementMessagingProperties properties;

        RelaySchedule(MeasurementOutboxRelay relay, MeasurementOutboxPurge purge,
                      MeasurementMessagingProperties properties) {
            this.relay = relay;
            this.purge = purge;
            this.properties = properties;
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(relay::relay, properties.pollInterval());
            registrar.addFixedDelayTask(purge::purge, properties.purgeInterval());
        }
    }
}
