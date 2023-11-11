/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.core.TestUtil;

import lombok.extern.slf4j.Slf4j;

// Uses inheritance instead of parameterized test because we need to
// disable some store engine in some environments and report them as skipped.
@Slf4j
public abstract class AbstractDataStoreEngineTest {

    protected static final String TEST_STORE_NAME = "testStore";

    @TempDir
    private Path tempDir;

    private TestObject obj;

    public Path getTempDir() {
        return tempDir;
    }

    @BeforeEach
    protected void beforeEach() throws IOException {
        // Test pojo record
        obj = new TestObject();
        obj.setReference("areference");
        obj.setCount(66);
        obj.setContentChecksum("checksumvalue");
        obj.setParentRootReference("parentReference");
        obj.setContentType(ContentType.TEXT);
        obj.setValid(true);

        // Delete any data so tests start on a clean slate.
        inNewStoreSession(store -> {
            store.clear();
        });
        inNewStoreEngineSession(engine -> {
            engine.clean();
        });
    }

    protected abstract DataStoreEngine createEngine();

    @Test
    void testStoreFind() {
        savePojo(obj);
        inNewStoreSession(store -> {
            var newPojo = store.find("areference").get();
            Assertions.assertEquals(obj, newPojo);
        });
    }

    @Test
    void testStoreFindFirst() {
        savePojo(obj);
        var obj2 = new TestObject("breference", 67, "blah", "ipsum");
        savePojo(obj2);
        inNewStoreSession(store -> {
            var newPojo = store.findFirst().get();
            Assertions.assertEquals(obj, newPojo);
        });
    }

    @Test
    void testStoreForEach() {
        // Saving two entries, make sure they are retreived
        savePojo(obj);
        var obj2 = new TestObject("breference", 67, "blah", "ipsum");
        savePojo(obj2);
        inNewStoreSession(store -> {
            store.forEach((k, v) -> {
                Assertions.assertTrue(EqualsUtil.equalsAny(v, obj, obj2));
                return true;
            });
        });
    }

    @Test
    void testStoreExists() {
        savePojo(obj);
        inNewStoreSession(store -> {
            Assertions.assertTrue(store.exists("areference"));
            Assertions.assertFalse(store.exists("breference"));
        });
    }

    @Test
    void testStoreCount() {
        savePojo(obj);
        // each test start with 1
        inNewStoreSession(store -> {
            Assertions.assertEquals(1, store.count());
        });
        // add another one and try again
        obj.setReference("breference");
        savePojo(obj);
        inNewStoreSession(store -> {
            Assertions.assertEquals(2, store.count());
        });
    }

    @Test
    void testStoreDelete() {
        savePojo(obj);
        inNewStoreSession(store -> {
            Assertions.assertEquals(1, store.count());
            Assertions.assertTrue(store.delete(obj.getReference()));
        });
        inNewStoreSession(store -> {
            Assertions.assertEquals(0, store.count());
        });
    }

    @Test
    void testStoreDeleteFirst() {
        savePojo(obj);

        inNewStoreSession(store -> {
            Assertions.assertEquals(1, store.count());
            Assertions.assertTrue(store.deleteFirst().isPresent());
        });
        inNewStoreSession(store -> {
            Assertions.assertEquals(0, store.count());
        });
    }

    @Test
    void testEngineRenameStore() {
        inNewStoreEngineSession(engine -> {
            DataStore<TestObject> store =
                    engine.openStore(TEST_STORE_NAME, TestObject.class);
            engine.renameStore(store, "blah");
            assertThat(store.getName()).isEqualTo("blah");
        });
    }

    @Test
    void testStoreModify() {
        // 1st save:
        savePojo(obj);
        // 2nd save:
        obj.setCount(67);
        obj.setContentChecksum("newVal2");
        savePojo(obj);
        // 3rd save:
        obj.setCount(67);
        obj.setContentChecksum("newVal3");
        savePojo(obj);

        inNewStoreSession(store -> {
            Assertions.assertEquals(1, store.count());
        });
        inNewStoreSession(store -> {
            Assertions.assertEquals("newVal3",
                    store.find("areference").get().getContentChecksum());
        });
    }

    @Test
    void testStoreInstanceModifyId() {
        // 1st save:
        savePojo(obj);
        // 2nd save:
        obj.setReference("breference");
        obj.setCount(67);
        obj.setContentChecksum("newVal");
        savePojo(obj);
        // 3rd save:
        obj.setReference("creference");
        obj.setCount(67);
        obj.setContentChecksum("newVal");
        savePojo(obj);

        inNewStoreSession(store -> {
            Assertions.assertEquals(3, store.count());
        });
        inNewStoreSession(store -> {
            Assertions.assertEquals("checksumvalue",
                    store.find("areference").get().getContentChecksum());
            Assertions.assertEquals("newVal",
                    store.find("breference").get().getContentChecksum());
            Assertions.assertEquals("newVal",
                    store.find("creference").get().getContentChecksum());
        });
    }

    @Test
    void testStoreClear() {
        savePojo(obj);

        // add 2nd:
        obj.setReference("breference");
        obj.setCount(67);
        savePojo(obj);

        inNewStoreSession(store -> {
            Assertions.assertEquals(2, store.count());
            store.clear();
        });
        inNewStoreSession(store -> {
            Assertions.assertEquals(0, store.count());
        });
    }

    @Test
    void testEngineClean() {
        savePojo(obj);
        inNewStoreEngineSession(engine -> {
            engine.openStore("storeA", TestObject.class);
            engine.openStore("storeB", TestObject.class);
            assertThat(engine.getStoreNames())
                .map(String::toUpperCase)
                .containsExactlyInAnyOrder(
                        "ACTIVE",
                        "QUEUED",
                        "TESTSTORE",
                        "STOREB",
                        "STOREA",
                        "PROCESSED",
                        "CACHED");
            engine.clean();
            assertThat(engine.getStoreNames()).isEmpty();
        });
    }

    @Test
    void testEngineWriteRead() {
        var engine = createEngine();
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(engine));
    }

    private void savePojo(TestObject testPojo) {
        inNewStoreSession(store -> {
            store.save(testPojo.getReference(), testPojo);
        });
    }

    private void inNewStoreEngineSession(Consumer<DataStoreEngine> c) {
        TestUtil.withinInitializedCrawler(
                tempDir,
                crawler -> {
                    LOG.debug("Start data store engine test...");
                    c.accept(crawler.getDataStoreEngine());
                    LOG.debug("Data store test engine done.");
                },
                cfg -> cfg.setDataStoreEngine(createEngine()));
    }

    private void inNewStoreSession(Consumer<DataStore<TestObject>> c) {
        TestUtil.withinInitializedCrawler(
                tempDir,
                crawler -> {
                    LOG.debug("Start data store test...");
                    try (DataStore<TestObject> store =
                            crawler.getDataStoreEngine().openStore(
                                    TEST_STORE_NAME, TestObject.class)) {
                        c.accept(store);
                    }
                    LOG.debug("Data store test done.");
                },
                cfg -> cfg.setDataStoreEngine(createEngine()));
    }
}
