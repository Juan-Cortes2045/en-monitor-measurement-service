package com.energymonitor.measurement.adapter.out.event;

import com.energymonitor.measurement.adapter.out.messaging.MeasurementMessagePayloads.MeasurementRecordedPayload;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementMessageTypes;
import com.energymonitor.measurement.adapter.out.messaging.MeasurementOutboxWriter;
import com.energymonitor.measurement.api.MeasurementRecorded;
import com.energymonitor.measurement.application.port.out.MeasurementEventPort;

/**
 * Announces every recorded reading to the other services through the outbox.
 *
 * <p>energy-monitor-backend hears {@code measurement.recorded} and evaluates its alerts with it.
 * The use case publishes inside its transaction, so the outbox row commits with the reading.
 */
public class MeasurementEventAdapter implements MeasurementEventPort {

    private final MeasurementOutboxWriter outbox;

    public MeasurementEventAdapter(MeasurementOutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(MeasurementRecorded event) {
        outbox.write(MeasurementMessageTypes.MEASUREMENT_RECORDED,
                new MeasurementRecordedPayload(event.idMeasurement(), event.deviceId(), event.dateTime(),
                        event.voltage(), event.current(), event.activePower(), event.storedEnergy()),
                event.dateTime());
    }
}
