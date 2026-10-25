package com.energymonitor.measurement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Internal caller check")
class InternalCallerCheckTest {

    private static InternalCallerCheck check(String token) {
        return new InternalCallerCheck(new InternalApiProperties(token, List.of("127.0.0.1/32", "172.16.0.0/12"),
                URI.create("http://localhost:8083"), URI.create("http://localhost:8084"),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofHours(1)));
    }

    private static MockHttpServletRequest request(String address, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        if (token != null) {
            request.addHeader(InternalApiProperties.TOKEN_HEADER, token);
        }
        return request;
    }

    @Test
    @DisplayName("trusts the right token from an allowed network, a Docker one included")
    void trusted() {
        assertThat(check("secret").trusts(request("127.0.0.1", "secret"))).isTrue();
        assertThat(check("secret").trusts(request("172.18.0.5", "secret"))).isTrue();
    }

    @Test
    @DisplayName("refuses a wrong or missing token, and another network")
    void refused() {
        assertThat(check("secret").trusts(request("127.0.0.1", "other"))).isFalse();
        assertThat(check("secret").trusts(request("127.0.0.1", null))).isFalse();
        assertThat(check("secret").trusts(request("192.168.1.20", "secret"))).isFalse();
    }

    @Test
    @DisplayName("with no token configured nothing is trusted")
    void failsClosed() {
        assertThat(check("").trusts(request("127.0.0.1", ""))).isFalse();
        assertThat(check(null).trusts(request("127.0.0.1", "anything"))).isFalse();
    }
}
