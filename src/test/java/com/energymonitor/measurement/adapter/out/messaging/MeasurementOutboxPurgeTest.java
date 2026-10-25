package com.energymonitor.measurement.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.measurement.adapter.out.persistence.entity.MeasurementOutboxEntity;
import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementOutboxRepository;
import com.energymonitor.measurement.infrastructure.JwtKeyedTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The purge against the real schema. {@code @Transactional}: the purge joins the test's
 * transaction, which is rolled back, so the shared table is left as it was, whatever else the
 * purge removed in between. The clock is far in the future, so the rows of this test are old or
 * recent whatever the date the suite runs.
 */
@SpringBootTest
@Transactional
@DisplayName("Measurement outbox purge")
class MeasurementOutboxPurgeTest extends JwtKeyedTest {

    private static final Instant NOW = Instant.parse("2099-01-10T00:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(7);

    @Autowired
    private MeasurementOutboxRepository outbox;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    @DisplayName("removes what was delivered before the retention, keeps the recent and the pending")
    void purgesOnlyOldDelivered() {
        Instant old = NOW.minus(Duration.ofDays(8));
        Instant recent = NOW.minus(Duration.ofDays(1));
        save("mpu0000001", old, old, null);       // published long ago
        save("mpu0000002", old, null, old);       // discarded long ago
        save("mpu0000003", recent, recent, null); // published recently
        save("mpu0000004", old, null, null);      // still pending, however old

        int removed = new MeasurementOutboxPurge(outbox, new TransactionTemplate(transactions),
                Clock.fixed(NOW, ZoneOffset.UTC), RETENTION).purge();

        assertThat(removed).isGreaterThanOrEqualTo(2);
        assertThat(outbox.findById("mpu0000001")).isEmpty();
        assertThat(outbox.findById("mpu0000002")).isEmpty();
        assertThat(outbox.findById("mpu0000003")).isPresent();
        assertThat(outbox.findById("mpu0000004")).isPresent();
    }

    private void save(String id, Instant occurredAt, Instant publishedAt, Instant discardedAt) {
        MeasurementOutboxEntity message = new MeasurementOutboxEntity();
        message.setIdMessage(id);
        message.setMessageType(MeasurementMessageTypes.MEASUREMENT_RECORDED);
        message.setPayload("{\"deviceId\":\"DEV0000001\"}");
        message.setOccurredAt(occurredAt);
        message.setPublishedAt(publishedAt);
        message.setDiscardedAt(discardedAt);
        outbox.saveAndFlush(message);
    }
}
