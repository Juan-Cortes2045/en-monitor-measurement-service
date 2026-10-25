package com.energymonitor.measurement.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import com.energymonitor.measurement.application.exception.HomeServiceUnavailableException;
import com.energymonitor.measurement.application.port.out.HomeAccessPort;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The filter chain in front of the endpoints, with a token signed as the security service signs
 * them, and what a caller gets while the owners of homes and devices do not answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Secured endpoints")
class SecuredEndpointsTest extends JwtKeyedTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HomeAccessPort homes;

    @Test
    @DisplayName("without a token the API answers 401 with the shared error body")
    void noToken() throws Exception {
        mvc.perform(get("/api/v1/measurements/latest").param("deviceId", "DEV0000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/homes/hom0000001/consumption/summary"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/consumption-levels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token signed by someone else is refused")
    void foreignToken() throws Exception {
        mvc.perform(get("/api/v1/consumption-levels")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.c2lnbmF0dXJl"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("with a valid token the consumption levels are listed")
    void levels() throws Exception {
        mvc.perform(get("/api/v1/consumption-levels").header("Authorization", bearer("use0000001")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a device of a home the caller does not belong to looks missing")
    void foreignDevice() throws Exception {
        given(homes.homeOf("DEV0000001")).willReturn(Optional.of("hom0000001"));
        given(homes.isMember("use0000001", "hom0000001")).willReturn(false);

        mvc.perform(get("/api/v1/measurements/latest").param("deviceId", "DEV0000001")
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("503 while the device service or the backend does not answer")
    void ownersUnavailable() throws Exception {
        given(homes.homeOf("DEV0000001")).willThrow(new DeviceServiceUnavailableException("down", null));
        given(homes.isMember(any(), any())).willThrow(new HomeServiceUnavailableException("down"));

        mvc.perform(get("/api/v1/measurements/latest").param("deviceId", "DEV0000001")
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/homes/hom0000001/consumption/summary")
                        .header("Authorization", bearer("use0000001")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("the health probe needs no token, and nothing outside the API is open")
    void outsideTheApi() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/internal/devices/DEV0000001").header("Authorization", bearer("use0000001")))
                .andExpect(status().isForbidden());
    }
}
