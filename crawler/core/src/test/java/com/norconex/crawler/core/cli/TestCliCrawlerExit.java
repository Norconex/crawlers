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
package com.norconex.crawler.core.cli;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import com.norconex.commons.lang.event.Event;

import lombok.Data;

@Data
public class TestCliCrawlerExit {
    private int code = -1;
    private String stdOut;
    private String stdErr;
    private final List<Event> events = new ArrayList<>();

    public boolean ok() {
        return code == 0;
    }

    public List<String> getEventNames() {
        return events.stream().map(Event::getName).toList();
    }

    public Bag<String> getEventCounts() {
        var bag = new HashBag<String>();
        events.stream().map(Event::getName).forEach(bag::add);
        return bag;
    }
}
