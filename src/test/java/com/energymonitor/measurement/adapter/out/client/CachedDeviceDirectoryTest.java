package com.energymonitor.measurement.adapter.out.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.measurement.adapter.out.client.DeviceServiceClient.RemoteDevice;
import com.energymonitor.measurement.application.exception.DeviceServiceUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the cached device directory asks the device service, when it asks again, and what it
 * serves while the service does not answer. The clock of the caches is moved by hand.
 */
@DisplayName("Devices read from the device service")
class CachedDeviceDirectoryTest {

    private static final RemoteDevice FRIDGE = new RemoteDevice("DEV0000001", "hom0000001");

    private final DeviceServiceClient client = mock(DeviceServiceClient.class);
    private final AtomicLong nanos = new AtomicLong();
    private final CachedDeviceDirectory devices = new CachedDeviceDirectory(client, Duration.ofSeconds(30),
            Duration.ofHours(1), nanos::get);

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    @Test
    @DisplayName("the home of a device comes from one cached request")
    void homeFromOneRequest() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));

        assertThat(devices.homeOf("DEV0000001")).contains("hom0000001");
        assertThat(devices.homeOf("DEV0000001")).contains("hom0000001");

        verify(client, times(1)).device("DEV0000001");
    }

    @Test
    @DisplayName("an unknown device is cached too, so it is not asked every time")
    void unknownDeviceCached() {
        given(client.device("DEV0000099")).willReturn(Optional.empty());

        assertThat(devices.homeOf("DEV0000099")).isEmpty();
        assertThat(devices.homeOf("DEV0000099")).isEmpty();

        verify(client, times(1)).device("DEV0000099");
    }

    @Test
    @DisplayName("asks again once the entry expires")
    void asksAgainAfterTtl() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));

        devices.homeOf("DEV0000001");
        advance(Duration.ofSeconds(31));
        devices.homeOf("DEV0000001");

        verify(client, times(2)).device("DEV0000001");
    }

    @Test
    @DisplayName("serves the last answer while the service does not answer")
    void servesStaleWhenUnavailable() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));

        devices.homeOf("DEV0000001");
        devices.deviceIds("hom0000001");
        advance(Duration.ofMinutes(10));

        assertThat(devices.homeOf("DEV0000001")).contains("hom0000001");
        assertThat(devices.deviceIds("hom0000001")).containsExactly("DEV0000001");
    }

    @Test
    @DisplayName("with nothing kept, an unavailable service fails the call")
    void failsWithNothingKept() {
        given(client.device("DEV0000001")).willThrow(new DeviceServiceUnavailableException("down", null));

        assertThatThrownBy(() -> devices.homeOf("DEV0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the last answer is not kept forever")
    void staleExpires() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE))
                .willThrow(new DeviceServiceUnavailableException("down", null));

        devices.homeOf("DEV0000001");
        advance(Duration.ofHours(2));

        assertThatThrownBy(() -> devices.homeOf("DEV0000001"))
                .isInstanceOf(DeviceServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the devices of a home are cached by home")
    void devicesOfHomeCached() {
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE));

        assertThat(devices.deviceIds("hom0000001")).containsExactly("DEV0000001");
        devices.deviceIds("hom0000001");
        assertThat(devices.deviceIds(null)).isEmpty();

        verify(client, times(1)).devicesOfHome("hom0000001");
    }

    @Test
    @DisplayName("forgetting a device and its home asks the service again at once")
    void forget() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(client.devicesOfHome("hom0000001")).willReturn(List.of(FRIDGE));
        devices.homeOf("DEV0000001");
        devices.deviceIds("hom0000001");

        devices.forget("DEV0000001", "hom0000001");
        devices.homeOf("DEV0000001");
        devices.deviceIds("hom0000001");

        verify(client, times(2)).device("DEV0000001");
        verify(client, times(2)).devicesOfHome("hom0000001");
    }

    @Test
    @DisplayName("a device exists whether or not it is linked, from the same cached request as its home")
    void exists() {
        given(client.device("DEV0000001")).willReturn(Optional.of(FRIDGE));
        given(client.device("DEV0000002")).willReturn(Optional.of(new RemoteDevice("DEV0000002", null)));
        given(client.device("DEV0000099")).willReturn(Optional.empty());

        assertThat(devices.exists("DEV0000001")).isTrue();
        assertThat(devices.homeOf("DEV0000001")).contains("hom0000001");
        assertThat(devices.exists("DEV0000002")).isTrue();
        assertThat(devices.exists("DEV0000099")).isFalse();
        assertThat(devices.exists(null)).isFalse();

        verify(client, times(1)).device("DEV0000001");
    }

    @Test
    @DisplayName("no device asked is no request")
    void nothingAsked() {
        assertThat(devices.homeOf(null)).isEmpty();
        verifyNoInteractions(client);
    }
}
