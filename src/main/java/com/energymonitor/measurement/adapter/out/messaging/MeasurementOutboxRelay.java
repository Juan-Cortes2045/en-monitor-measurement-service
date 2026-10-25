package com.energymonitor.measurement.adapter.out.messaging;

import com.energymonitor.measurement.adapter.out.persistence.entity.MeasurementOutboxEntity;
import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

/**
 * Moves the measurement outbox to the broker.
 *
 * <p>Each run takes the pending messages oldest first and publishes them one by one, waiting for
 * the broker's confirmation before marking a message as published. The first failure stops the
 * run, so a later message never overtakes an earlier one; the failed message is retried on the
 * next run, which is what makes delivery at-least-once. Consumers drop duplicates by message id.
 *
 * <p>Runs on a single instance of the service. A second instance would publish the same rows
 * twice, which consumers tolerate, but it would also break the ordering above.
 */
public class MeasurementOutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(MeasurementOutboxRelay.class);

    private static final int MAX_ERROR_LENGTH = 255;

    private final MeasurementOutboxRepository outbox;
    private final MeasurementOutboxPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public MeasurementOutboxRelay(MeasurementOutboxRepository outbox, MeasurementOutboxPublisher publisher, Clock clock,
                           int batchSize) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * Publishes what is pending.
     *
     * @return how many messages were published in this run
     */
    public int relay() {
        int published = 0;
        for (MeasurementOutboxEntity message : outbox.findPending(PageRequest.of(0, batchSize))) {
            try {
                publisher.publish(message.getIdMessage(), message.getMessageType(),
                        message.getPayload(), message.getOccurredAt());
            } catch (RuntimeException failure) {
                message.setAttempts(message.getAttempts() + 1);
                message.setLastError(truncate(failure.getMessage()));
                outbox.save(message);
                LOG.warn("Measurement outbox message {} ({}) not published, attempt {}: {}",
                        message.getIdMessage(), message.getMessageType(), message.getAttempts(),
                        failure.getMessage());
                break;
            }
            message.setPublishedAt(clock.instant());
            outbox.save(message);
            published++;
        }
        return published;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
