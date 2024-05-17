/* Copyright 2023 Norconex Inc.
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
package com.norconex.committer.amazoncloudsearch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.URLStreamer;

/**
 * AmazonCloudSearch main tests.
 *
 * @author Pascal Essiembre
 */

@Testcontainers(disabledWithoutDocker = true)
class AmazonCloudSearchCommitterTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            AmazonCloudSearchCommitterTest.class);

    //TODO test update/delete URL params
    //TODO test source + target mappings + other mappings

    private static final int CLOUDSEARCH_PORT = 15808;
    private static final String CLOUDSEARCH_NAME = "nozama-cloudsearch";
    private static final String API_DEV_DOCUMENTS = "/dev/documents";
    private static final String TEST_ID = "3";
    private static final String TEST_CONTENT = "This is test content.";

    @Container
    static DockerComposeContainer<?> container =
            new DockerComposeContainer<>(
                    new File("src/test/resources/nozama-cloudsearch.yaml"))
            .withExposedService(
                    CLOUDSEARCH_NAME,
                    CLOUDSEARCH_PORT,
                    /*
                     * Ensure nozama container gets into a state where
                     * it will accept HTTP DELETE requests
                     */
                    Wait
                        .forHttp(API_DEV_DOCUMENTS)
                        .withMethod("DELETE")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(60))
                    );

    private static String CLOUDSEARCH_ENDPOINT;

    @TempDir
    static File tempDir;

    @BeforeAll
    static void setCloudSearchEndpoint() {
        CLOUDSEARCH_ENDPOINT =
                "http://"
                + container.getServiceHost(CLOUDSEARCH_NAME, CLOUDSEARCH_PORT)
                + ":"
                + container.getServicePort(CLOUDSEARCH_NAME, CLOUDSEARCH_PORT)
                + "/";
    }

    @BeforeEach
    void beforeEach() throws Exception {
        httpDelete(API_DEV_DOCUMENTS);
    }

    @Test
    void testCommitAdd() throws Exception {
        // Add new doc to CloudSearch
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
        });
        var docs = getAllDocs();
        assertEquals(1, docs.size());
        assertTestDoc(docs.get(0));
    }

    @Test
    void testAddWithQueueContaining2documents() throws Exception{
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
        });

        //Check that there is 2 documents in CloudSearch
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

        //Check that there are 2 documents in CloudSearch
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

        //Check that there is 1 documents in CloudSearch
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

        // Check that it's remove from CloudSearch
        Assertions.assertEquals(0, getAllDocs().size());
    }


    @Test
    void testCommitDeleteWithBadIdValue() throws Exception {
        // Add a document
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1`~<", "Document 1"));
        });

        // Delete it in a new session.
        withinCommitterSession(c -> {
            c.delete(new DeleteRequest("1`~<", new Properties()));
        });

        // Check that it's remove from CloudSearch
        Assertions.assertEquals(0, getAllDocs().size());
    }

    @Test
    void testMultiValueFields() throws Exception {
        var metadata = new Properties();
        var fieldname = "MULTI"; // (CloudSearch saves as uppercase by default)
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in CloudSearch
        var docs = getAllDocs();
        assertEquals(1, docs.size());
        var doc = docs.get(0);

        // Check multi values are still there
        assertEquals(3,
                doc.getJSONObject("fields").getJSONArray("multi").length(),
                "Multi-value not saved properly.");
    }

    @Test
    void testCommitAdd_emptyServiceEndpoint_throwsException() throws Exception {
        //setup
        Exception expectedException = null;

        //execute
        try {
            withinCommitterSessionEmptyServiceEndpoint(c -> {
                c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
            });
        } catch(CommitterException e) {
            expectedException = e;
        }

        //verify
        assertThat(expectedException)
                .isNotNull()
                .hasMessage("Service endpoint is undefined.");
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

    private void assertTestDoc(JSONObject doc) throws RetriableException {
        assertEquals(TEST_ID, doc.getString("id"));
        assertEquals(TEST_CONTENT,
                doc.getJSONObject("fields").getString("content"));
    }
    private List<JSONObject> getAllDocs() {
        var response = httpGET(API_DEV_DOCUMENTS);
        LOG.debug("CloudSearch getAllDocs() response: {}", response);
        var json = new JSONObject(response);
        var jsonDocs = json.getJSONArray("documents");
        List<JSONObject> docs = new ArrayList<>();
        for (var i = 0; i < jsonDocs.length(); i++) {
            docs.add(jsonDocs.getJSONObject(i));
        }
        return docs;
    }

    private AmazonCloudSearchCommitter createCloudSearchCommitter()
            throws CommitterException {
        var ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new AmazonCloudSearchCommitter();
        committer.getConfiguration()
            .setServiceEndpoint(CLOUDSEARCH_ENDPOINT)
            .setSecretKey("dummySecretKey")
            .setAccessKey("dummyAccessKey")
            .setFixBadIds(true);
        committer.init(ctx);
        return committer;
    }

    private AmazonCloudSearchCommitter createCloudSearchCommitterWithEmptyServiceEndpoint()
            throws CommitterException {
        var ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new AmazonCloudSearchCommitter();
        committer.getConfiguration()
                .setServiceEndpoint("")
                .setSecretKey("dummySecretKey")
                .setAccessKey("dummyAccessKey")
                .setFixBadIds(true);
        committer.init(ctx);
        return committer;
    }

    private AmazonCloudSearchCommitter withinCommitterSession(
            CommitterConsumer c)
            throws CommitterException {
    	var committer = createCloudSearchCommitter();
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

    private AmazonCloudSearchCommitter withinCommitterSessionEmptyServiceEndpoint(
            CommitterConsumer c)
            throws CommitterException {
        var committer = createCloudSearchCommitterWithEmptyServiceEndpoint();
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
        void accept(AmazonCloudSearchCommitter c) throws Exception;
    }

    private String httpGET(String path) {
        var url = CLOUDSEARCH_ENDPOINT + StringUtils.removeStart(path, "/");
        LOG.debug("CloudSearch test GET request: {}", url);
        return URLStreamer.streamToString(url);
    }

    private void httpDelete(String path) throws CommitterException {
        var url = CLOUDSEARCH_ENDPOINT + StringUtils.removeStart(path, "/");
        LOG.debug("CloudSearch test DELETE request: {}", url);
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setUseCaches(false);
            con.setRequestMethod("DELETE");
            // Get the response
            var responseCode = con.getResponseCode();
            LOG.debug("Server Response Code: {}", responseCode);
            var response = IOUtils.toString(
                    con.getInputStream(), StandardCharsets.UTF_8);
            LOG.debug("Server Response Text: {}", response);
            if (!StringUtils.contains(response, "\"status\": \"ok\"")) {
                throw new CommitterException(
                        "Unexpected HTTP response: " + response);
            }
        } catch (IOException e) {
            throw new CommitterException(
                    "Cannot post content to " + url, e);
        } finally {
            con.disconnect();
        }
    }
}
