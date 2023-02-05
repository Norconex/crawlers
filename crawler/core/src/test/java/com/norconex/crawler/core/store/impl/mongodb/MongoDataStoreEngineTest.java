/* Copyright 2021-2023 Norconex Inc.
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

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.norconex.crawler.core.store.AbstractDataStoreEngineTest;
import com.norconex.crawler.core.store.DataStoreEngine;

import lombok.extern.slf4j.Slf4j;

@Testcontainers(disabledWithoutDocker = true)
@Slf4j
class MongoDataStoreEngineTest extends AbstractDataStoreEngineTest {

    @Container
    static MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:4.2.0"));

    @Override
    protected DataStoreEngine createEngine() {
        var connStr = "mongodb://"
                + mongoDBContainer.getHost() + ":"
                + mongoDBContainer.getFirstMappedPort();

        LOG.info("Creating new Mongo data store engine using: {}", connStr);
        var engine = new MongoDataStoreEngine();
        engine.setConnectionString(connStr);
        return engine;
    }
}
