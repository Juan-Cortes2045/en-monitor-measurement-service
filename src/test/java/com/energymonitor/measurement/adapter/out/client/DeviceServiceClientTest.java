package com.energymonitor.measurement.adapter.out.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.measurement.adapter.out.client.DeviceServiceClient.RemoteDevice;
import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Device service client")
class DeviceServiceClientTest {

    private static final String FRIDGE = "{\"idDevice\":\"DEV0000001\",\"name\":\"Nevera\","
            + "\"applianceTypeId\":\"appl000001\",\"location\":\"Cocina\",\"description\":null,"
            + "\"installationDate\":\"2026-10-01T10:00:00Z\",\"deviceCode\":\"EM204\",\"homeId\":\"hom0000001\"}";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://device")
            .defaultHeader("X-Internal-Token", "secret");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final DeviceServiceClient client = new DeviceServiceClient(builder.build());

    @Test
    @DisplayName("reads the home of a device, sending the internal token and ignoring the rest")
    void device() {
        server.expect(requestTo("http://device/internal/devices/DEV0000001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess(FRIDGE, MediaType.APPLICATION_JSON));

        assertThat(client.device("DEV0000001")).contains(new RemoteDevice("DEV0000001", "hom0000001"));
        server.verify();
    }

    @Test
    @DisplayName("404 is a device that does not exist, not a failure")
    void unknownDevice() {
        server.expect(requestTo("http://device/internal/devices/DEV0000099"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.device("DEV0000099")).isEmpty();
    }

    @Test
    @DisplayName("reads the devices of a home, and none for a home without any")
    void devicesOfHome() {
        server.expect(requestTo("http://device/internal/homes/hom0000001/devices"))
                .andRespond(withSuccess("[" + FRIDGE + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://device/internal/homes/hom0000002/devices"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.devicesOfHome("hom0000001")).extracting(RemoteDevice::idDevice)
                .containsExactly("DEV0000001");
        assertThat(client.devicesOfHome("hom0000002")).isEmpty();
    }

    @Test
    @DisplayName("a refused token, a server error or no answer is unavailable")
    void unavailable() {
        server.expect(requestTo("http://device/internal/devices/DEV0000001"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("http://device/internal/homes/hom0000001/devices"))
                .andRespond(withServerError());
        server.expect(requestTo("http://device/internal/devices/DEV0000002"))
                .andRespond(withException(new IOException("refused")));

        assertThatThrownBy(() -> client.device("DEV0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> client.devicesOfHome("hom0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class).hasMessageContaining("500");
        assertThatThrownBy(() -> client.device("DEV0000002"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }
}
