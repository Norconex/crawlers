/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.committer.solr;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.embedded.JettySolrRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;

/**
 * Base class for Solr tests requiring a running Solr server.  One Solr
 * instance is shared with all all tests in implementing class.
 * @author Pascal Essiembre
 * @author Harinder Hanjan
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractSolrTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractSolrTest.class);

    @TempDir
    static File tempDir;

    private File solrHome;
    private JettySolrRunner solrServer;
    private SolrClient solrClient;

    @BeforeAll
    private void beforeAll() throws Exception {
        solrHome = new File(tempDir, "solr-home");

        // Solr Server:
        LOG.info("Starting Solr test server...");
        LOG.info("  Solr home: {}", solrHome);

        if (solrServer != null && solrServer.isRunning()) {
            throw new IllegalStateException(
                    "Solr already running on local port "
                            + solrServer.getLocalPort());
        }
        System.setProperty(
                "solr.log.dir",
                new File(solrHome, "solr-test.log").getAbsolutePath());

        FileUtils.copyDirectory(
                new File("./src/test/resources/solr-server"),
                solrHome);

        solrServer = new JettySolrRunner(
                solrHome.getAbsolutePath(), "/solr", 0);
        solrServer.start();

        var seconds = 0;
        for (; seconds < 30; seconds++) {
            if (solrServer.isRunning()) {
                break;
            }
            LOG.info("Waiting for Solr to start...");
            Sleeper.sleepSeconds(1);
        }
        if (seconds >= 30) {
            LOG.warn(
                    "Looks like Solr is not starting on port {}. "
                            + "Please investigate.",
                    solrServer.getLocalPort());

        } else {
            LOG.info("Solr started on port {}", solrServer.getLocalPort());
        }

        // Solr Client:
        // solrServer.newClient() does not work for some reason, host is null
        solrClient = new Http2SolrClient.Builder(
                "http://localhost:" + getSolrPort() + "/solr/test").build();
    }

    @BeforeEach
    private void beforeEach() throws SolrServerException, IOException {
        solrClient.deleteByQuery("*:*");
        solrClient.commit();
    }

    @AfterAll
    private void afterAll() throws Exception {
        LOG.info("Stopping Solr.");
        solrClient.close();
        solrServer.stop();
        LOG.info("Solr stopped");
    }

    public int getSolrPort() {
        if (solrServer == null) {
            throw new IllegalStateException(
                    "Cannot get Solr port. Solr Server is not running.");
        }
        return solrServer.getLocalPort();
    }

    public File getSolrHome() {
        return solrHome;
    }

    public String getSolrTestURL() {
        return getSolrBaseURL() + "/test";
    }

    public String getSolrBaseURL() {
        if (solrServer == null) {
            throw new IllegalStateException(
                    "Cannot get Solr base URL. Solr Server is not running.");
        }
        return "http://localhost:" + getSolrPort()
                + solrServer.getBaseUrl().getPath();
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    protected SolrCommitter createSolrCommitter() throws CommitterException {
        var ctx = CommitterContext.builder()
                .setWorkDir(
                        new File(
                                getSolrHome(),
                                "" + TimeIdGenerator.next()).toPath())
                .build();
        var committer = new SolrCommitter();
        committer.getConfiguration().setSolrURL(getSolrTestURL());
        committer.getConfiguration().setUpdateUrlParam("commitWithin", "1");
        committer.init(ctx);
        return committer;
    }

    protected void withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        var committer = createSolrCommitter();
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
    }

    @FunctionalInterface
    protected interface CommitterConsumer {
        void accept(SolrCommitter c) throws Exception;
    }
}
