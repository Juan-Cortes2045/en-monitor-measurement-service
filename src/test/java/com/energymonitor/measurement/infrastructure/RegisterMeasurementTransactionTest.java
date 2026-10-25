package com.energymonitor.measurement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.energymonitor.measurement.adapter.out.persistence.repository.MeasurementRepository;
import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.port.out.MeasurementEventPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A reading and its outbox message commit together: when the message cannot be written, the
 * reading is not stored either. Not {@code @Transactional}, so the rollback is the one of the
 * use case; nothing is left behind because nothing commits.
 */
@SpringBootTest
@DisplayName("Recording a reading is one transaction")
class RegisterMeasurementTransactionTest extends JwtKeyedTest {

    @Autowired
    private RegisterMeasurement registerMeasurement;
    @Autowired
    private MeasurementRepository measurements;

    @MockitoBean
    private MeasurementEventPort events;

    @Test
    @DisplayName("a failed outbox write rolls the reading back")
    void failedMessageRollsBackTheReading() {
        String device = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        willThrow(new IllegalStateException("outbox unavailable")).given(events).publish(any());

        assertThatThrownBy(() -> registerMeasurement.register(new RegisterMeasurementCommand(device,
                Instant.parse("2026-10-24T20:00:00Z"), 231.4, 1.2, 285.5, 12.8)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(measurements.findFirstByDeviceIdAndDeletedAtIsNullOrderByDateTimeDesc(device)).isEmpty();
    }
}
