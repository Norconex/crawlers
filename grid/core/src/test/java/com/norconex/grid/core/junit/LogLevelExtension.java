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

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

//TODO move to Nx Commons?

public class LogLevelExtension
        implements BeforeEachCallback, AfterEachCallback {
    private final Map<String, Level> originalLevels = new HashMap<>();
    private static final boolean LOG4J_AVAILABLE = isLog4jPresent();

    @Override
    public void beforeEach(ExtensionContext context) {
        // Skip if Log4j2 isn't in classpath
        if (!LOG4J_AVAILABLE) {
            return;
        }

        var annotation = context.getRequiredTestMethod()
                .getAnnotation(WithLogLevel.class);
        if (annotation == null) {
            annotation = context.getRequiredTestClass()
                    .getAnnotation(WithLogLevel.class);
        }

        if (annotation != null) {
            var newLevel = annotation.value();
            var loggerContext = LoggerContext.getContext(false);

            for (Class<?> clazz : annotation.classes()) {
                var loggerName = clazz.getName();
                var loggerConfig = loggerContext.getConfiguration()
                        .getLoggerConfig(loggerName);

                // Save original level
                originalLevels.put(loggerName, loggerConfig.getLevel());
                // Change log level
                Configurator.setLevel(loggerName, newLevel);
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Skip if Log4j2 isn't in classpath
        if (!LOG4J_AVAILABLE) {
            return;
        }

        // Restore original log levels after the test
        originalLevels.forEach(Configurator::setLevel);
    }

    private static boolean isLog4jPresent() {
        try {
            Class.forName("org.apache.logging.log4j.core.LoggerContext");
            return true;
        } catch (ClassNotFoundException e) {
            System.err.println("""
                Junit LogLevelExtension only work when Log4j2 \
                is present in the classpath (test scope). Your test \
                log level won't be affected.""");
            return false;
        }
    }
}
