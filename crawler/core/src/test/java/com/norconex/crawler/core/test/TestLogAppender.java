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
package com.norconex.crawler.core.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

//TODO consider using slf4j-test instead.
public class TestLogAppender extends AbstractAppender {

    //TODO add static methods for setup/teardown

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private boolean started;

    protected TestLogAppender(String name) {
        super(
                name,
                null,
                PatternLayout.newBuilder()
                        .withPattern("%level %logger - %msg")
                        .build(),
                true,
                Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        messages.add(getLayout().toSerializable(event).toString());
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getCombined() {
        return String.join("\n", messages);
    }

    public void startCapture() {
        var ctx = (LoggerContext) LogManager.getContext(false);
        var config = ctx.getConfiguration();
        start();
        config.addAppender(this);
        var root = config.getRootLogger();
        root.addAppender(this, null, null);
        ctx.updateLoggers();
        started = true;
    }

    public void stopCapture() {
        if (!started) {
            return;
        }
        var ctx = (LoggerContext) LogManager.getContext(false);
        var config = ctx.getConfiguration();

        var root = config.getRootLogger();
        root.removeAppender(getName());

        stop();
        ctx.updateLoggers();
    }
}
