package com.norconex.committer.solr;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.commons.lang.map.Properties;

/**
 * To run these tests under Eclipse, you have to enable JVM assertions (-ea).
 * Under Maven, the surefire plugin enables them by default.
 * 
 * @author Pascal Dimassimo
 */
public class SolrCommitterTest extends AbstractSolrTestCase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private EmbeddedSolrServer server;

    private SolrCommitter committer;

    @Before
    public void setup() throws Exception {

        File solrHome = tempFolder.newFolder("solr");
        initCore("src/test/resources/solrconfig.xml",
                "src/test/resources/schema.xml", solrHome.toString());

        server = new EmbeddedSolrServer(h.getCoreContainer(), h.getCore()
                .getName());
        committer = new SolrCommitter(server);

        File queue = tempFolder.newFolder("queue");
        committer.setQueueDir(queue.toString());
    }

    @After
    public void teardown() {
        deleteCore();
    }

    @Test
    public void test_commit_add() throws Exception {

        String content = "hello world!";
        File file = createFile(content);

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(SolrCommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to Solr
        committer.queueAdd(id, file, metadata);

        committer.commit();

        // Check that it's in Solr
        SolrDocumentList results = queryId(id);
        assertEquals(1, results.getNumFound());
        assertEquals(id,
                results.get(0).get(SolrCommitter.DEFAULT_SOLR_TARGET_ID));
        // TODO we need to trim because of the extra white spaces returned by
        // Solr. Why is that?
        assertEquals(content,
                results.get(0).get(SolrCommitter.DEFAULT_SOLR_TARGET_CONTENT)
                        .toString().trim());
    }

    @Test
    public void test_commit_delete() throws Exception {

        // Add a document directly to Solr
        SolrInputDocument doc = new SolrInputDocument();
        String id = "1";
        doc.addField(SolrCommitter.DEFAULT_SOLR_TARGET_ID, id);
        String content = "hello world!";
        doc.addField(SolrCommitter.DEFAULT_SOLR_TARGET_CONTENT, content);
        server.add(doc);
        server.commit();

        // Queue it to be deleted
        File file = createFile(content);
        Properties metadata = new Properties();
        metadata.addString(SolrCommitter.DEFAULT_DOCUMENT_REFERENCE, id);
        committer.queueRemove(id, file, metadata);

        committer.commit();

        // Check that it's remove from Solr
        SolrDocumentList results = queryId(id);
        assertEquals(0, results.getNumFound());
    }

    private File createFile(String content) throws IOException {
        File file = tempFolder.newFile();
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
        return file;
    }

    private SolrDocumentList queryId(String id) throws SolrServerException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.set("q", String.format("%s:%s",
                SolrCommitter.DEFAULT_SOLR_TARGET_ID, id));
        QueryResponse response = server.query(solrParams);
        SolrDocumentList results = response.getResults();
        return results;
    }
}
