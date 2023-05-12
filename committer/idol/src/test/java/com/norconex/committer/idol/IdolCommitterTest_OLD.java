/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.committer.idol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.URLStreamer;
import com.norconex.commons.lang.xml.XML;

/**
 * IdolCommitter main tests.
 *
 * @author Pascal Essiembre
 */
@EnabledIfSystemProperty(named = "idol.index.url", matches = ".*")
@EnabledIfSystemProperty(named = "idol.aci.url", matches = ".*")
@TestInstance(Lifecycle.PER_CLASS)
class IdolCommitterTest_OLD {

    private static final Logger LOG = LoggerFactory.getLogger(
            IdolCommitterTest_OLD.class);

    //TODO test update/delete URL params
    //TODO test source + target mappings + other mappings

    private static final String TEST_DB = "tests";
    private static final String TEST_ID = "1";
    private static final String TEST_CONTENT = "This is test content.";

    private static final String ACI_ROOT_URL = StringUtils.appendIfMissing(
            System.getProperty("idol.aci.url"), "/");
    private static final String INDEX_ROOT_URL = StringUtils.appendIfMissing(
            System.getProperty("idol.index.url"), "/");

    @TempDir
    static File tempDir;

    @BeforeAll
    void beforeAll() throws Exception {
        indexGET("DRECREATEDBASE?DREDbName=" + TEST_DB);
    }
    @BeforeEach
    void beforeEach() throws Exception {
        indexGET("DREDELDBASE?DREDbName=" + TEST_DB);
        dreSync();
    }
    @AfterAll
    void afterAll() throws Exception {
        indexGET("DREREMOVEDBASE?DREDbName=" + TEST_DB);
    }

    @Test
    void testCommitAdd() throws Exception {
        // Add new doc to IDOL
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
        });
        List<XML> docs = getAllDocs();
        assertEquals(1, docs.size());
        assertTestDoc(docs.get(0));
    }

    @Test
    void testAddWithQueueContaining2documents() throws Exception{
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
        });

        //Check that there is 2 documents in IDOL
        Assertions.assertEquals(2, getAllDocs().size());
    }

    @Test
    void testCommitQueueWith3AddCommandAnd1DeleteCommand()
            throws Exception{

        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
            c.delete(new DeleteRequest("1", new Properties()));
            c.upsert(upsertRequest("3", "Document 3"));
        });

        //Check that there are 2 documents in IDOL
        Assertions.assertEquals(2, getAllDocs().size());
    }

    @Test
    void testCommitQueueWith3AddCommandAnd2DeleteCommand()
            throws Exception{

        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
            c.delete(new DeleteRequest("1", new Properties()));
            c.delete(new DeleteRequest("2", new Properties()));
            c.upsert(upsertRequest("3", "Document 3"));
        });

        //Check that there is 1 documents in IDOL
        Assertions.assertEquals(1, getAllDocs().size());
    }

    @Test
    void testCommitDelete() throws Exception {

        // Add a document
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
        });

        // Delete it in a new session.
        withinCommitterSession(c -> {
            c.delete(new DeleteRequest("1", new Properties()));
        });

        // Check that it's remove from IDOL
        Assertions.assertEquals(0, getAllDocs().size());
    }


    @Test
    void testMultiValueFields() throws Exception {
        Properties metadata = new Properties();
        String fieldname = "MULTI"; // (IDOL saves as uppercase by default)
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in IDOL
        List<XML> docs = getAllDocs();
        assertEquals(1, docs.size());
        XML doc = docs.get(0);

        // Check multi values are still there
        assertEquals(3, doc.getStringList(fieldname).size(),
                "Multi-value not saved properly.");
    }

    private UpsertRequest upsertRequest(String id, String content) {
        return upsertRequest(id, content, null);
    }
    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        Properties p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(id, p, content == null
                ? new NullInputStream(0) : toInputStream(content, UTF_8));
    }

    private void assertTestDoc(XML doc) throws RetriableException {
        assertEquals(TEST_ID, doc.getString("DREREFERENCE"));
        assertEquals(TEST_DB, doc.getString("DREDBNAME"));
        assertEquals(TEST_CONTENT, doc.getString("DRECONTENT").trim());
    }
    private List<XML> getAllDocs() {
        // Wait a little to give DRESYNC time to kick in.
        Sleeper.sleepMillis(500);
        XML xml = new XML(aciGET("a=List"));
        LOG.debug("IDOL getAllDocs() response: {}", xml);
        assertEquals("SUCCESS", xml.getString("response"));
        return xml.getXMLList("responsedata/hit/content/DOCUMENT");
    }

    private IdolCommitter createIdolCommitter() throws CommitterException {
        CommitterContext ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        IdolCommitter committer = new IdolCommitter();
        committer.getConfig().setUrl(INDEX_ROOT_URL);
        committer.getConfig().setDatabaseName(TEST_DB);
        committer.init(ctx);
        return committer;
    }

    private IdolCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        IdolCommitter committer = createIdolCommitter();
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        dreSync();
        return committer;
    }

    private void dreSync() {
        int id = Integer.valueOf(indexGET("/DRESYNC").trim()
                .replaceFirst("INDEXID=(\\d+)", "$1"));
        // Wait for status -1, which means "Finished".
        int cnt = 0;
        int status = 0;
        while ((status = dreGetStatus(id)) != -1 && cnt++ < 10) {
            Sleeper.sleepSeconds(1);
        }
        assertEquals(-1, status,
                "DRESYNC status not 'Fisished' after 10 seconds.");
    }
    private int dreGetStatus(int id) {
        return new XML(aciGET("a=indexergetstatus&index=" + id)).getInteger(
                "responsedata/item/status");
    }

    @FunctionalInterface
    private interface CommitterConsumer {
        void accept(IdolCommitter c) throws Exception;
    }

    private String aciGET(String command) {
        return httpGET(ACI_ROOT_URL + StringUtils.removeStart(command, "/"));
    }
    private String indexGET(String command) {
        return httpGET(INDEX_ROOT_URL + StringUtils.removeStart(command, "/"));
    }
    private String httpGET(String url) {
        LOG.debug("IDOL test request: {}", url);
        return URLStreamer.streamToString(url);
    }
}
