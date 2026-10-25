package com.energymonitor.measurement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.energymonitor.measurement.application.result.HourlyEnergyResult;
import com.energymonitor.measurement.application.usecase.GetHourlyEnergyService;
import com.energymonitor.measurement.application.usecase.ListReportingDevicesService;
import com.energymonitor.measurement.domain.model.Measurement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Readings asked by energy-monitor-backend")
class GetHourlyEnergyServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-24T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-24T12:59:59Z");

    private MeasurementUseCaseFixtures.FakeMeasurementPersistencePort measurements;
    private GetHourlyEnergyService hourly;
    private ListReportingDevicesService reporting;
    private int ids;

    @BeforeEach
    void setUp() {
        measurements = new MeasurementUseCaseFixtures.FakeMeasurementPersistencePort();
        hourly = new GetHourlyEnergyService(measurements);
        reporting = new ListReportingDevicesService(measurements);
    }

    private void reading(String device, String at, double power, double counter) {
        measurements.save(Measurement.record(String.format("mea%07d", ++ids), device, Instant.parse(at),
                230.0, power / 230.0, power, counter));
    }

    @Test
    @DisplayName("energy per device and hour, from the counter, with the reading before the range as baseline")
    void energyPerHour() {
        reading("DEV0000001", "2026-10-24T09:50:00Z", 100.0, 10.0);
        reading("DEV0000001", "2026-10-24T10:30:00Z", 200.0, 10.2);
        reading("DEV0000001", "2026-10-24T11:30:00Z", 400.0, 10.6);
        reading("DEV0000002", "2026-10-24T10:15:00Z", 50.0, 3.0);

        List<HourlyEnergyResult> result = hourly.hourly(List.of("DEV0000001", "DEV0000002"), FROM, TO);

        assertThat(result).filteredOn(r -> r.deviceId().equals("DEV0000001"))
                .extracting(HourlyEnergyResult::hourStart)
                .containsExactly(Instant.parse("2026-10-24T10:00:00Z"), Instant.parse("2026-10-24T11:00:00Z"));
        List<HourlyEnergyResult> fridge = result.stream().filter(r -> r.deviceId().equals("DEV0000001")).toList();
        assertThat(fridge.get(0).energy()).isCloseTo(0.2, within(1e-9));
        assertThat(fridge.get(1).energy()).isCloseTo(0.4, within(1e-9));
        assertThat(result).filteredOn(r -> r.deviceId().equals("DEV0000002")).hasSize(1);
    }

    @Test
    @DisplayName("no device is no energy")
    void noDevice() {
        assertThat(hourly.hourly(List.of(), FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("a missing or inverted range is refused")
    void badRange() {
        assertThatThrownBy(() -> hourly.hourly(List.of("DEV0000001"), null, TO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hourly.hourly(List.of("DEV0000001"), TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the devices reporting since an instant, and a missing instant refused")
    void reportingDevices() {
        reading("DEV0000001", "2026-10-24T09:00:00Z", 100.0, 10.0);
        reading("DEV0000002", "2026-10-24T11:00:00Z", 100.0, 10.0);

        assertThat(reporting.since(Instant.parse("2026-10-24T10:00:00Z"))).containsExactly("DEV0000002");
        assertThatThrownBy(() -> reporting.since(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
