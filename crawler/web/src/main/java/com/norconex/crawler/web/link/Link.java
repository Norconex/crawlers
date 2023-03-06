/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.web.link;

import org.apache.commons.lang3.builder.CompareToBuilder;

import com.norconex.commons.lang.map.Properties;

import lombok.Data;

/**
 * Represents a link extracted from a document.  A link is typically a URL,
 * with optional metadata associated with it, such as the referrer, text and
 * markup tag used to hold the URL.
 */
@Data
public class Link implements Comparable<Link> {

    private final String url;
    private String referrer;
    private final Properties metadata = new Properties();

    public Link(String url) {
        this.url = url;
    }

    public void setMetadata(Properties data) {
        metadata.putAll(data);
    }

    @Override
    public int compareTo(final Link other) {
        return new CompareToBuilder().append(url, other.url).toComparison();
    }
}
