/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.stop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.stop.impl.FileBasedStopper;
import com.norconex.crawler.core.stubs.CrawlerStubs;

class FileBasedStopperTest {

    @TempDir
    private Path tempDir;

    @Test
    void testListenForStopRequest() throws InterruptedException {
        var crawler = CrawlerStubs.memoryCrawler(tempDir, cfg -> {
            cfg.setStartReferences(List.of(
                    "ref1", "ref2", "ref3", "ref4", "ref5",
                    "ref6", "ref7", "ref8", "ref9", "ref10"));
            cfg.setStartReferencesAsync(true);
        });

        var receiver = new FileBasedStopper();
        var emmitter = new FileBasedStopper();

        var isRunning = new MutableObject<>(Boolean.FALSE);
        var gotStopBegin = new MutableObject<>(Boolean.FALSE);
        var gotStopEnd = new MutableObject<>(Boolean.FALSE);


        crawler.getConfiguration().addEventListener(
            ev -> {
                if (CrawlerEvent.CRAWLER_INIT_END.equals(ev.getName())) {
                    receiver.listenForStopRequest(crawler);
                    isRunning.setValue(true);
                } else if (CrawlerEvent.CRAWLER_RUN_BEGIN.equals(
                        ev.getName())) {
                    emmitter.fireStopRequest(crawler);
                } else if (CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN.equals(
                        ev.getName())) {
                    Sleeper.sleepMillis(1000);
                } else if (CrawlerEvent.CRAWLER_STOP_BEGIN.equals(
                        ev.getName())) {
                    gotStopBegin.setValue(true);
                } else  if (CrawlerEvent.CRAWLER_STOP_END.equals(
                        ev.getName())) {
                    gotStopEnd.setValue(true);
                }
            });
        crawler.start();


        receiver.destroy();
        assertThat(gotStopBegin.getValue()).isTrue();
        assertThat(gotStopEnd.getValue()).isTrue();
    }
}
