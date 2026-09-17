package com.reelcipe.imports.source;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
public interface HostAddressResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
}
