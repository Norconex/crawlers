/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.state;

import java.io.Closeable;
import java.io.IOException;

import com.norconex.commons.lang.file.FileAlreadyLockedException;
import com.norconex.commons.lang.file.FileLocker;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerException;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class CrawlerState implements Closeable {
    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private FileLocker lock;

    private boolean resuming;
    private boolean stopping;
    private boolean stopped;
    private boolean terminatedProperly;
    private Crawler crawler;

    public CrawlerState(Crawler crawler) {
        this.crawler = crawler;
    }

    public boolean isExecutionLocked() {
        return lock != null && lock.isLocked();
    }

    public void init(boolean lockExecution) {
        if (lockExecution) {
            lock = createLock();
        }
    }

    @Override
    public void close() {
        unlock();
    }

    //--- Private methods ------------------------------------------------------

    private synchronized FileLocker createLock() {
        LOG.debug("Locking local crawl session execution...");
        var lck = new FileLocker(crawler.getWorkDir().resolve(".crawler-lock"));
        try {
            lck.lock();
        } catch (FileAlreadyLockedException e) {
            throw new CrawlerException("""
                    The crawler instance you are attempting to run is\s\
                    already running or otherwise executing a command. Wait\s\
                    for it to complete or stop it and try again.""");
        } catch (IOException e) {
            throw new CrawlerException(
                    "Could not create crawler execution lock.", e
            );
        }
        LOG.debug("Crawl session execution locked");
        return lck;
    }

    private synchronized void unlock() {
        if (lock == null) {
            return;
        }
        try {
            lock.unlock();
            LOG.debug("Crawler execution unlocked");
        } catch (IOException e) {
            throw new CrawlerException("Cannot unlock crawler execution.", e);
        }
        lock = null;
    }
}
