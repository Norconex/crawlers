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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
import com.norconex.grid.core.util.ExecutorManager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreGrid implements Grid {

    @Getter
    private final String gridName;
    @Getter
    private String nodeName;
    @Getter
    private final GridContext gridContext;
    @Getter
    private final CoreCompute compute;
    @Getter
    private final GridStorage storage;

    private final List<QuorumWaiter> quorumWaiters = new ArrayList<>();
    private final ScheduledExecutorService quorumScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r);
                t.setDaemon(true);
                t.setName("grid-quorum-scheduler");
                return t;
            });

    //--- JGroups ---
    private final JChannel channel;
    @Getter
    private Address nodeAddress;
    @Getter
    private Address coordAddress;

    CoreGrid(
            CoreGridConnectorConfig connConfig,
            GridStorage storage,
            GridContext gridContext)
            throws Exception {
        LOG.info(org.jgroups.Version.printDescription());
        this.gridContext = gridContext;
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
        return Objects.equals(nodeAddress, coordAddress);
    }

    public JChannel getChannel() {
        return channel;
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
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    public ExecutorManager getNodeExecutors() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean resetSession() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

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
            //            view = newView;

            if (initializedLatch.getCount() > 0
                    && view.containsMember(channel.getAddress())) {
                view = channel.getView();
                nodeAddress = channel.getAddress();
                if (StringUtils.isBlank(nodeName)) {
                    nodeName = channel.getAddressAsString();
                }
                LOG.info("Node joined \"{}\" grid: {}", gridName, nodeName);
                initializedLatch.countDown();
            }

            var currentSize = view.getMembers().size();
            LOG.info("Current \"{}\" grid size: {}", gridName, currentSize);
            var prevCoordAddress = coordAddress;
            var nextCoordAddress = view.getCoord();
            coordAddress = nextCoordAddress;
            if (!Objects.equals(prevCoordAddress, nextCoordAddress)) {
                // if it changed (as opposed to first set), notify all
                if (prevCoordAddress == null) {
                    LOG.info("Grid \"{}\" elected coordinator: {}",
                            gridName, nextCoordAddress);
                } else {
                    LOG.info("New \"{}\" grid coordinator elected: {} -> "
                            + "{}", gridName,
                            prevCoordAddress, nextCoordAddress);
                    //TODO handle handling of new coordinator
                    //send(new NewCoordMessage());
                }
            }

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
