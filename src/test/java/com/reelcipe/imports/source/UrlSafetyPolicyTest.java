package com.reelcipe.imports.source;

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyPolicyTest {
    @Test
    void blocksPrivateLoopbackAndMetadataAddresses() throws Exception {
        UrlSafetyPolicy policy = new UrlSafetyPolicy(
                host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")},
                false);

        assertThatThrownBy(() -> policy.validate(URI.create("https://metadata.example/")))
                .hasMessageContaining("SOURCE_ADDRESS_BLOCKED");
    }

    @Test
    void blocksIpv6Loopback() throws Exception {
        InetAddress loopback = Inet6Address.getByName("::1");
        UrlSafetyPolicy policy = new UrlSafetyPolicy(host -> new InetAddress[]{loopback}, false);

        assertThatThrownBy(() -> policy.validate(URI.create("https://ipv6.example/")))
                .hasMessageContaining("SOURCE_ADDRESS_BLOCKED");
    }

    @Test
    void blocksIpv6UniqueLocalAddresses() throws Exception {
        InetAddress privateAddress = Inet6Address.getByName("fd00::1");
        UrlSafetyPolicy policy = new UrlSafetyPolicy(
                host -> new InetAddress[]{privateAddress}, false);

        assertThatThrownBy(() -> policy.validate(URI.create("https://private.example/")))
                .hasMessageContaining("SOURCE_ADDRESS_BLOCKED");
    }

    @Test
    void checksDnsAgainAfterTheAddressChanges() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UrlSafetyPolicy policy = new UrlSafetyPolicy(host -> {
            if (calls.incrementAndGet() == 1) {
                return new InetAddress[]{InetAddress.getByName("8.8.8.8")};
            }
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        }, false);
        URI url = URI.create("https://rebound.example/video.mp4");

        policy.validate(url);
        assertThatThrownBy(() -> policy.validate(url))
                .hasMessageContaining("SOURCE_ADDRESS_BLOCKED");
    }

    @Test
    void blocksUnexpectedSchemeAndPort() {
        UrlSafetyPolicy policy = new UrlSafetyPolicy(host -> new InetAddress[]{
                InetAddress.getLoopbackAddress()}, true);

        assertThatThrownBy(() -> policy.validate(URI.create("file:///tmp/video.mp4")))
                .hasMessageContaining("SOURCE_URL_INVALID");
        assertThatThrownBy(() -> policy.validate(URI.create("http://localhost:8080/video.mp4")))
                .hasMessageContaining("SOURCE_URL_PORT_BLOCKED");
    }
}
