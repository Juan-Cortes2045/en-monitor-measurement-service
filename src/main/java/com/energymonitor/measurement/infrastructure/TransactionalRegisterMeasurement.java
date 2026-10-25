package com.energymonitor.measurement.infrastructure;

import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.result.MeasurementResult;
import com.energymonitor.measurement.application.usecase.RegisterMeasurementService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs {@link RegisterMeasurementService} in one transaction per reading.
 *
 * <p>A reading writes its row in {@code measurement} and, through the event, its message in
 * {@code measurement_outbox}. One without the other must never stay committed: a reading the
 * alerts never evaluate, or an alert for a reading that was rolled back. The service stays
 * framework-free; this decorator is what the MQTT consumer gets.
 */
public class TransactionalRegisterMeasurement implements RegisterMeasurement {

    private final RegisterMeasurementService target;
    private final TransactionTemplate transaction;

    public TransactionalRegisterMeasurement(RegisterMeasurementService target, TransactionTemplate transaction) {
        this.target = target;
        this.transaction = transaction;
    }

    @Override
    public MeasurementResult register(RegisterMeasurementCommand command) {
        return transaction.execute(status -> target.register(command));
    }
}
