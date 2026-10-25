package com.energymonitor.measurement.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.energymonitor.measurement.adapter.out.client.DeviceChanges;
import com.energymonitor.measurement.infrastructure.DeviceEventsMessagingConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The queue of this service against a real RabbitMQ: the links and unlinks the device service
 * publishes reach it and the consumer forgets the device, and the connectivity events do not.
 *
 * <p>The topology is declared with exchanges of its own, deleted afterwards, so nothing the
 * device service publishes is touched. Needs a broker on {@code RABBITMQ_HOST:RABBITMQ_PORT}
 * (localhost:5672 by default) with the credentials of {@code RABBITMQ_USERNAME} and
 * {@code RABBITMQ_PASSWORD}; without one it is skipped rather than failed. A measurement service
 * running against the same broker would compete for the messages, so stop it first.
 */
@DisplayName("Device links over RabbitMQ")
class DeviceLinksBrokerTest {

    private static final String HOST = env("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(env("RABBITMQ_PORT", "5672"));
    private static final String QUEUE = DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE;

    private CachingConnectionFactory connections;
    private RabbitAdmin admin;
    private RabbitTemplate rabbit;
    private String exchange;
    private String deadLetters;

    @BeforeEach
    void connect() {
        assumeTrue(reachable(), "no RabbitMQ on " + HOST + ":" + PORT);
        connections = new CachingConnectionFactory(HOST, PORT);
        connections.setUsername(env("RABBITMQ_USERNAME", "guest"));
        connections.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        admin = new RabbitAdmin(connections);
        exchange = "test.device." + UUID.randomUUID();
        deadLetters = "test.dlx." + UUID.randomUUID();
        Declarables topology = new DeviceEventsMessagingConfiguration().deviceEventQueues(exchange, deadLetters);
        topology.getDeclarables().forEach(declarable -> {
            switch (declarable) {
                case org.springframework.amqp.core.Exchange e -> admin.declareExchange(e);
                case org.springframework.amqp.core.Queue q -> admin.declareQueue(q);
                case org.springframework.amqp.core.Binding b -> admin.declareBinding(b);
                default -> { }
            }
        });
        rabbit = new RabbitTemplate(connections);
    }

    @AfterEach
    void disconnect() {
        if (admin != null) {
            admin.deleteExchange(exchange);
            admin.deleteExchange(deadLetters);
            connections.destroy();
        }
    }

    @Test
    @DisplayName("a link reaches the queue and forgets the device; a connectivity change does not reach it")
    void linkReachesTheConsumer() {
        DeviceChanges changes = mock(DeviceChanges.class);
        DeviceLinksConsumer consumer = new DeviceLinksConsumer(changes, JsonMapper.builder().build());

        send("device.connectivity-lost", "{\"deviceId\":\"DEV0000009\"}");
        send("device.linked", "{\"deviceId\":\"DEV0000001\",\"homeId\":\"hom0000001\"}");

        Message received = rabbit.receive(QUEUE, 5_000);
        assertThat(received).isNotNull();
        consumer.on(received);
        verify(changes).forget("DEV0000001", "hom0000001");
        assertThat(rabbit.receive(QUEUE, 500)).isNull();
    }

    private void send(String type, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setType(type);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(exchange, type, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 500);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
