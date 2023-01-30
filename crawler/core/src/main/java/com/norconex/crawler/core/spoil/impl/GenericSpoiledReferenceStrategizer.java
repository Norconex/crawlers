/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.crawler.core.spoil.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategy;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Generic implementation of {@link SpoiledReferenceStrategizer} that
 * offers a simple mapping between the crawl state of references that have
 * turned "bad" and the strategy to adopt for each.
 * Whenever a crawl state does not have a strategy associated, the fall-back
 * strategy is used (default being <code>DELETE</code>).
 * </p>
 * <p>
 * The mappings defined by default are as follow:
 * </p>
 *
 * <table border="1" style="width:300px;" summary="Default mappings">
 *   <tr><td><b>Crawl state</b></td><td><b>Strategy</b></td></tr>
 *   <tr><td>NOT_FOUND</td><td>DELETE</td></tr>
 *   <tr><td>BAD_STATUS</td><td>GRACE_ONCE</td></tr>
 *   <tr><td>ERROR</td><td>GRACE_ONCE</td></tr>
 * </table>
 *
 * {@nx.xml.usage
 * <spoiledReferenceStrategizer
 *     class="com.norconex.crawler.core.spoil.impl.GenericSpoiledReferenceStrategizer"
 *     fallbackStrategy="[DELETE|GRACE_ONCE|IGNORE]">
 *   <mapping state="(any crawl state)" strategy="[DELETE|GRACE_ONCE|IGNORE]" />
 *   (repeat mapping tag as needed)
 * </spoiledReferenceStrategizer>
 * }
 *
 * {@nx.xml.example
 * <spoiledReferenceStrategizer class="GenericSpoiledReferenceStrategizer">
 *   <mapping state="NOT_FOUND" strategy="DELETE" />
 *   <mapping state="BAD_STATUS" strategy="DELETE" />
 *   <mapping state="ERROR" strategy="IGNORE" />
 * </spoiledReferenceStrategizer>
 * }
 * <p>
 * The above example indicates we should ignore (do nothing) errors processing
 * documents, and send a deletion request if they are not found or have
 * resulted in a bad status.
 * </p>
 */
@EqualsAndHashCode
@ToString
public class GenericSpoiledReferenceStrategizer implements
        SpoiledReferenceStrategizer, XMLConfigurable {

    public static final SpoiledReferenceStrategy DEFAULT_FALLBACK_STRATEGY =
            SpoiledReferenceStrategy.DELETE;

    private final Map<CrawlDocState, SpoiledReferenceStrategy> mappings =
            new HashMap<>();
    private SpoiledReferenceStrategy fallbackStrategy =
            DEFAULT_FALLBACK_STRATEGY;

    public GenericSpoiledReferenceStrategizer() {
        // store default mappings
        mappings.put(CrawlDocState.NOT_FOUND, SpoiledReferenceStrategy.DELETE);
        mappings.put(CrawlDocState.BAD_STATUS,
                SpoiledReferenceStrategy.GRACE_ONCE);
        mappings.put(CrawlDocState.ERROR, SpoiledReferenceStrategy.GRACE_ONCE);
    }

    @Override
    public SpoiledReferenceStrategy resolveSpoiledReferenceStrategy(
            String reference, CrawlDocState state) {

        var strategy = mappings.get(state);
        if (strategy == null) {
            strategy = getFallbackStrategy();
        }
        if (strategy == null) {
            strategy = DEFAULT_FALLBACK_STRATEGY;
        }
        return strategy;
    }

    public SpoiledReferenceStrategy getFallbackStrategy() {
        return fallbackStrategy;
    }
    public void setFallbackStrategy(SpoiledReferenceStrategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }

    public void addMapping(
            CrawlDocState state, SpoiledReferenceStrategy strategy) {
        mappings.put(state, strategy);
    }

    @Override
    public void loadFromXML(XML xml) {
        var fallback = xml.getEnum(
                "@fallbackStrategy",
                SpoiledReferenceStrategy.class, fallbackStrategy);
        if (fallback != null) {
            setFallbackStrategy(fallback);
        }

        for (XML node : xml.getXMLList("mapping")) {
            var attribState = node.getString("@state", null);
            var strategy = node.getEnum(
                    "@strategy", SpoiledReferenceStrategy.class);
            if (StringUtils.isBlank(attribState) || strategy == null) {
                continue;
            }
            var state = CrawlDocState.valueOf(attribState);
            addMapping(state, strategy);
        }
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("fallbackStrategy", getFallbackStrategy());
        for (Entry<CrawlDocState, SpoiledReferenceStrategy> entry :
                mappings.entrySet()) {
            xml.addElement("mapping")
                    .setAttribute("state", entry.getKey())
                    .setAttribute("strategy", entry.getValue());
        }
    }
}
