/* Copyright 2015 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import com.norconex.collector.core.crawler.ICrawler;
import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.crawler.event.ICrawlerEventListener;

/**
 * Crashes the JVM with no finalization, after the 7th document *fetch*.
 * In other words, the last fetch document will not be committed.
 * @author Pascal Essiembre
 */
public class JVMCrasher implements ICrawlerEventListener {

    public static final int CRASH_EXIT_VALUE = 13;
    
    private int count = 0;
    
    /**
     * Constructor.
     */
    public JVMCrasher() {
    }

    @Override
    public void crawlerEvent(ICrawler crawler, CrawlerEvent event) {
        if (CrawlerEvent.DOCUMENT_FETCHED.equals(event.getEventType())) {
            count++;
            if (count % 7 == 0) {
                Runtime.getRuntime().halt(13);
            }
        }
    }

}
