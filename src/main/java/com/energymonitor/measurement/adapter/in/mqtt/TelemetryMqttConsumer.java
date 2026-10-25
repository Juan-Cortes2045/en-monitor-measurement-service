package com.energymonitor.measurement.adapter.in.mqtt;

import com.energymonitor.measurement.adapter.in.mqtt.dto.TelemetryMessage;
import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.port.out.DeviceRegistryPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns every message on {@code energy-monitor/devices/+/telemetry} into a measurement.
 *
 * <p>The measurement keeps the device timestamp, so samples replayed from the offline queue land
 * at the time they were taken. The device service subscribes to the same topic and keeps the
 * connectivity itself.
 *
 * <p>Whether the device exists is asked to the device service, cached; while it does not answer
 * and nothing was kept, the reading is dropped and logged. A message that cannot be trusted or
 * parsed is dropped too; the subscription goes on.
 */
public class TelemetryMqttConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttConsumer.class);

    private final ObjectMapper objectMapper;
    private final DeviceRegistryPort devices;
    private final RegisterMeasurement registerMeasurement;

    public TelemetryMqttConsumer(ObjectMapper objectMapper, DeviceRegistryPort devices,
                                 RegisterMeasurement registerMeasurement) {
        this.objectMapper = objectMapper;
        this.devices = devices;
        this.registerMeasurement = registerMeasurement;
    }

    @ServiceActivator(inputChannel = MqttIngestionConfiguration.TELEMETRY_CHANNEL)
    public void handleTelemetry(Message<?> message) {
        Optional<String> topicDevice = MqttTopics.deviceId(message);
        if (topicDevice.isEmpty()) {
            log.warn("Ignoring telemetry on unexpected topic");
            return;
        }
        String deviceId = topicDevice.get();
        try {
            TelemetryMessage telemetry = objectMapper.readValue(message.getPayload().toString(), TelemetryMessage.class);
            if (telemetry.deviceId() != null && !telemetry.deviceId().equals(deviceId)) {
                log.warn("Ignoring telemetry on the topic of {} that claims to be {}", deviceId, telemetry.deviceId());
                return;
            }
            if (!devices.exists(deviceId)) {
                log.warn("Ignoring telemetry of unknown device {}", deviceId);
                return;
            }
            registerMeasurement.register(new RegisterMeasurementCommand(
                    deviceId,
                    telemetry.dateTime(),
                    telemetry.voltage(),
                    telemetry.current(),
                    telemetry.activePower(),
                    telemetry.storedEnergy()));
        } catch (JacksonException e) {
            log.warn("Failed to parse telemetry of device {}: {}", deviceId, e.getOriginalMessage());
        } catch (RuntimeException e) {
            log.warn("Failed to process telemetry of device {}: {}", deviceId, e.getMessage());
        }
    }
}
