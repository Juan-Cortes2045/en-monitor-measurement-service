package com.energymonitor.measurement.adapter.in.mqtt;

import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.port.out.DeviceRegistryPort;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import tools.jackson.databind.ObjectMapper;

/**
 * MQTT inbound wiring of the readings: the telemetry subscription, its channel and its handler.
 *
 * <p><strong>Off unless {@code mqtt.inbound.enabled=true}</strong> ({@code MEASUREMENT_MQTT_ENABLED}).
 * While energy-monitor-backend still stores the readings, both subscribed would store every
 * reading twice in the shared table; the subscription is turned on when the backend stops
 * subscribing. The test profile leaves it off, so a test never takes the session of a running
 * service.
 *
 * <p>The client id gets a {@code -telemetry} suffix of this service's {@code mqtt.client-id}. The
 * broker disconnects a client when another one connects with the same id, so it must differ from
 * the device service's and the backend's, which subscribe to the same topic.
 *
 * <p>{@link EnableIntegration} is explicit because the project depends on
 * {@code spring-integration-mqtt} without Spring Boot's integration starter.
 */
@Configuration(proxyBeanMethods = false)
@EnableIntegration
@ConditionalOnProperty(name = "mqtt.inbound.enabled", havingValue = "true")
public class MqttIngestionConfiguration {

    static final String TELEMETRY_CHANNEL = "mqttTelemetryChannel";

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:measurement-service}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.topics.telemetry:energy-monitor/devices/+/telemetry}")
    private String telemetryTopic;

    @Value("${mqtt.qos:0}")
    private int qos;

    @Value("${mqtt.keep-alive-interval:60}")
    private int keepAliveInterval;

    @Value("${mqtt.connection-timeout:30}")
    private int connectionTimeout;

    @Value("${mqtt.clean-session:true}")
    private boolean cleanSession;

    /**
     * How long a subscribe, unsubscribe or disconnect may wait for the broker, and how long a
     * stopping adapter lets in-flight work finish. Paho's defaults are 30 s each, so a service
     * stopping while the broker is down would hang for a minute.
     */
    @Value("${mqtt.completion-timeout:PT5S}")
    private Duration completionTimeout;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }
        options.setKeepAliveInterval(keepAliveInterval);
        options.setConnectionTimeout(connectionTimeout);
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(name = TELEMETRY_CHANNEL)
    public MessageChannel mqttTelemetryChannel() {
        return new DirectChannel();
    }

    // The channel is injected, not obtained by calling its @Bean method: without bean method
    // proxies such a call returns a new, unnamed channel with no subscriber, and every message
    // would fail to be delivered (and Paho would drop the connection on it).
    @Bean
    public MessageProducer inboundTelemetry(MqttPahoClientFactory factory,
                                            @Qualifier(TELEMETRY_CHANNEL) MessageChannel channel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-telemetry", factory, telemetryTopic);
        adapter.setQos(qos);
        adapter.setCompletionTimeout(completionTimeout.toMillis());
        adapter.setDisconnectCompletionTimeout(completionTimeout.toMillis());
        adapter.setQuiescentTimeout(completionTimeout.toMillis());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setOutputChannel(channel);
        return adapter;
    }

    @Bean
    public TelemetryMqttConsumer telemetryMqttConsumer(ObjectMapper objectMapper, DeviceRegistryPort devices,
                                                       RegisterMeasurement registerMeasurement) {
        return new TelemetryMqttConsumer(objectMapper, devices, registerMeasurement);
    }
}
