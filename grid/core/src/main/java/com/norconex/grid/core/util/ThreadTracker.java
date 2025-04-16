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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class ThreadTracker {

    private static final ThreadMXBean THREAD_MX_BEAN =
            ManagementFactory.getThreadMXBean();

    private ThreadTracker() {
    }

    public static List<ThreadInfo> allThreadInfos(Predicate<ThreadInfo> p) {
        var threadIds = THREAD_MX_BEAN.getAllThreadIds();
        var infos = THREAD_MX_BEAN.getThreadInfo(threadIds, Integer.MAX_VALUE);
        return Arrays.stream(infos)
                .filter(Objects::nonNull)
                .filter(p)
                .toList();
    }

    public static List<ThreadInfo> allThreadInfos() {
        return allThreadInfos(t -> true);
    }

    public static void printAllThreads(String reason) {
        System.err.println("==== Thread Dump: " + reason + " ====");
        var threadIds = THREAD_MX_BEAN.getAllThreadIds();
        var threadInfos =
                THREAD_MX_BEAN.getThreadInfo(threadIds, Integer.MAX_VALUE);

        Arrays.stream(threadInfos)
                .filter(info -> info != null)
                .forEach(ThreadTracker::printThreadInfo);

        System.err.println("==== End of Thread Dump ====");
    }

    private static void printThreadInfo(ThreadInfo info) {
        System.err.printf("\"%s\" Id=%d State=%s%n",
                info.getThreadName(), info.getThreadId(),
                info.getThreadState());

        for (StackTraceElement ste : info.getStackTrace()) {
            System.err.println("    at " + ste);
        }

        System.err.println();
    }

    public static int getLiveThreadCount() {
        return THREAD_MX_BEAN.getThreadCount();
    }

    public static int getPeakThreadCount() {
        return THREAD_MX_BEAN.getPeakThreadCount();
    }

    public static int getDaemonThreadCount() {
        return THREAD_MX_BEAN.getDaemonThreadCount();
    }

    public static void resetPeakThreadCount() {
        THREAD_MX_BEAN.resetPeakThreadCount();
    }
}
