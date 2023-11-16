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
package com.norconex.committer.azurecognitivesearch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
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

import com.norconex.committer.azurecognitivesearch.AzureSearchMocker.Doc;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.security.Credentials;

/**
 * Azure Search main tests. Because Microsoft does not offer a local instance
 * for unit testing, we simulate one using a mock server.
 *
 * @author Pascal Essiembre
 * @author Harinder Hanjan
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
        searchIndex = new AzureSearchMocker(mockServer);
        port = mockServer.getPort();
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
    void testCommitAdd_keyStartsWithUnderscore_throwsException()
            throws Exception {
        // setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession(c -> {
                c.upsert(upsertRequest("_" + TEST_ID, TEST_CONTENT));
            });
        } catch(Exception e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
        .isNotNull()
        .isInstanceOf(CommitterException.class)
        .hasRootCauseMessage("Document key cannot start with an underscore "
                + "character: _3");
    }

    @Test
    void testCommitAdd_keyWithInvalidChars_throwsException()
            throws Exception {
        // setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession(c -> {
                c.upsert(upsertRequest(TEST_ID + "@$", TEST_CONTENT));
            });
        } catch(Exception e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
        .isNotNull()
        .isInstanceOf(CommitterException.class)
        .hasRootCauseMessage("""
        	Document key cannot have one or more\s\
        	characters other than letters, numbers, dashes,\s\
        	underscores, and equal signs: 3@$""");
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
        var metadata = new Properties();
        var fieldname = "MULTI"; // (Azure Search saves uppercase by default)
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.getConfiguration().setArrayFields(fieldname);
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in Azure Search
        assertEquals(1, searchIndex.docCount());
        var doc = searchIndex.getDoc(0);

        // Check multi values are still there
        assertEquals(3, doc.getFieldValues(fieldname).size(),
                "Multi-value not saved properly.");
    }

    @Test
    void testAdd_FieldNameStartsWithAzureSearch_ExceptionThrown()
            throws CommitterException {
        //setup
        var metadata = new Properties();
        var myField = "azureSearchField";
        metadata.set(myField, "1");
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession(c -> {
                c.upsert(upsertRequest(TEST_ID, "doc content", metadata));
            });
        } catch(CommitterException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(CommitterException.class)
            .hasRootCauseMessage("""
                    Document field cannot begin with "azureSearch": azureSearchField""");
    }

    @Test
    void testAdd_FieldNameWithInvalidChars_ExceptionThrown()
            throws CommitterException {
        //setup
        var metadata = new Properties();
        var myField = "myField@!";
        metadata.set(myField, "1");
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession(c -> {
                c.upsert(upsertRequest(TEST_ID, "doc content", metadata));
            });
        } catch(CommitterException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(CommitterException.class)
            .hasRootCauseMessage("""
            	Document field cannot have one or more\s\
            	characters other than letters, numbers and underscores:\s\
            	myField@!""");
    }

    @Test
    void testAdd_FieldNameLengthIs129_ExceptionThrown()
            throws CommitterException {
        //setup
        var metadata = new Properties();
        var myField = StringUtils.repeat("a", 129);
        metadata.set(myField, "1");
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession(c -> {
                c.upsert(upsertRequest(TEST_ID, "doc content", metadata));
            });
        } catch(CommitterException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(CommitterException.class)
            .hasRootCauseMessage("Document field cannot be "
                    + "longer than 128 characters: " + myField);
    }

    @Test
    void testAdd_emptyEndpoint_ExceptionThrown()
            throws CommitterException {
        //setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession_emptyEndpoint(c -> {
                c.upsert(upsertRequest(TEST_ID, "content", new Properties()));
            });
        } catch(IllegalArgumentException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(("Endpoint is undefined."));
    }

    @Test
    void testAdd_emptyApiKey_ExceptionThrown()
            throws CommitterException {
        //setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession_emptyApiKey(c -> {
                c.upsert(upsertRequest(TEST_ID, "content", new Properties()));
            });
        } catch(IllegalArgumentException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(("API admin key is undefined."));
    }

    @Test
    void testAdd_emptyIndexName_ExceptionThrown()
            throws CommitterException {
        //setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSession_emptyIndexName(c -> {
                c.upsert(upsertRequest(TEST_ID, "content", new Properties()));
            });
        } catch(IllegalArgumentException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
            .isNotNull()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(("Index name is undefined."));
    }

    private UpsertRequest upsertRequest(String id, String content) {
        return upsertRequest(id, content, null);
    }
    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        var p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(id, p, content == null
                ? new NullInputStream(0) : toInputStream(content, UTF_8));
    }

    private void assertTestDoc(Doc doc) throws RetriableException {
        assertEquals(TEST_ID, doc.getKey());
        assertEquals(TEST_CONTENT, doc.getFieldValue("content"));
    }

    private CommitterContext createCommitterContext() {
        return CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
    }

    private AzureSearchCommitter createAzureSearchCommitter()
            throws CommitterException {
        var committer = new AzureSearchCommitter();
        var config = committer.getConfiguration();
        config.setApiKey(AzureSearchMocker.MOCK_API_KEY);
        config.setDisableDocKeyEncoding(true);
        config.setEndpoint("http://localhost:" + port);
        config.setIndexName("test");
        config.getProxySettings().setHost(new Host("localhost", port));
        config.getProxySettings().setCredentials(
                new Credentials("Homer", "simpson"));
        return committer;
    }

    private AzureSearchCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        var committer = createAzureSearchCommitter();
        committer.init(createCommitterContext());

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

    private AzureSearchCommitter withinCommitterSession_emptyEndpoint(
            CommitterConsumer c) throws CommitterException {
        return withinCommitterSession(c, ConfigOptions.EmptyEndpoint);
    }

    private AzureSearchCommitter withinCommitterSession_emptyApiKey(
            CommitterConsumer c) throws CommitterException {
        return withinCommitterSession(c, ConfigOptions.EmptyApiKey);
    }

    private AzureSearchCommitter withinCommitterSession_emptyIndexName(
            CommitterConsumer c) throws CommitterException {
        return withinCommitterSession(c, ConfigOptions.EmptyIndexName);
    }

    private AzureSearchCommitter withinCommitterSession(
            CommitterConsumer c,
            ConfigOptions options) throws CommitterException {
        var committer = createAzureSearchCommitter();

        switch (options) {
            case EmptyEndpoint:
                committer.getConfiguration().setEndpoint("");
            case EmptyApiKey:
                committer.getConfiguration().setApiKey("");
            case EmptyIndexName:
                committer.getConfiguration().setIndexName("");
        }

        committer.init(createCommitterContext());

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

    private enum ConfigOptions {
        EmptyEndpoint, EmptyApiKey, EmptyIndexName
    }
}
