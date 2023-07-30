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
package com.norconex.crawler.server.api.feature.crawl.model;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Matches specific events to be returned, along with matching event properties.
 * If no properties are specified, a minimal set of default properties
 * are sent with each event.
 */
@Schema(name="CrawlEventMatcher")
@Data
public class CrawlEventMatcher {
    /**
     * Regular expression matching one or more event names.
     */
    private String name;
    /**
     * Indexed property names on the event object for properties to return.
     */
    private final List<String> properties = new ArrayList<>();
}