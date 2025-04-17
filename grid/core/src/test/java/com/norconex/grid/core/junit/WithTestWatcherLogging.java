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
package com.norconex.grid.core.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import com.norconex.grid.core.junit.WithTestWatcherLogging.LoggingTestWatcher;

import lombok.extern.slf4j.Slf4j;

//TODO move to Nx Commons?

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LoggingTestWatcher.class)
public @interface WithTestWatcherLogging {

    @Slf4j(topic = "🧪 TEST")
    public static class LoggingTestWatcher
            implements TestWatcher, BeforeTestExecutionCallback {

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            LOG.info("Starting test: {}", context.getDisplayName());
        }

        @Override
        public void testSuccessful(ExtensionContext context) {
            LOG.info("Test passed: {}", context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            LOG.warn("Test failed: {}", context.getDisplayName(), cause);
        }

        @Override
        public void testDisabled(
                ExtensionContext context, Optional<String> reason) {
            LOG.info("Test disabled: {} - Reason: {}",
                    context.getDisplayName(), reason.orElse("None given."));
        }

        @Override
        public void testAborted(ExtensionContext context, Throwable cause) {
            LOG.warn("Test aborted: {} - Cause: {}",
                    context.getDisplayName(), cause.getLocalizedMessage());
        }
    }
}
