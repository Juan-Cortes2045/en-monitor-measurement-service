package com.energymonitor.measurement.adapter.in.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.measurement.api.RiskConsumption;
import com.energymonitor.measurement.application.port.in.GetHourlyEnergy;
import com.energymonitor.measurement.application.port.in.ListConsumptionLevels;
import com.energymonitor.measurement.application.port.in.ListReportingDevices;
import com.energymonitor.measurement.application.result.ConsumptionLevelResult;
import com.energymonitor.measurement.application.result.HourlyEnergyResult;
import com.energymonitor.measurement.infrastructure.JwtKeyedTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The internal API energy-monitor-backend reads: who may call it, and what it answers. The token
 * is the one of the test profile; MockMvc requests come from 127.0.0.1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Internal measurement API")
class InternalMeasurementControllerWebTest extends JwtKeyedTest {

    private static final String TOKEN = "test-only-internal-token";
    private static final String HOURLY = "/internal/measurements/hourly-energy";
    private static final Instant FROM = Instant.parse("2026-10-24T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-24T23:59:59Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListConsumptionLevels listConsumptionLevels;
    @MockitoBean
    private GetHourlyEnergy getHourlyEnergy;
    @MockitoBean
    private ListReportingDevices listReportingDevices;

    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Token", TOKEN);
    }

    @Test
    @DisplayName("the consumption levels, each with its range")
    void levels() throws Exception {
        given(listConsumptionLevels.list(any())).willReturn(List.of(
                new ConsumptionLevelResult("lev0000001", RiskConsumption.LOW, "Bajo", 0.0, 500.0)));

        mvc.perform(internal(get("/internal/consumption-levels")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idConsumptionLevel").value("lev0000001"))
                .andExpect(jsonPath("$[0].name").value("LOW"))
                .andExpect(jsonPath("$[0].minLimit").value(0.0))
                .andExpect(jsonPath("$[0].maxLimit").value(500.0));
    }

    @Test
    @DisplayName("the energy per device and hour of the devices asked")
    void hourlyEnergy() throws Exception {
        given(getHourlyEnergy.hourly(List.of("DEV0000001", "DEV0000002"), FROM, TO)).willReturn(List.of(
                new HourlyEnergyResult("DEV0000001", Instant.parse("2026-10-24T10:00:00Z"), 200.0, 0.2)));

        mvc.perform(internal(get(HOURLY).param("deviceId", "DEV0000001", "DEV0000002")
                        .param("from", FROM.toString()).param("to", TO.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("DEV0000001"))
                .andExpect(jsonPath("$[0].hourStart").value("2026-10-24T10:00:00Z"))
                .andExpect(jsonPath("$[0].averagePower").value(200.0))
                .andExpect(jsonPath("$[0].energy").value(0.2));
    }

    @Test
    @DisplayName("no device asked is an empty list, and a missing or wrong range is 400")
    void hourlyEnergyParameters() throws Exception {
        given(getHourlyEnergy.hourly(List.of(), FROM, TO)).willReturn(List.of());
        given(getHourlyEnergy.hourly(List.of("DEV0000001"), TO, FROM))
                .willThrow(new IllegalArgumentException("to must not be before from"));

        mvc.perform(internal(get(HOURLY).param("from", FROM.toString()).param("to", TO.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(internal(get(HOURLY).param("deviceId", "DEV0000001").param("from", FROM.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(internal(get(HOURLY).param("deviceId", "DEV0000001").param("from", "yesterday")
                        .param("to", TO.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(internal(get(HOURLY).param("deviceId", "DEV0000001")
                        .param("from", TO.toString()).param("to", FROM.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the devices reporting since an instant")
    void reportingDevices() throws Exception {
        given(listReportingDevices.since(FROM)).willReturn(List.of("DEV0000001"));

        mvc.perform(internal(get("/internal/measurements/reporting-devices").param("since", FROM.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("DEV0000001"));
        verify(listReportingDevices).since(FROM);
    }

    @Test
    @DisplayName("refused without the token, with a wrong one, from outside the networks or with a user token")
    void refused() throws Exception {
        mvc.perform(get("/internal/consumption-levels")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/consumption-levels").header("X-Internal-Token", "guess"))
                .andExpect(status().isForbidden());
        mvc.perform(internal(get("/internal/consumption-levels")).with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/measurements/reporting-devices").param("since", FROM.toString())
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listConsumptionLevels, listReportingDevices);
    }
}
