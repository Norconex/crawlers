/* Copyright 2015-2023 Norconex Inc.
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

/**
 * Crashes the JVM with no finalization, after the specified number
 * of documents to be *fetched* (default is 7).
 * That is, the last fetch document will not be committed.
 * Process return value after crash is 13.
 */
public class JVMCrasher implements EventListener<Event>, XMLConfigurable {

    public static final int CRASH_EXIT_VALUE = 13;

    private int crashAt;
    private int count = 0;

    public JVMCrasher() {
        crashAt = 7;
    }
    public JVMCrasher(int crashAt) {
        this.crashAt = crashAt;
    }

    @Override
    public void accept(Event e) {
        if (e.is(CrawlerEvent.DOCUMENT_FETCHED)) {
            count++;
            if (count % crashAt == 0) {
                System.err.println(count + " documents fetched. CRASH!");
                System.err.flush();
                Runtime.getRuntime().halt(13);
            }
        }
    }
    @Override
    public void loadFromXML(XML xml) {
        crashAt = xml.getInteger("crashAt", crashAt);
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("crashAt", crashAt);
    }
}
