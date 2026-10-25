package com.energymonitor.measurement.adapter.in.messaging;

import com.energymonitor.measurement.adapter.out.client.DeviceChanges;
import com.energymonitor.measurement.infrastructure.DeviceEventsMessagingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Hears {@code device.linked} and {@code device.unlinked} on {@code energy-monitor.device} and
 * drops what this service keeps of the device and of its home. A device just linked therefore
 * shows in the consumption of its home at once, and one just unlinked stops being readable by
 * the members of the home it left, instead of half a minute later.
 *
 * <p>A message may arrive twice: forgetting twice only costs one more request. An unknown type,
 * or a message without a device, is dropped.
 */
public class DeviceLinksConsumer {

    static final String DEVICE_LINKED = "device.linked";
    static final String DEVICE_UNLINKED = "device.unlinked";

    private static final Logger LOG = LoggerFactory.getLogger(DeviceLinksConsumer.class);

    private final DeviceChanges changes;
    private final ObjectMapper json;

    public DeviceLinksConsumer(DeviceChanges changes, ObjectMapper json) {
        this.changes = changes;
        this.json = json;
    }

    @RabbitListener(queues = DeviceEventsMessagingConfiguration.DEVICE_EVENTS_QUEUE)
    public void on(Message message) {
        String type = typeOf(message);
        if (!DEVICE_LINKED.equals(type) && !DEVICE_UNLINKED.equals(type)) {
            LOG.warn("Ignoring device message of type '{}'", type);
            return;
        }
        DeviceMessage body = json.readValue(message.getBody(), DeviceMessage.class);
        if (body.deviceId() == null || body.deviceId().isBlank()) {
            LOG.warn("Ignoring {} {} without a deviceId", type, message.getMessageProperties().getMessageId());
            return;
        }
        changes.forget(body.deviceId(), body.homeId());
    }

    /** The routing key it was published with, which the device service also sets as the type. */
    private static String typeOf(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        return routingKey != null ? routingKey : message.getMessageProperties().getType();
    }

    /** The fields of a link or an unlink this service reads; the rest is ignored. */
    record DeviceMessage(String deviceId, String homeId) {
    }
}
