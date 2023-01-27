/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mongodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.norconex.crawler.core.store.AbstractDataStoreEngineTest;
import com.norconex.crawler.core.store.DataStoreEngine;

@Testcontainers(disabledWithoutDocker = true)
class MongoDataStoreEngineTest extends AbstractDataStoreEngineTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(MongoDataStoreEngineTest.class);

    @Container
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:4.2.0"));

    @Override
    protected DataStoreEngine createEngine() {
        String connStr = "mongodb://"
                + mongoDBContainer.getHost() + ":"
                + mongoDBContainer.getFirstMappedPort();

        LOG.info("Creating new Mongo data store engine using: {}", connStr);
        MongoDataStoreEngine engine = new MongoDataStoreEngine();
        engine.setConnectionString(connStr);
        return engine;
    }
}
