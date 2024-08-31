/* Copyright 2013-2024 Norconex Inc.
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
package com.norconex.committer.elasticsearch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.io.IoUtil;
import com.norconex.commons.lang.map.Properties;

import lombok.extern.slf4j.Slf4j;

@Testcontainers(disabledWithoutDocker = true)
@Slf4j
class ElasticsearchCommitterTest {

    private static final String TEST_ES_VERSION = "8.7.1";
    private static final String TEST_INDEX = "tests";
    private static final String TEST_ID = "1";
    private static final String TEST_CONTENT = "This is test content.";
    private static final String INDEX_ENDPOINT = "/" + TEST_INDEX + "/";
    private static final String CONTENT_FIELD =
            ElasticsearchCommitterConfig.DEFAULT_ELASTICSEARCH_CONTENT_FIELD;

    @TempDir
    static File tempDir;

    @SuppressWarnings("resource")
    @Container
    static ElasticsearchContainer container = new ElasticsearchContainer(
            DockerImageName.parse(
                    "docker.elastic.co/elasticsearch/elasticsearch")
                    .withTag(TEST_ES_VERSION))
                            .withEnv("xpack.security.enabled", "false");

    private static RestClient restClient;

    @BeforeAll
    static void beforeAll() {
        restClient = RestClient.builder(
                HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeEach
    void beforeEach() throws Exception {
        if (restClient.performRequest(new Request("HEAD", "/" + TEST_INDEX))
                .getStatusLine().getStatusCode() == 200) {
            performRequest("DELETE", "/" + TEST_INDEX);
            performRequest("POST", "_flush");
            performRequest("PUT", "/" + TEST_INDEX);
        }

    }

    @AfterAll
    static void afterAll() {
        IoUtil.closeQuietly(restClient);
    }

    @Test
    void testCommitAdd() throws Exception {
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
        });

        var doc = getDocument(TEST_ID);
        assertTrue(isFound(doc), "Not found.");
        assertTrue(hasTestContent(doc), "Bad content.");
    }

    @Test
    void testCommitDelete() throws Exception {
        // Add a document directly to ES
        var request = new Request(
                "PUT", INDEX_ENDPOINT + "_doc/" + TEST_ID);
        request.setJsonEntity(
                "{\"" + CONTENT_FIELD + "\":\"" + TEST_CONTENT + "\"}");
        restClient.performRequest(request);

        assertTrue(isFound(getDocument(TEST_ID)), "Not properly added.");

        // Queue it to be deleted
        withinCommitterSession(c -> {
            c.delete(new DeleteRequest(TEST_ID, new Properties()));
        });

        // Check that it's removed from ES
        assertFalse(isFound(getDocument(TEST_ID)), "Was not deleted.");
    }

    @Test
    void testRemoveQueuedFilesAfterAdd() throws Exception {
        // Add new doc to ES
        var esc = withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, null));
        });

        // After commit, make sure queue is emptied of all files
        assertTrue(listFiles(esc).isEmpty());
    }

    @Test
    void testRemoveQueuedFilesAfterDelete() throws Exception {
        // Delete doc from ES
        var esc = withinCommitterSession(c -> {
            c.delete(new DeleteRequest(TEST_ID, new Properties()));
        });

        // After commit, make sure queue is emptied of all files
        assertTrue(listFiles(esc).isEmpty());
    }

    @Test
    void testIdSourceFieldRemoval() throws Exception {
        // Force to use a reference field instead of the default
        // reference ID.
        var sourceIdField = "customId";
        var metadata = new Properties();
        var customIdValue = "ABC";
        metadata.set(sourceIdField, customIdValue);

        // Add new doc to ES with a difference id than the one we
        // assigned in source reference field. Set to keep that
        // field.
        withinCommitterSession(c -> {
            c.getConfiguration().setSourceIdField(sourceIdField);
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT, metadata));
        });

        // Check that it's in ES using the custom ID
        var doc = getDocument(customIdValue);
        assertTrue(isFound(doc), "Not found.");
        assertTrue(hasTestContent(doc), "Bad content.");
        assertFalse(
                hasField(doc, sourceIdField), "sourceIdField was not removed.");
    }

    @Test
    void testCustomTargetContentField() throws Exception {
        var targetContentField = "customContent";
        var metadata = new Properties();
        metadata.set(targetContentField, TEST_CONTENT);

        // Add new doc to ES
        withinCommitterSession(c -> {
            c.getConfiguration().setTargetContentField(targetContentField);
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT, metadata));
        });

        // Check that it's in ES
        var doc = getDocument(TEST_ID);
        assertTrue(isFound(doc), "Not found.");

        // Check content is available in custom content target field and
        // not in the default field
        assertEquals(
                TEST_CONTENT, getFieldValue(doc, targetContentField),
                "targetContentField was not saved.");
        assertFalse(
                hasField(doc, CONTENT_FIELD),
                "Default content field was saved.");
    }

    @Test
    void testMultiValueFields() throws Exception {
        var metadata = new Properties();
        var fieldname = "multi";
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in ES
        var doc = getDocument(TEST_ID);
        assertTrue(isFound(doc), "Not found.");

        // Check multi values are still there
        assertEquals(
                3, getFieldValues(doc, fieldname).size(),
                "Multi-value not saved properly.");
    }

    @Test
    void testDotReplacement() throws Exception {
        var metadata = new Properties();
        var fieldNameDots = "with.dots.";
        var fieldNameNoDots = "with_dots_";
        var fieldValue = "some value";
        metadata.set(fieldNameDots, fieldValue);

        withinCommitterSession(c -> {
            c.getConfiguration().setDotReplacement("_");
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in ES
        var doc = getDocument(TEST_ID);

        assertTrue(isFound(doc), "Not found.");

        // Check the dots were replaced
        assertEquals(
                fieldValue, getFieldValue(doc, fieldNameNoDots),
                "Dots not replaced.");
        assertFalse(hasField(doc, fieldNameDots), "Dots still present.");
    }

    @Test
    void testErrorsFiltering() throws Exception {
        // Should only get errors returned.
        var metadata = new Properties();

        metadata.set("date", "2014-01-01");
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("good1", null, metadata));
        });

        // Commit a mixed batch with one wrong date format
        try {
            withinCommitterSession(c -> {
                var m = new Properties();

                m.set("date", "2014-01-02");
                c.upsert(upsertRequest("good2", null, m));

                m = new Properties();
                m.set("date", "5/30/2011");
                c.upsert(upsertRequest("bad1", null, m));

                m = new Properties();
                m.set("date", "2014-01-03");
                c.upsert(upsertRequest("good3", null, m));

                m = new Properties();
                m.set("date", "5/30/2012");
                c.upsert(upsertRequest("bad2", null, m));

                m = new Properties();
                m.set("date", "2014-01-04");
                c.upsert(upsertRequest("good4", null, m));
            });
            Assertions.fail("Failed to throw exception.");
        } catch (CommitterException e) {
            assertEquals(
                    2, StringUtils.countMatches(
                            ExceptionUtil.getFormattedMessages(e),
                            "\"error\":"),
                    "Wrong error count.");
        }
    }

    @Test
    void testUpsertWithBadId_idIsFixed()
            throws CommitterException, IOException {
        //setup
        var expectdId = StringUtils.repeat("a", 501) + "!0626151616";
        var props = new Properties();
        props.add("homer", "simpson");

        var upsertReq = new UpsertRequest(
                StringUtils.repeat("a", 513),
                props,
                InputStream.nullInputStream());

        List<CommitterRequest> upsert = new ArrayList<>();
        upsert.add(upsertReq);

        //execute
        withinCommitterSession(c -> {
            c.getConfiguration().setFixBadIds(true);
            c.commitBatch(upsert.iterator());
        });

        //verify
        var response = getDocument(expectdId);
        assertThat(response.getBoolean("found")).isTrue();
    }

    private boolean hasTestContent(JSONObject doc) {
        return TEST_CONTENT.equals(getContentFieldValue(doc));
    }

    private boolean hasField(JSONObject doc, String fieldName) {
        return doc.getJSONObject("_source").has(fieldName);
    }

    private String getFieldValue(JSONObject doc, String fieldName) {
        return doc.getJSONObject("_source").getString(fieldName);
    }

    private List<String> getFieldValues(JSONObject doc, String fieldName) {
        List<String> values = new ArrayList<>();
        var array = doc.getJSONObject("_source").getJSONArray(fieldName);
        for (var i = 0; i < array.length(); i++) {
            values.add(array.getString(i));
        }
        return values;
    }

    private String getContentFieldValue(JSONObject doc) {
        return getFieldValue(doc, CONTENT_FIELD);
    }

    private boolean isFound(JSONObject doc) {
        return doc.getBoolean("found");
    }

    private JSONObject getDocument(String id) throws IOException {
        return performTypeRequest("GET", "_doc/" + id);
    }

    private JSONObject performTypeRequest(String method, String request)
            throws IOException {
        return performRequest(method, INDEX_ENDPOINT + request);
    }

    private JSONObject performRequest(String method, String endpoint)
            throws IOException {
        Response httpResponse;
        try {
            var request = new Request(method, endpoint);
            httpResponse = restClient.performRequest(request);
        } catch (ResponseException e) {
            httpResponse = e.getResponse();
        }
        var response = IOUtils.toString(
                httpResponse.getEntity().getContent(), StandardCharsets.UTF_8);
        var json = new JSONObject(response);
        LOG.info("Response status: {}", httpResponse.getStatusLine());
        LOG.debug("Response body: {}", json);
        return json;
    }

    private UpsertRequest upsertRequest(String id, String content) {
        return upsertRequest(id, content, null);
    }

    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        var p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(
                id, p, content == null
                        ? new NullInputStream(0)
                        : toInputStream(content, UTF_8));
    }

    private List<File> listFiles(ElasticsearchCommitter c) {
        return new ArrayList<>(
                FileUtils.listFiles(
                        c.getCommitterContext().getWorkDir().toFile(), null,
                        true));
    }

    protected ElasticsearchCommitter createESCommitter()
            throws CommitterException {

        var ctx = CommitterContext.builder()
                .setWorkDir(
                        new File(
                                tempDir,
                                "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new ElasticsearchCommitter();
        committer.getConfiguration().setNodes(
                List.of(container.getHttpHostAddress()));
        committer.getConfiguration().setIndexName(TEST_INDEX);
        committer.init(ctx);
        return committer;
    }

    protected ElasticsearchCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        var committer = createESCommitter();
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
    protected interface CommitterConsumer {
        void accept(ElasticsearchCommitter c) throws Exception;
    }

}
