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
package com.norconex.crawler.web.session.recovery;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.crawler.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Stops the crawl session after the specified number
 * of documents to be *fetched* (default is 7).
 * The last fetched document should finish cleanly and be committed
 * despite the stop.
 */
@Slf4j
public class CrawlSessionStopper
        implements EventListener<Event>, XMLConfigurable {

    private int stopAt;
    private int count = 0;

    public CrawlSessionStopper() {
        stopAt = 7;
    }
    public CrawlSessionStopper(int crashAt) {
        stopAt = crashAt;
    }

    @Override
    public void accept(Event e) {
        if (e.is(CrawlerEvent.DOCUMENT_FETCHED)) {
            count++;
            if (count % stopAt == 0) {
                LOG.info("{} documents fetched. STOP!", count);
                ((CrawlerEvent) e)
                .getSource()
                .getCrawlSession()
                .getService()
                .stop();
            }
        }
    }
    @Override
    public void loadFromXML(XML xml) {
        stopAt = xml.getInteger("crashAt", stopAt);
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("crashAt", stopAt);
    }
}
