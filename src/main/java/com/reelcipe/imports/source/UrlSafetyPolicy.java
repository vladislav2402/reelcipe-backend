package com.reelcipe.imports.source;

import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.ImportFailure;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

public class UrlSafetyPolicy {
    private static final Set<String> METADATA_ENDPOINTS = Set.of(
            "169.254.169.254",
            "169.254.170.2",
            "100.100.100.200",
            "fd00:ec2::254");

    private final HostAddressResolver addressResolver;
    private final boolean allowLocalAddresses;
    private final boolean allowNonStandardPorts;

    public UrlSafetyPolicy() {
        this(InetAddress::getAllByName, false, false);
    }

    public UrlSafetyPolicy(HostAddressResolver addressResolver, boolean allowLocalAddresses) {
        this(addressResolver, allowLocalAddresses, false);
    }

    public UrlSafetyPolicy(
            HostAddressResolver addressResolver,
            boolean allowLocalAddresses,
            boolean allowNonStandardPorts) {
        this.addressResolver = addressResolver;
        this.allowLocalAddresses = allowLocalAddresses;
        this.allowNonStandardPorts = allowNonStandardPorts;
    }

    public URI validate(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw blocked("SOURCE_URL_INVALID");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw blocked("SOURCE_URL_SCHEME_BLOCKED");
        }
        int port = uri.getPort();
        if (!allowNonStandardPorts && port != -1 && port != 80 && port != 443) {
            throw blocked("SOURCE_URL_PORT_BLOCKED");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) {
            throw blocked("SOURCE_URL_HOST_BLOCKED");
        }
        validateAddresses(host);
        return uri.normalize();
    }

    private void validateAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = addressResolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DNS_RESOLUTION_FAILED"), exception);
        }
        if (addresses.length == 0) {
            throw blocked("SOURCE_DNS_RESOLUTION_EMPTY");
        }
        for (InetAddress address : addresses) {
            if (!allowLocalAddresses && isBlocked(address)) {
                throw blocked("SOURCE_ADDRESS_BLOCKED");
            }
        }
    }

    private boolean isBlocked(InetAddress address) {
        String normalized = address.getHostAddress().toLowerCase(Locale.ROOT);
        int scopeSeparator = normalized.indexOf('%');
        if (scopeSeparator >= 0) {
            normalized = normalized.substring(0, scopeSeparator);
        }
        if (METADATA_ENDPOINTS.contains(normalized)) {
            return true;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address
                && (normalized.startsWith("fc") || normalized.startsWith("fd"))) {
            return true;
        }
        return isCarrierGradeNat(address);
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private ImportProcessingException blocked(String errorCode) {
        return new ImportProcessingException(ImportFailure.permanent(errorCode));
    }
}
