package com.energymonitor.measurement.adapter.in.internal;

import com.energymonitor.measurement.application.command.ListConsumptionLevelsQuery;
import com.energymonitor.measurement.application.port.in.GetHourlyEnergy;
import com.energymonitor.measurement.application.port.in.ListConsumptionLevels;
import com.energymonitor.measurement.application.port.in.ListReportingDevices;
import com.energymonitor.measurement.application.result.ConsumptionLevelResult;
import com.energymonitor.measurement.application.result.HourlyEnergyResult;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * What energy-monitor-backend asks about readings: its alerts classify every reading and compare
 * the energy of a home with its limits, and its recommendations look at the energy of the devices
 * that report. Only the services reach it; see {@code InternalApiConfiguration}.
 *
 * <ul>
 *   <li>{@code GET /internal/consumption-levels}: the catalogue, each level with its
 *       {@code [minLimit, maxLimit)} range, so the backend classifies a reading itself.</li>
 *   <li>{@code GET /internal/measurements/hourly-energy?deviceId=..&deviceId=..&from=..&to=..}:
 *       energy per device and UTC hour, oldest first.</li>
 *   <li>{@code GET /internal/measurements/reporting-devices?since=..}: devices with a reading
 *       since then.</li>
 * </ul>
 */
@Hidden
@RestController
public class InternalMeasurementController {

    private final ListConsumptionLevels listConsumptionLevels;
    private final GetHourlyEnergy getHourlyEnergy;
    private final ListReportingDevices listReportingDevices;

    public InternalMeasurementController(ListConsumptionLevels listConsumptionLevels,
                                         GetHourlyEnergy getHourlyEnergy,
                                         ListReportingDevices listReportingDevices) {
        this.listConsumptionLevels = listConsumptionLevels;
        this.getHourlyEnergy = getHourlyEnergy;
        this.listReportingDevices = listReportingDevices;
    }

    @GetMapping("/internal/consumption-levels")
    public List<ConsumptionLevelResult> consumptionLevels() {
        return listConsumptionLevels.list(new ListConsumptionLevelsQuery());
    }

    @GetMapping("/internal/measurements/hourly-energy")
    public List<HourlyEnergyResult> hourlyEnergy(
            @RequestParam(name = "deviceId", required = false) List<String> deviceIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return getHourlyEnergy.hourly(deviceIds == null ? List.of() : deviceIds, from, to);
    }

    @GetMapping("/internal/measurements/reporting-devices")
    public List<String> reportingDevices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return listReportingDevices.since(since);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
