/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openqa.selenium.WebDriver;

final class DriverSession implements AutoCloseable {

    @FunctionalInterface
    interface CloseAction {
        void close() throws Exception;
    }

    private static final Set<DriverSession> OPEN_SESSIONS =
            ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                DriverSession::closeAllOpenSessions,
                "DriverSession-ShutdownHook"));
    }

    private final WebDriver driver;
    private final CloseAction closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    DriverSession(WebDriver driver, CloseAction closeAction) {
        this.driver = driver;
        this.closeAction = closeAction;
        OPEN_SESSIONS.add(this);
    }

    static DriverSession of(WebDriver driver) {
        return new DriverSession(driver, driver::quit);
    }

    WebDriver driver() {
        return driver;
    }

    void untrack() {
        OPEN_SESSIONS.remove(this);
    }

    @Override
    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) {
            OPEN_SESSIONS.remove(this);
            closeAction.close();
        }
    }

    static void closeAllOpenSessions() {
        var failures = new ArrayList<Exception>();
        for (var session : new ArrayList<>(OPEN_SESSIONS)) {
            try {
                session.close();
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            var ex = new IllegalStateException(
                    "Could not close all WebDriver sessions.");
            failures.forEach(ex::addSuppressed);
            throw ex;
        }
    }
}
