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
package com.norconex.crawler.web.spi;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.bean.spi.PolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.web.cases.recovery.CrawlSessionStopper;
import com.norconex.crawler.web.cases.recovery.JVMCrasher;
import com.norconex.crawler.web.cases.recovery.TestCommitter;
import com.norconex.crawler.web.mocks.MockStartURLsProvider;

public class WebTestPtProvider implements PolymorphicTypeProvider {

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {
        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();
        map.put(EventListener.class, JVMCrasher.class);
        map.put(EventListener.class, CrawlSessionStopper.class);
        map.put(ReferencesProvider.class, MockStartURLsProvider.class);
        map.put(Committer.class, TestCommitter.class);
        return map;
    }
}
