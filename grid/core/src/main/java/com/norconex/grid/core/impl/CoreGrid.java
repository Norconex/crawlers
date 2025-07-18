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
package com.norconex.grid.core.impl;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridContext;
import com.norconex.grid.core.impl.compute.CoreCompute;
import com.norconex.grid.core.storage.GridStorage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreGrid implements Grid {

    /**
     * Key used to store a default context. This key is used to register
     * a context under a {@code null} or blank key, or when specifying a
     * {@code null} context key in a submitted grid task.
     */
    public static final String DEFAULT_CONTEXT_KEY = "default";

    @Getter
    private final String gridName;
    @Getter
    private String nodeName;
    @Getter
    private final CoreCompute compute;
    @Getter
    private final GridStorage storage;
    @Getter
    private final CoreGridConnectorConfig connectorConfig;

    private final List<QuorumWaiter> quorumWaiters = new ArrayList<>();
    private final ScheduledExecutorService quorumScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r);
                t.setDaemon(true);
                t.setName("grid-quorum-scheduler");
                return t;
            });

    private final Map<String, Object> localContexts = new ConcurrentHashMap<>();

    //--- JGroups ---
    private final JChannel channel;

    // used to detect coordinator changes only (otherwise obtained dynamically)
    private Address cachedCoordAddress;

    public CoreGrid(
            CoreGridConnectorConfig connConfig,
            GridStorage storage,
            //TODO goback to passing workdir only?  And let connectors
            // needing to pass one, pass it?
            GridContext gridContext)
            throws Exception {
        LOG.debug(org.jgroups.Version.printDescription());
        connectorConfig = connConfig;
        gridName = connConfig.getGridName();
        nodeName = connConfig.getNodeName();
        this.storage = storage;
        channel = new JChannel(connConfig.getProtocols());
        // The name the node and channel will share. If not set, will be the
        // address from the view, obtained after connecting
        if (StringUtils.isNotBlank(nodeName)) {
            channel.setName(nodeName);
        }
        channel.setReceiver(new JGroupMessageReceiver());
        channel.connect(gridName);
        compute = new CoreCompute(this);

    }

    public boolean isCoordinator() {
        return Objects.equals(getNodeAddress(), getCoordAddress());
    }

    public JChannel getChannel() {
        return channel;
    }

    public Address getNodeAddress() {
        return channel.getAddress();
    }

    public Address getCoordAddress() {
        return channel.getView().getCoord();
    }

    public List<Address> getGridMembers() {
        return ofNullable(channel.getView()).map(View::getMembers)
                .orElse(List.of());
    }

    @Override
    public void close() {
        //        if (dispatcher != null)
        //            try {
        //                dispatcher.close();
        //            } catch (IOException e) {
        //                // TODO Auto-generated catch block
        //                e.printStackTrace();
        //            }
        try {
            storage.close();
        } catch (IOException e) {
            LOG.error("Node {} could not close storage.", getNodeName(), e);
        }
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    public void resetSession() {
        storage.getSessionAttributes().clear();
    }

    @Override
    public void stop() {
        compute.stopTask(null);
    }

    @Override
    public void registerContext(String contextKey, Object context) {
        localContexts.put(StringUtils.isBlank(contextKey)
                ? DEFAULT_CONTEXT_KEY
                : contextKey,
                context);
    }

    @Override
    public Object getContext(String contextKey) {
        return localContexts.get(
                StringUtils.isBlank(contextKey) ? DEFAULT_CONTEXT_KEY
                        : contextKey);
    }

    @Override
    public Object unregisterContext(String contextKey) {
        return localContexts.remove(contextKey);
    }

    @Override
    public CompletableFuture<Void> awaitMinimumNodes(
            int count, Duration timeout) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Minimum node count must be > 0");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }

        var future = new CompletableFuture<Void>();
        synchronized (this) {
            if (getMemberCount(channel) >= count) {
                future.complete(null);
                return future;
            }

            // Track all pending quorum futures
            quorumWaiters.add(new QuorumWaiter(count, future));
        }

        // Schedule timeout
        quorumScheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException(
                        "Timed out waiting for minimum %s nodes. Got %s."
                                .formatted(count, getMemberCount(channel))));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return future;
    }

    private static int getMemberCount(JChannel channel) {
        return ofNullable(channel)
                .map(JChannel::getView)
                .map(View::getMembers)
                .map(List::size)
                .orElse(0);
    }

    //--- Inner Classes ---

    private class JGroupMessageReceiver implements Receiver {
        private final CountDownLatch initializedLatch = new CountDownLatch(1);

        //TODO Add method to receive/listen to messages???

        @Override
        public void viewAccepted(View view) {
            if (initializedLatch.getCount() > 0
                    && view.containsMember(channel.getAddress())) {
                if (StringUtils.isBlank(nodeName)) {
                    nodeName = channel.getAddressAsString();
                }
                LOG.info("Node joined \"{}\" grid: {}", gridName, nodeName);
                initializedLatch.countDown();
            }

            var meOrNot = isCoordinator() ? "me!" : "not me";
            var coordAddress = channel.view().getCoord();
            if (cachedCoordAddress == null) {
                LOG.info("Grid \"{}\" elected coordinator ({}): {}",
                        gridName, meOrNot, coordAddress);
            } else if (!Objects.equals(cachedCoordAddress, coordAddress)) {
                LOG.info("Grid \"{}\" coordinator changed: {} -> {} ({})",
                        gridName, cachedCoordAddress, coordAddress, meOrNot);
                //TODO need to handle the change??
            }
            cachedCoordAddress = coordAddress;

            var currentSize = view.getMembers().size();
            LOG.info("Current \"{}\" grid size: {}", gridName, currentSize);
            LOG.debug("Current \"{}\" grid nodes: {}", gridName, view);

            // Inform quorum waiters if applicable
            synchronized (this) {
                var iter = quorumWaiters.iterator();
                while (iter.hasNext()) {
                    var waiter = iter.next();
                    if (currentSize >= waiter.count
                            && !waiter.future.isDone()) {
                        waiter.future.complete(null);
                        iter.remove();
                    }
                }
            }
        }
    }

    private static class QuorumWaiter {
        final int count;
        final CompletableFuture<Void> future;

        QuorumWaiter(int count, CompletableFuture<Void> future) {
            this.count = count;
            this.future = future;
        }
    }
}
