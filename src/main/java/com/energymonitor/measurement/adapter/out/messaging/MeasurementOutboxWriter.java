package com.energymonitor.measurement.adapter.out.messaging;

import com.energymonitor.measurement.adapter.out.persistence.entity.MeasurementOutboxEntity;
import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import com.energymonitor.measurement.application.port.out.IdentifierGeneratorPort;
import java.time.Clock;
import java.time.Instant;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a measurement message to the outbox.
 *
 * <p>Called from inside the use case's transaction, so the row is committed or rolled back
 * together with the reading it announces. Publishing to the broker is left to
 * {@link MeasurementOutboxRelay}.
 */
public class MeasurementOutboxWriter {

    private final MeasurementOutboxRepository outbox;
    private final IdentifierGeneratorPort identifiers;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean discardWritten;

    /**
     * @param discardWritten whether a message is stored already discarded; only the test profile
     *                       sets it, so the suite's messages are never published
     */
    public MeasurementOutboxWriter(MeasurementOutboxRepository outbox, IdentifierGeneratorPort identifiers,
                                   ObjectMapper json, Clock clock, boolean discardWritten) {
        this.outbox = outbox;
        this.identifiers = identifiers;
        this.json = json;
        this.clock = clock;
        this.discardWritten = discardWritten;
    }

    /**
     * @param type       the routing key, one of {@link MeasurementMessageTypes}
     * @param payload    the body, serialized as JSON
     * @param occurredAt when the reading was taken
     */
    public void write(String type, Object payload, Instant occurredAt) {
        MeasurementOutboxEntity message = new MeasurementOutboxEntity();
        message.setIdMessage(identifiers.generate());
        message.setMessageType(type);
        message.setPayload(json.writeValueAsString(payload));
        message.setOccurredAt(occurredAt);
        if (discardWritten) {
            message.setDiscardedAt(clock.instant());
        }
        outbox.save(message);
    }
}
