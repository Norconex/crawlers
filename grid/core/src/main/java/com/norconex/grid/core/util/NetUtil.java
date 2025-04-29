/* Copyright 2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.grid.core.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgroups.stack.IpAddress;

public final class NetUtil {

    private static final String LOCALHOST_IP = "127.0.0.1";

    private NetUtil() {
    }

    public static int getAvailablePort() {
        try (var socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to find an available port", e);
        }
    }

    public static int[] getAvailablePorts(int numPorts) {
        Set<Integer> usedPorts = new HashSet<>();
        List<Integer> bindPorts = new ArrayList<>();
        while (bindPorts.size() < numPorts) {
            var port = getAvailablePort();
            if (usedPorts.add(port)) {
                bindPorts.add(port);
            }
        }
        return bindPorts.stream().mapToInt(Integer::intValue).toArray();
    }

    public static InetAddress localInetAddress() {
        try {
            return InetAddress.getByName(LOCALHOST_IP);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    "Could not resolve INET address: " + LOCALHOST_IP, e);
        }
    }

    public static InetSocketAddress localInetSocketAddress(int port) {
        return new InetSocketAddress(NetUtil.localInetAddress(), port);
    }

    public static IpAddress localIpAddress(int port) {
        try {
            return new IpAddress(LOCALHOST_IP, port);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    "Could not resolve IP address: %s:%s".formatted(
                            LOCALHOST_IP, port),
                    e);
        }
    }

    public static String localHost(int port) {
        try {
            return "%s[%s]".formatted(
                    new IpAddress(LOCALHOST_IP, port).printHostAddress(),
                    port);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    "Could not resolve IP address: %s:%s"
                            .formatted(LOCALHOST_IP, port),
                    e);
        }
    }
}
