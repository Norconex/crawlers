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
package com.norconex.crawler.core.spi;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import com.norconex.commons.lang.bean.spi.PolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.cmd.crawl.task.DocProcessorUpsertTest;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;
import com.norconex.crawler.core.junit.CrawlTestCapturer;
import com.norconex.crawler.core.mocks.cli.MockCliEventWriter;
import com.norconex.importer.response.ImporterResponseProcessor;

public class CoreTestPtProvider implements PolymorphicTypeProvider {

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {
        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();
        map.put(EventListener.class, MockCliEventWriter.class);
        map.put(EventListener.class, CrawlTestCapturer.class);
        map.put(ImporterResponseProcessor.class,
                DocProcessorUpsertTest.TestResponseProcessor.class);
        map.put(GridConnector.class, IgniteGridConnector.class);
        return map;
    }
}
