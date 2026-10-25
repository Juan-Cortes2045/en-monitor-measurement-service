package com.energymonitor.measurement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.energymonitor.measurement.application.port.in.ListReportingDevices;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The address the service sees behind the gateway. MockMvc skips Tomcat, where the forwarded
 * headers are applied, so these requests go to a running server on a random port; they all come
 * from loopback, a trusted proxy, and differ only in what they forward.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Behind the gateway")
class BehindGatewayTest extends JwtKeyedTest {

    private static final String TOKEN = "test-only-internal-token";

    /**
     * A refusal on a running server: the chain answers 403, and Tomcat's error page for it is
     * then refused by the API chain with 401. Either way nothing reaches the controller.
     */
    private static final Integer[] REFUSED = {401, 403};

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @MockitoBean
    private ListReportingDevices listReportingDevices;

    @BeforeEach
    void noReadings() {
        given(listReportingDevices.since(any())).willReturn(List.of());
    }

    @Test
    @DisplayName("the backend calling directly reaches the internal API")
    void directInternalCall() throws Exception {
        assertThat(internalCall(null)).isEqualTo(200);
    }

    @Test
    @DisplayName("a caller from the Docker network relayed by a proxy reaches the internal API")
    void relayedFromTheDockerNetwork() throws Exception {
        assertThat(internalCall("172.18.0.5")).isEqualTo(200);
    }

    @Test
    @DisplayName("an outside caller relayed by a proxy counts as itself and is refused")
    void relayedFromOutside() throws Exception {
        assertThat(internalCall("203.0.113.9")).isIn(REFUSED);
        verify(listReportingDevices, never()).since(any());
    }

    @Test
    @DisplayName("an outside caller cannot pass as loopback by forwarding it")
    void spoofedLoopback() throws Exception {
        // The proxy appends the real address; the rightmost untrusted one is the caller.
        assertThat(internalCall("127.0.0.1, 203.0.113.9")).isIn(REFUSED);
        verify(listReportingDevices, never()).since(any());
    }

    private int internalCall(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(url("/internal/measurements/reporting-devices?since=2026-10-24T00:00:00Z"))
                .header("X-Internal-Token", TOKEN)
                .GET();
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
