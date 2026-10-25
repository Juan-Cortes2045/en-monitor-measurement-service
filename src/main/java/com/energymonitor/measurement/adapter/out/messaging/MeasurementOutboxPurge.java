package com.energymonitor.measurement.adapter.out.messaging;

import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes the outbox messages already published, or discarded, once they are older than the
 * retention. Every reading writes one message, so without it the table would grow by one row per
 * module and minute; the pending ones are never touched.
 */
public class MeasurementOutboxPurge {

    private static final Logger LOG = LoggerFactory.getLogger(MeasurementOutboxPurge.class);

    private final MeasurementOutboxRepository outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration retention;

    public MeasurementOutboxPurge(MeasurementOutboxRepository outbox, TransactionTemplate transaction,
                                  Clock clock, Duration retention) {
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * @return how many messages were removed
     */
    public int purge() {
        Integer removed = transaction.execute(status ->
                outbox.deleteDeliveredBefore(clock.instant().minus(retention)));
        int count = removed == null ? 0 : removed;
        if (count > 0) {
            LOG.info("Purged {} delivered measurement outbox messages older than {}", count, retention);
        }
        return count;
    }
}
