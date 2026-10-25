package com.energymonitor.measurement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

@DisplayName("Device events queue")
class DeviceEventsMessagingConfigurationTest {

    private final Declarables declarables = new DeviceEventsMessagingConfiguration()
            .deviceEventQueues("energy-monitor.device", "measurement-service.dlx");

    @Test
    @DisplayName("the durable queue takes only the links and unlinks from the device exchange")
    void bindsLinksOnly() {
        assertThat(declarables.getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getDestination().equals(DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE)))
                .allMatch(b -> b.getExchange().equals("energy-monitor.device"))
                .extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrder("device.linked", "device.unlinked");
    }

    @Test
    @DisplayName("a message that keeps failing goes to its dead-letter queue")
    void deadLetters() {
        Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(q -> q.getName().equals(DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE))
                .findFirst().orElseThrow();

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "measurement-service.dlx")
                .containsEntry("x-dead-letter-routing-key", DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE);
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .anyMatch(b -> b.getDestination().equals(DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE + ".dlq")
                        && b.getExchange().equals("measurement-service.dlx"));
    }
}
