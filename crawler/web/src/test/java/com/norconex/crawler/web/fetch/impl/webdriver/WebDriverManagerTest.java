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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

@Timeout(30)
class WebDriverManagerTest {

    @Test
    void testGetDriverReturnsCurrentThreadDriver() throws Exception {
        var manager = newManager();
        try {
            var driver = mock(WebDriver.class);
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(driver, () -> {}));

            assertThat(manager.getDriver()).isSameAs(driver);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testQuitDriverRemovesAndClosesCurrentThreadEntry() throws Exception {
        var manager = newManager();
        var closed = new AtomicInteger();
        try {
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(mock(WebDriver.class),
                            () -> closed.incrementAndGet()));

            manager.quitDriver();

            assertThat(closed.get()).isEqualTo(1);
            assertThat(getDrivers(manager)).doesNotContainKey(
                    Thread.currentThread());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testSafeCallRetriesAfterWebDriverException() throws Exception {
        var manager = newManager();
        var closed = new AtomicInteger();
        var firstDriver = mock(WebDriver.class);
        var secondDriver = mock(WebDriver.class);
        var created = new AtomicInteger();
        try {
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(
                            created.getAndIncrement() == 0
                                    ? firstDriver
                                    : secondDriver,
                            () -> closed.incrementAndGet()));

            var seen = new AtomicInteger();
            var result = manager.safeCall(driver -> {
                if (seen.getAndIncrement() == 0) {
                    assertThat(driver).isSameAs(firstDriver);
                    throw new WebDriverException("boom");
                }
                assertThat(driver).isSameAs(secondDriver);
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
            assertThat(closed.get()).isEqualTo(1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testSafeCallWithoutRetryReturnsImmediately() throws Exception {
        var manager = newManager();
        try {
            var driver = mock(WebDriver.class);
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(driver, () -> {}));

            var result = manager.safeCall(d -> {
                assertThat(d).isSameAs(driver);
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testSafeCallPropagatesRetryFailure() throws Exception {
        var manager = newManager();
        var closed = new AtomicInteger();
        var created = new AtomicInteger();
        try {
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(
                            mock(WebDriver.class),
                            () -> {
                                closed.incrementAndGet();
                                created.incrementAndGet();
                            }));

            assertThatThrownBy(() -> manager.safeCall(driver -> {
                throw new WebDriverException("still broken");
            })).isInstanceOf(WebDriverException.class)
                    .hasMessageContaining("still broken");

            assertThat(closed.get()).isEqualTo(1);
            assertThat(created.get()).isEqualTo(1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testCleanupDeadThreadsRemovesDeadThreadEntries() throws Exception {
        var manager = newManager();
        var closed = new AtomicInteger();
        var deadThread = new Thread(() -> {}, "dead-thread");
        try {
            putDriverEntry(
                    manager,
                    deadThread,
                    () -> new DriverSession(mock(WebDriver.class),
                            () -> closed.incrementAndGet()));

            invokeCleanupDeadThreads(manager);

            assertThat(closed.get()).isEqualTo(1);
            assertThat(getDrivers(manager)).doesNotContainKey(deadThread);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testGetDriverResetsWhenNavigationLimitExceeded() throws Exception {
        var manager = new WebDriverManager(
                new WebDriverFetcherConfig()
                        .setCleanupInterval(Duration.ofDays(1))
                        .setBrowserMaxNavigations(0));
        var firstDriver = mock(WebDriver.class);
        var secondDriver = mock(WebDriver.class);
        var created = new AtomicInteger();
        var closed = new AtomicInteger();
        try {
            putDriverEntry(
                    manager,
                    Thread.currentThread(),
                    () -> new DriverSession(
                            created.getAndIncrement() == 0
                                    ? firstDriver
                                    : secondDriver,
                            () -> closed.incrementAndGet()));

            var driver = manager.getDriver();

            assertThat(driver).isSameAs(secondDriver);
            assertThat(closed.get()).isEqualTo(1);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testQuitDriverWithNoRegisteredEntryDoesNothing() {
        var manager = newManager();
        try {
            manager.quitDriver();
            manager.shutdown();
        } finally {
            // shutdown is safe to invoke a second time if it already ran above
            manager.shutdown();
        }
    }

    @Test
    void testShutdownClosesAllDriversAndClearsMap() throws Exception {
        var manager = newManager();
        var closed = new AtomicInteger();

        putDriverEntry(
                manager,
                Thread.currentThread(),
                () -> new DriverSession(mock(WebDriver.class),
                        () -> closed.incrementAndGet()));
        putDriverEntry(
                manager,
                new Thread(() -> {}, "other-thread"),
                () -> new DriverSession(mock(WebDriver.class),
                        () -> closed.incrementAndGet()));

        manager.shutdown();

        assertThat(closed.get()).isEqualTo(2);
        assertThat(getDrivers(manager)).isEmpty();
    }

    private WebDriverManager newManager() {
        return new WebDriverManager(
                new WebDriverFetcherConfig()
                        .setCleanupInterval(Duration.ofDays(1)));
    }

    private void invokeCleanupDeadThreads(WebDriverManager manager)
            throws Exception {
        Method method = WebDriverManager.class.getDeclaredMethod(
                "cleanupDeadThreads");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private void putDriverEntry(
            WebDriverManager manager,
            Thread thread,
            Supplier<DriverSession> supplier) throws Exception {
        getDrivers(manager).put(thread, createDriverEntry(supplier));
    }

    private Object createDriverEntry(Supplier<DriverSession> supplier)
            throws Exception {
        Class<?> type = Class.forName(
                WebDriverManager.class.getName() + "$DriverEntry");
        Constructor<?> ctor = type.getDeclaredConstructor(Supplier.class);
        ctor.setAccessible(true);
        return ctor.newInstance(supplier);
    }

    @SuppressWarnings("unchecked")
    private Map<Thread, Object> getDrivers(WebDriverManager manager)
            throws Exception {
        Field field = WebDriverManager.class.getDeclaredField("drivers");
        field.setAccessible(true);
        return (Map<Thread, Object>) field.get(manager);
    }
}
