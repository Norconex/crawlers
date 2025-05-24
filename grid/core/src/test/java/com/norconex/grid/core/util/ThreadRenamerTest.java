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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.commons.lang3.function.FailableFunction;
import org.junit.jupiter.api.Test;

class ThreadRenamerTest {

    @Test
    void testSuffixStringRunnable() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.runAsync(
                ThreadRenamer.suffix("renamed",
                        () -> ref.set(Thread.currentThread().getName())))))
                                .endsWith("renamed");
    }

    @Test
    void testSuffixStringSupplierOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                ThreadRenamer.suffix("renamed", (Supplier<String>) (() -> {
                    ref.set(Thread.currentThread().getName());
                    return "";
                }))))).endsWith("renamed");
    }

    @Test
    void testSuffixStringCallableOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                (Supplier<String>) (() -> {
                    try {
                        return ThreadRenamer.suffix("renamed",
                                (Callable<String>) (() -> {
                                    ref.set(Thread.currentThread().getName());
                                    return "";
                                })).call();
                    } catch (Exception e) {
                        return "";
                    }
                })))).endsWith("renamed");
    }

    @Test
    void testPrefixStringRunnable() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.runAsync(
                ThreadRenamer.prefix("renamed",
                        () -> ref.set(Thread.currentThread().getName())))))
                                .startsWith("renamed");
    }

    @Test
    void testPrefixStringSupplierOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                ThreadRenamer.prefix("renamed", (Supplier<String>) (() -> {
                    ref.set(Thread.currentThread().getName());
                    return "";
                }))))).startsWith("renamed");
    }

    @Test
    void testPrefixStringCallableOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                (Supplier<String>) (() -> {
                    try {
                        return ThreadRenamer.prefix("renamed",
                                (Callable<String>) (() -> {
                                    ref.set(Thread.currentThread().getName());
                                    return "";
                                })).call();
                    } catch (Exception e) {
                        return "";
                    }
                })))).startsWith("renamed");
    }

    @Test
    void testSetStringRunnable() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.runAsync(
                ThreadRenamer.set("renamed",
                        () -> ref.set(Thread.currentThread().getName())))))
                                .isEqualTo("renamed");
    }

    @Test
    void testSetStringSupplierOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                ThreadRenamer.set("renamed", (Supplier<String>) (() -> {
                    ref.set(Thread.currentThread().getName());
                    return "";
                }))))).isEqualTo("renamed");
    }

    @Test
    void testSetStringCallableOfT() throws Exception {
        assertThat(withFutureName(ref -> CompletableFuture.<String>supplyAsync(
                (Supplier<String>) (() -> {
                    try {
                        return ThreadRenamer.set("renamed",
                                (Callable<String>) (() -> {
                                    ref.set(Thread.currentThread().getName());
                                    return "";
                                })).call();
                    } catch (Exception e) {
                        return "";
                    }
                })))).isEqualTo("renamed");
    }

    // returns thread name
    private String withFutureName(
            FailableFunction<AtomicReference<String>, CompletableFuture<?>,
                    Exception> f)
            throws Exception {
        var beforeName = Thread.currentThread().getName();
        var nameRef = new AtomicReference<String>();
        f.apply(nameRef).get();
        assertThat(nameRef.get()).isNotEqualTo(
                Thread.currentThread().getName());
        assertThat(beforeName).isEqualTo(Thread.currentThread().getName());
        return nameRef.get();
    }

}
