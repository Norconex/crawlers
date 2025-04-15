/* Copyright 2024-2025 Norconex Inc.
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

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.bean.spi.BasePolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.web.cases.recovery.JVMCrasher;
import com.norconex.crawler.web.cases.recovery.TestCommitter;
import com.norconex.crawler.web.cases.recovery.ThrowingEventListener;
import com.norconex.crawler.web.mocks.MockStartURLsProvider;

public class WebTestPtProvider extends BasePolymorphicTypeProvider {

    @Override
    protected void register(Registry r) {
        r.add(EventListener.class,
                JVMCrasher.class, ThrowingEventListener.class);
        r.add(ReferencesProvider.class, MockStartURLsProvider.class);
        r.add(Committer.class, TestCommitter.class);

    }
}
