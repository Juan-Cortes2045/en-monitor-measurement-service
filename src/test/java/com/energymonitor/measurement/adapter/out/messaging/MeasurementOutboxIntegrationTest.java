package com.energymonitor.measurement.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.measurement.adapter.out.persistence.entity.MeasurementOutboxEntity;
import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.result.MeasurementResult;
import com.energymonitor.measurement.infrastructure.JwtKeyedTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every reading writes its {@code measurement.recorded} message, against the real schema.
 * {@code @Transactional}: everything joins the test's transaction, which is rolled back, so no row
 * is left. The test profile also stores the messages already discarded.
 */
@SpringBootTest
@Transactional
@DisplayName("Measurement outbox")
class MeasurementOutboxIntegrationTest extends JwtKeyedTest {

    private static final String DEVICE = "mot0000001";
    private static final Instant TAKEN = Instant.parse("2026-10-24T20:00:00Z");

    @Autowired
    private RegisterMeasurement registerMeasurement;
    @Autowired
    private MeasurementOutboxRepository outbox;
    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("a reading writes measurement.recorded with the reading, at the time it was taken")
    void readingWritesTheMessage() {
        MeasurementResult stored = registerMeasurement.register(
                new RegisterMeasurementCommand(DEVICE, TAKEN, 231.4, 1.2, 285.5, 12.8));

        List<MeasurementOutboxEntity> messages = messagesFor(DEVICE);
        assertThat(messages).extracting(MeasurementOutboxEntity::getMessageType)
                .containsExactly(MeasurementMessageTypes.MEASUREMENT_RECORDED);
        MeasurementOutboxEntity message = messages.getFirst();
        assertThat(message.getOccurredAt()).isEqualTo(TAKEN);
        assertThat(message.getDiscardedAt()).as("the test profile never publishes").isNotNull();

        JsonNode payload = json.readTree(message.getPayload());
        assertThat(payload.get("idMeasurement").asString()).isEqualTo(stored.idMeasurement());
        assertThat(payload.get("deviceId").asString()).isEqualTo(DEVICE);
        assertThat(payload.get("dateTime").asString()).isEqualTo("2026-10-24T20:00:00Z");
        assertThat(payload.get("activePower").asDouble()).isEqualTo(285.5);
        assertThat(payload.get("storedEnergy").asDouble()).isEqualTo(12.8);
    }

    @Test
    @DisplayName("each reading has a message of its own, with an id of its own")
    void oneMessagePerReading() {
        registerMeasurement.register(new RegisterMeasurementCommand(DEVICE, TAKEN, 231.4, 1.2, 285.5, 12.8));
        registerMeasurement.register(new RegisterMeasurementCommand(DEVICE, TAKEN.plusSeconds(60),
                231.0, 1.1, 280.0, 12.9));

        assertThat(messagesFor(DEVICE)).hasSize(2)
                .extracting(MeasurementOutboxEntity::getIdMessage).doesNotHaveDuplicates();
    }

    private List<MeasurementOutboxEntity> messagesFor(String deviceId) {
        return outbox.findAll().stream()
                .filter(message -> message.getPayload().contains("\"" + deviceId + "\""))
                .toList();
    }
}
