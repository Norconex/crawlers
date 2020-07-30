/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.recovery;

import org.junit.jupiter.api.Disabled;

import com.norconex.collector.core.store.IDataStoreEngine;

/**
 * Test that the right amount of docs are crawled after stoping
 * and starting the collector.
 * @author Pascal Essiembre
 */
@Disabled
public class StartAfterJvmCrash extends AbstractTestJvmCrash {

    @Override
    protected boolean isResuming() {
        return false;
    }
    @Override
    protected IDataStoreEngine createDataStoreEngine() {
        return null;
//        MVStoreCrawlDataStoreEngine f = new MVStoreCrawlDataStoreEngine();
//        f.setStoreDir(Paths.get("mvstore"));
//        return f;
    }
}
