package com.energymonitor.measurement.adapter.out.messaging;

import java.time.Instant;

/**
 * The JSON bodies of the messages in {@link MeasurementMessageTypes}, one record per type.
 *
 * <p>They are the wire contract, kept apart from the {@code api} records on purpose: a domain
 * event can gain a field without every consumer having to understand it. Instants travel as
 * ISO-8601 strings.
 */
public final class MeasurementMessagePayloads {

    /**
     * {@code measurement.recorded}: the reading as it was stored, with the time it was taken.
     */
    public record MeasurementRecordedPayload(String idMeasurement, String deviceId, Instant dateTime,
                                             double voltage, double current, double activePower,
                                             double storedEnergy) {
    }

    private MeasurementMessagePayloads() {
    }
}
