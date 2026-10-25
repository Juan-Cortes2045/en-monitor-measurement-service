package com.energymonitor.measurement.adapter.out.messaging;

/**
 * The messages this service publishes, named by their routing key on the
 * {@code energy-monitor.measurement} topic exchange.
 *
 * <p>These names are a contract with the services that consume them: renaming one silently
 * stops its delivery, because the consumer's binding no longer matches.
 */
public final class MeasurementMessageTypes {

    /** A reading was stored: energy-monitor-backend evaluates its alerts with it. */
    public static final String MEASUREMENT_RECORDED = "measurement.recorded";

    private MeasurementMessageTypes() {
    }
}
