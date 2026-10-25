/**
 * What other services learn from the Monitoring and Measurements context: the
 * {@code MeasurementRecorded} event and the {@code RiskConsumption} classification it is
 * evaluated with.
 *
 * <p>Inside this service the event is published to the Spring context; announcing it to
 * energy-monitor-backend, whose alerts evaluate every reading, comes with the outbox.
 */
package com.energymonitor.measurement.api;
