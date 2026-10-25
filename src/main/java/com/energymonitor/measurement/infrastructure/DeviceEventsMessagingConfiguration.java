package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.adapter.in.messaging.DeviceLinksConsumer;
import com.energymonitor.measurement.adapter.out.client.DeviceChanges;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * The queue through which this service hears the device service: links and unlinks, for the
 * cache of devices.
 *
 * <p>Durable, bound to {@code energy-monitor.device} with {@code device.linked} and
 * {@code device.unlinked}, and dead-lettered to {@value #DEVICE_EVENTS_QUEUE}{@code .dlq} once a
 * message has failed every retry. The connectivity events are not bound: energy-monitor-backend
 * raises the alerts from them.
 */
@Configuration(proxyBeanMethods = false)
public class DeviceEventsMessagingConfiguration {

    public static final String DEVICE_EVENTS_QUEUE = "measurement-service.device.events";

    @Bean
    public Declarables deviceEventQueues(
            @Value("${messaging.device-exchange}") String deviceExchange,
            @Value("${messaging.dead-letter-exchange}") String deadLetterExchange) {
        return topology(deviceExchange, deadLetterExchange, DEVICE_EVENTS_QUEUE);
    }

    /**
     * The exchanges, the queue with its dead-letter queue and their bindings. The queue name is a
     * parameter so a test against a real broker declares a queue of its own: declaring the
     * service's queue with other arguments would leave it unusable for the service.
     */
    static Declarables topology(String deviceExchange, String deadLetterExchange, String queue) {
        TopicExchange device = new TopicExchange(deviceExchange, true, false);
        DirectExchange deadLetters = new DirectExchange(deadLetterExchange, true, false);
        Queue events = QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(queue)
                .build();
        Queue dead = QueueBuilder.durable(queue + ".dlq").build();
        return new Declarables(device, deadLetters, events, dead,
                BindingBuilder.bind(events).to(device).with("device.linked"),
                BindingBuilder.bind(events).to(device).with("device.unlinked"),
                BindingBuilder.bind(dead).to(deadLetters).with(queue));
    }

    /** Forgetting goes to the cache of devices, {@code CachedDeviceDirectory}. */
    @Bean
    public DeviceLinksConsumer deviceLinksConsumer(DeviceChanges changes, ObjectMapper json) {
        return new DeviceLinksConsumer(changes, json);
    }
}
