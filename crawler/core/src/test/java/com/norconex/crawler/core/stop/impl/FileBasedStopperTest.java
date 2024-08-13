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
package com.norconex.crawler.core.stop.impl;

import static com.norconex.crawler.core.session.CrawlSessionEvent.CRAWLSESSION_STOP_BEGIN;
import static com.norconex.crawler.core.session.CrawlSessionEvent.CRAWLSESSION_STOP_END;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.pipeline.queue.MockQueueInitializer;

class FileBasedStopperTest {

    @TempDir
    private Path tempDir;

    @Test
    void testListenForStopRequest() throws InterruptedException {
        var sess = CoreStubber.crawlSession(
                tempDir,
                implBuilder -> {
                    var mqi = new MockQueueInitializer(
                            "ref1", "ref2", "ref3", "ref4", "ref5",
                            "ref6", "ref7", "ref8", "ref9", "ref10");
                    mqi.setAsync(true);
                    mqi.setDelay(1000);
                    implBuilder.queueInitializer(mqi);
                });
        var receiver = new FileBasedStopper();
        receiver.listenForStopRequest(sess);

        var isRunning = new MutableObject<>(Boolean.FALSE);
        var gotStopBegin = new MutableObject<>(Boolean.FALSE);
        var gotStopEnd = new MutableObject<>(Boolean.FALSE);
        TestUtil.getFirstCrawlerConfig(sess).setNumThreads(1);
        sess.getCrawlSessionConfig().addEventListener(
                ev -> {
                    if (CrawlerEvent.CRAWLER_INIT_END.equals(ev.getName())) {
                        isRunning.setValue(true);
                        var emmitter = new FileBasedStopper();
                        emmitter.fireStopRequest(sess);
                        //emmitter.destroy();
                    } else if (CRAWLSESSION_STOP_BEGIN.equals(ev.getName())) {
                        gotStopBegin.setValue(true);
                    } else  if (CRAWLSESSION_STOP_END.equals(ev.getName())) {
                        gotStopEnd.setValue(true);
                    }
                });
        sess.getService().start();


        receiver.destroy();
        assertThat(gotStopBegin.getValue()).isTrue();
        assertThat(gotStopEnd.getValue()).isTrue();
    }
}
