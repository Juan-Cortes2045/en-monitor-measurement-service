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

import com.energymonitor.measurement.application.exception.HomeServiceUnavailableException;
import com.energymonitor.measurement.application.port.out.HomeAccessPort.Limits;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Homes read from energy-monitor-backend")
class BackendHomeClientTest {

    private static final String ACCESS = "http://backend/internal/homes/hom0000001/access?userId=use0000001";
    private static final String THRESHOLDS = "http://backend/internal/homes/hom0000001/thresholds";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://backend")
            .defaultHeader("X-Internal-Token", "secret");
    private final MockRestServiceServer backend = MockRestServiceServer.bindTo(builder).build();
    private final BackendHomeClient homes = new BackendHomeClient(builder.build());

    private static String access(boolean exists, boolean member) {
        return "{\"exists\":" + exists + ",\"member\":" + member + ",\"owner\":false}";
    }

    @Test
    @DisplayName("a member of an existing home is a member, asked with the internal token")
    void member() {
        backend.expect(requestTo(ACCESS)).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess(access(true, true), MediaType.APPLICATION_JSON));

        assertThat(homes.isMember("use0000001", "hom0000001")).isTrue();
        backend.verify();
    }

    @Test
    @DisplayName("someone outside the home, or a home that does not exist, is not a member")
    void notMember() {
        backend.expect(requestTo(ACCESS))
                .andRespond(withSuccess(access(true, false), MediaType.APPLICATION_JSON));
        backend.expect(requestTo(ACCESS))
                .andRespond(withSuccess(access(false, true), MediaType.APPLICATION_JSON));

        assertThat(homes.isMember("use0000001", "hom0000001")).isFalse();
        assertThat(homes.isMember("use0000001", "hom0000001")).isFalse();
    }

    @Test
    @DisplayName("no caller or no home asks nothing")
    void nothingToAsk() {
        assertThat(homes.isMember(null, "hom0000001")).isFalse();
        assertThat(homes.isMember("use0000001", null)).isFalse();
        assertThat(homes.limits(null)).isEmpty();
        backend.verify();
    }

    @Test
    @DisplayName("a refused token, a server error or no answer is unavailable, never a guess")
    void accessUnavailable() {
        backend.expect(requestTo(ACCESS)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        backend.expect(requestTo(ACCESS)).andRespond(withServerError());
        backend.expect(requestTo(ACCESS)).andRespond(withException(new IOException("refused")));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> homes.isMember("use0000001", "hom0000001"))
                    .isInstanceOf(HomeServiceUnavailableException.class);
        }
    }

    @Test
    @DisplayName("reads the limits of a home")
    void limits() {
        backend.expect(requestTo(THRESHOLDS)).andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess("{\"dailyLimit\":10.0,\"monthlyLimit\":300.0,\"limitPeriod\":\"MONTHLY\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(homes.limits("hom0000001")).contains(new Limits(10.0, 300.0, "MONTHLY"));
    }

    @Test
    @DisplayName("404 is a home without limits, not a failure")
    void noLimits() {
        backend.expect(requestTo(THRESHOLDS)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(homes.limits("hom0000001")).isEmpty();
    }

    @Test
    @DisplayName("the limits of a backend that does not answer are unavailable")
    void limitsUnavailable() {
        backend.expect(requestTo(THRESHOLDS)).andRespond(withServerError());
        backend.expect(requestTo(THRESHOLDS)).andRespond(withException(new IOException("refused")));

        assertThatThrownBy(() -> homes.limits("hom0000001")).isInstanceOf(HomeServiceUnavailableException.class);
        assertThatThrownBy(() -> homes.limits("hom0000001")).isInstanceOf(HomeServiceUnavailableException.class);
    }
}
