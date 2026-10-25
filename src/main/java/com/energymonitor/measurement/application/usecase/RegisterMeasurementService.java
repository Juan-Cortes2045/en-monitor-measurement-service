package com.energymonitor.measurement.application.usecase;

import com.energymonitor.measurement.api.MeasurementRecorded;
import com.energymonitor.measurement.application.command.RegisterMeasurementCommand;
import com.energymonitor.measurement.application.port.in.RegisterMeasurement;
import com.energymonitor.measurement.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.measurement.application.port.out.MeasurementEventPort;
import com.energymonitor.measurement.application.port.out.MeasurementPersistencePort;
import com.energymonitor.measurement.application.result.MeasurementResult;
import com.energymonitor.measurement.domain.model.Measurement;
import java.time.Clock;
import java.time.Instant;

/**
 * Records a measurement produced by a device and publishes {@code MeasurementRecorded}.
 *
 * <p><strong>Transactional boundary:</strong> the reading and the message announcing it are two
 * writes that must commit together. The transaction is applied around this use case
 * ({@code TransactionalRegisterMeasurement}), not inside it.
 *
 * <p><strong>Device existence:</strong> the MER requires {@code deviceId} to be validated at
 * application level. The telemetry consumer asks the device service before calling this use case,
 * so the check is not repeated here; the schema enforces the value as {@code VARCHAR(10)}.
 *
 * <p>This service contains no Spring annotations.
 */
public class RegisterMeasurementService implements RegisterMeasurement {

    private final MeasurementPersistencePort measurementPort;
    private final IdentifierGeneratorPort identifiers;
    private final MeasurementEventPort events;
    private final Clock clock;

    public RegisterMeasurementService(MeasurementPersistencePort measurementPort,
                                      IdentifierGeneratorPort identifiers,
                                      MeasurementEventPort events, Clock clock) {
        this.measurementPort = measurementPort;
        this.identifiers = identifiers;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public MeasurementResult register(RegisterMeasurementCommand command) {
        Instant dateTime = command.dateTime() != null ? command.dateTime() : clock.instant();
        Measurement measurement = Measurement.record(identifiers.generate(), command.deviceId(),
                dateTime, command.voltage(), command.current(), command.activePower(),
                command.storedEnergy());
        measurementPort.save(measurement);

        // Observer pattern of the class diagram: the alerts of energy-monitor-backend subscribe to
        // this event, delivered through the outbox, instead of registering observers here.
        events.publish(new MeasurementRecorded(measurement.idMeasurement(), measurement.deviceId(),
                measurement.dateTime(), measurement.getVoltage(), measurement.getCurrent(),
                measurement.getActivePower(), measurement.getStoredEnergy()));

        return MeasurementResult.from(measurement);
    }
}
