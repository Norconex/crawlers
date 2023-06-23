/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.committer.azuresearch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;

import com.norconex.committer.azuresearch.AzureSearchMocker.Doc;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.map.Properties;

/**
 * Azure Search main tests. Because Microsoft does not offer a local instance
 * for unit testing, we simulate one using a mock server.
 *
 * @author Pascal Essiembre
 */
@ExtendWith(MockServerExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
class AzureSearchCommitterTest {

    private static final String TEST_ID = "3";
    private static final String TEST_CONTENT = "This is test content.";

    @TempDir
    static File tempDir;

    private AzureSearchMocker searchIndex;
    private int port;

    @BeforeAll
    public void beforeAll(ClientAndServer mockServer) throws IOException {
        this.searchIndex = new AzureSearchMocker(mockServer);
        this.port = mockServer.getPort();
    }

    @BeforeEach
    public void beforeEach() {
        searchIndex.clear();
    }

    @Test
    void testCommitAdd() throws Exception {
        // Add new doc to Azure SearchSearch
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
        });
        assertEquals(1, searchIndex.docCount());
        assertTestDoc(searchIndex.getDoc(0));
    }

    @Test
    void testAddWithQueueContaining2documents() throws Exception{
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
        });

        //Check that there is 2 documents in Azure Search
        Assertions.assertEquals(2, searchIndex.docCount());
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

        //Check that there are 2 documents in Azure Search
        Assertions.assertEquals(2, searchIndex.docCount());
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

        //Check that there is 1 documents in Azure Search
        Assertions.assertEquals(1, searchIndex.docCount());
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

        // Check that it's remove from Azure Search
        Assertions.assertEquals(0, searchIndex.docCount());
    }


    @Test
    void testMultiValueFields() throws Exception {
        Properties metadata = new Properties();
        String fieldname = "MULTI"; // (Azure Search saves uppercase by default)
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.getConfig().setArrayFields(fieldname);
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in Azure Search
        assertEquals(1, searchIndex.docCount());
        Doc doc = searchIndex.getDoc(0);

        // Check multi values are still there
        assertEquals(3, doc.getFieldValues(fieldname).size(),
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

    private void assertTestDoc(Doc doc) throws RetriableException {
        assertEquals(TEST_ID, doc.getKey());
        assertEquals(TEST_CONTENT, doc.getFieldValue("content"));
    }

    private AzureSearchCommitter createAzureSearchCommitter()
            throws CommitterException {
        CommitterContext ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        AzureSearchCommitter committer = new AzureSearchCommitter();
        AzureSearchCommitterConfig config = committer.getConfig();
        config.setApiKey(AzureSearchMocker.MOCK_API_KEY);
        config.setDisableDocKeyEncoding(true);
        config.setEndpoint("http://localhost:" + port);
        config.setIndexName("test");
        committer.init(ctx);
        return committer;
    }

    private AzureSearchCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        AzureSearchCommitter committer = createAzureSearchCommitter();
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    @FunctionalInterface
    private interface CommitterConsumer {
        void accept(AzureSearchCommitter c) throws Exception;
    }
}
