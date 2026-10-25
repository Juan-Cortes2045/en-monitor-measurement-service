package com.energymonitor.measurement.adapter.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.integration.core.MessageProducer;

/**
 * While energy-monitor-backend stores the readings this service must not: the subscription only
 * exists when it is turned on explicitly.
 */
@DisplayName("MQTT ingestion off by default")
class MqttIngestionOffByDefaultTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MqttIngestionConfiguration.class);

    @Test
    @DisplayName("without mqtt.inbound.enabled there is no subscription")
    void offWithoutTheProperty() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MessageProducer.class)
                .doesNotHaveBean(TelemetryMqttConsumer.class));
    }

    @Test
    @DisplayName("mqtt.inbound.enabled=false keeps it off")
    void offWhenFalse() {
        runner.withPropertyValues("mqtt.inbound.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MessageProducer.class));
    }
}
