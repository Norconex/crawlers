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

import java.lang.management.ThreadInfo;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import com.norconex.commons.lang.map.MapUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates {@link ThreadFactory} instances with a thread name prefixed with
 * a scope name and can log extra information on threads at job run begin/stop
 * (if DEBUG is enabled).
 */
@RequiredArgsConstructor
@Slf4j
public class ScopedThreadFactoryCreator {

    private static final Map<String, AtomicInteger> SCOPED_THREAD_COUNTS =
            new ConcurrentHashMap<>();

    private final String scopeName;

    public ThreadFactory create(String threadName) {
        return new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                new AtomicInteger();
                var scopedCount = SCOPED_THREAD_COUNTS.computeIfAbsent(
                        scopeName, k -> new AtomicInteger())
                        .incrementAndGet();
                var fullName = scopeName + "-" + threadName + "-" + scopedCount;
                var t = new Thread(LOG.isDebugEnabled() ? () -> {
                    debug(false, fullName, scopedCount);
                    r.run();
                    debug(true, fullName, scopedCount);
                } : r);
                t.setName(fullName);
                return t;
            }

            private void debug(
                    boolean ended,
                    String threadName,
                    int scopedCount) {
                if (!LOG.isDebugEnabled()) {
                    return;
                }

                var allScopedCnt = SCOPED_THREAD_COUNTS.values().stream()
                        .mapToInt(AtomicInteger::get)
                        .sum();

                var scopedAliveCount = new AtomicInteger();
                var activeThreads = ThreadTracker.allThreadInfos().stream()
                        .sorted(Comparator.comparing(ThreadInfo::getThreadName))
                        .map(t -> {
                            var tname = t.getThreadName();
                            var scoped = SCOPED_THREAD_COUNTS
                                    .keySet()
                                    .stream()
                                    .anyMatch(tname::startsWith);
                            if (scoped) {
                                scopedAliveCount.incrementAndGet();
                            }
                            var mark = scoped ? "* " : "  ";
                            var status = StringUtils.rightPad(
                                    t.getThreadState().name(), 15);
                            return status + mark + tname;
                        }).collect(Collectors.joining("\n    "));

                LOG.debug(StringSubstitutor.replace("""


                    ${ar} Thread #${scopeCnt} in scope "${scopeName}" ${when}
                    ${ar} ------------------------------------------------------
                    ${ar} Full thread Name:             ${threadName}
                    ${ar} Live thread count:            ${aliveCnt}
                    ${ar} Peak thread count:            ${peakCnt}
                    ${ar} Total scoped threads created: ${allScopedCnt}
                    ${ar} Scoped threads still alive:   ${scopedAlives}
                    ${ar} Threads:
                        ${activeThreads}
                    """, MapUtil.toMap(
                        "ar", ended ? "🡩" : "🡫",
                        "when", ended ? "ENDED" : "BEGAN",
                        "scopeCnt", scopedCount,
                        "threadName", threadName,
                        "allScopedCnt", allScopedCnt,
                        "scopeName", scopeName,
                        "aliveCnt", ThreadTracker.getLiveThreadCount(),
                        "peakCnt", ThreadTracker.getPeakThreadCount(),
                        "scopedAlives", scopedAliveCount,
                        "activeThreads", activeThreads

                )));
            }

        };
    }

    //    private void debugBefore(
    //            String scopeName,
    //            String fullName,
    //            int createdCount,
    //            int usageCount) {
    //        LOG.debug("""
    //
    //
    //            🡫 TASK BEGAN | THREAD %s | USAGE: %s | %s
    //            🡫 ------------------------------------------------
    //            🡫 Scope:
    //            🡫 Alive threads count: %s
    //            🡫 Peak  threads count: %s
    //            """.formatted(
    //                createdCount,
    //                usageCount,
    //                fullName,
    //                ThreadTracker.getLiveThreadCount(),
    //                ThreadTracker.getPeakThreadCount()));
    //    }

    //    private void debugAfter(String fullName, int createdCount, int usageCount) {
    //        LOG.debug("""
    //
    //
    //            🡩 TASK ENDED | THREAD %s | USAGE: %s | %s
    //            🡩 ------------------------------------------------
    //            🡩 Alive threads count: %s
    //            🡩 Peak  threads count: %s
    //            """.formatted(
    //                createdCount,
    //                usageCount,
    //                fullName,
    //                ThreadTracker.getLiveThreadCount(),
    //                ThreadTracker.getPeakThreadCount()));
    //    }

}
