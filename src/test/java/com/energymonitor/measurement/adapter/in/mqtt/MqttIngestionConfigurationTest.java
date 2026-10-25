package com.energymonitor.measurement.adapter.in.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.energymonitor.measurement.adapter.out.client.CachedDeviceDirectory;
import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.infrastructure.JwtKeyedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * With the subscription turned on it wires up, and the service starts even with no broker to
 * reach: the adapter keeps retrying in the background instead of failing the start. Nothing
 * listens on port 9, so no running broker or service is ever disturbed.
 */
@SpringBootTest(properties = {
        "mqtt.inbound.enabled=true",
        "mqtt.broker-url=tcp://127.0.0.1:9",
        "mqtt.connection-timeout=1"
})
@DisplayName("MQTT ingestion wiring")
class MqttIngestionConfigurationTest extends JwtKeyedTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private CachedDeviceDirectory devices;

    @MockitoBean
    private RegisterMeasurement registerMeasurement;

    @Test
    @DisplayName("one subscription to the telemetry, with a client id of its own, and the handler")
    void wiresTheSubscription() {
        var adapters = context.getBeansOfType(MqttPahoMessageDrivenChannelAdapter.class).values();

        assertThat(adapters).hasSize(1);
        MqttPahoMessageDrivenChannelAdapter adapter = adapters.iterator().next();
        assertThat(adapter.getTopic()).containsExactly("energy-monitor/devices/+/telemetry");
        assertThat((String) ReflectionTestUtils.invokeMethod(adapter, "getClientId"))
                .isEqualTo("measurement-service-test-telemetry");
        assertThat(context.getBean(TelemetryMqttConsumer.class)).isNotNull();
    }

    @Test
    @DisplayName("the subscription hands its messages to the handler, which stores the reading")
    void deliversToTheHandler() {
        given(devices.exists("DEV0000001")).willReturn(true);
        MqttPahoMessageDrivenChannelAdapter adapter =
                context.getBeansOfType(MqttPahoMessageDrivenChannelAdapter.class).values().iterator().next();

        // What Paho receives goes to the adapter's output channel; a channel nobody subscribed
        // to would throw here, and Paho drops the connection on that.
        adapter.getOutputChannel().send(MessageBuilder
                .withPayload("{\"dateTime\":\"2026-10-24T20:00:00Z\",\"voltage\":231.4,\"current\":1.2,"
                        + "\"activePower\":285.5,\"storedEnergy\":12.8}")
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "energy-monitor/devices/DEV0000001/telemetry")
                .build());

        ArgumentCaptor<RegisterMeasurementCommand> stored = ArgumentCaptor.forClass(RegisterMeasurementCommand.class);
        verify(registerMeasurement).register(stored.capture());
        assertThat(stored.getValue().deviceId()).isEqualTo("DEV0000001");
        assertThat(stored.getValue().activePower()).isEqualTo(285.5);
    }
}
