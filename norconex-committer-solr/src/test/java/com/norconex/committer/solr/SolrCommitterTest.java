package com.norconex.committer.solr;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.solr.client.solrj.SolrServer;
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

import com.norconex.committer.ICommitter;
import com.norconex.committer.solr.SolrCommitter.ISolrServerFactory;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.map.Properties;

/**
 * To run these tests under Eclipse, you have to enable JVM assertions (-ea).
 * Under Maven, the surefire plugin enables them by default.
 * 
 * @author Pascal Dimassimo
 */
public class SolrCommitterTest extends AbstractSolrTestCase {

    //TODO test update/delete URL params
    
    static {
        ClassLoader loader = SolrCommitterTest.class.getClassLoader();
        loader.setPackageAssertionStatus("org.apache.solr", true);
        loader.setPackageAssertionStatus("org.apache.lucene", true);
    }
    
    
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
        
        committer = new SolrCommitter(new ISolrServerFactory() {
            private static final long serialVersionUID = 4648990433469043210L;
            @Override
            public SolrServer createSolrServer(SolrCommitter solrCommitter) {
                return server;
            }
        });

        File queue = tempFolder.newFolder("queue");
        committer.setQueueDir(queue.toString());
    }

    @After
    public void teardown() {
        deleteCore();
    }

    @Test
    public void testCommitAdd() throws Exception {

        String content = "hello world!";
        File file = createFile(content);

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to Solr
        committer.queueAdd(id, file, metadata);

        committer.commit();

        // Check that it's in Solr
        SolrDocumentList results = queryId(id);
        assertEquals(1, results.getNumFound());
        assertEquals(id,
                results.get(0).get(SolrCommitter.DEFAULT_SOLR_ID_FIELD));
        // TODO we need to trim because of the extra white spaces returned by
        // Solr. Why is that?
        assertEquals(content, results.get(0).get(
                SolrCommitter.DEFAULT_SOLR_CONTENT_FIELD).toString().trim());
    }

    @Test
    public void testCommitDelete() throws Exception {

        // Add a document directly to Solr
        SolrInputDocument doc = new SolrInputDocument();
        String id = "1";
        doc.addField(SolrCommitter.DEFAULT_SOLR_ID_FIELD, id);
        String content = "hello world!";
        doc.addField(SolrCommitter.DEFAULT_SOLR_CONTENT_FIELD, content);
        server.add(doc);
        server.commit();

        // Queue it to be deleted
        File file = createFile(content);
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);
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
                SolrCommitter.DEFAULT_SOLR_ID_FIELD, id));
        QueryResponse response = server.query(solrParams);
        SolrDocumentList results = response.getResults();
        return results;
    }
    
    @Test
    public void testWriteRead() throws IOException {
        SolrCommitter outCommitter = new SolrCommitter();
        outCommitter.setQueueDir("C:\\FakeTestDirectory\\");
        outCommitter.setContentSourceField("contentSourceField");
        outCommitter.setContentTargetField("contentTargetField");
        outCommitter.setIdSourceField("idTargetField");
        outCommitter.setIdTargetField("idTargetField");
        outCommitter.setKeepContentSourceField(true);
        outCommitter.setKeepIdSourceField(false);
        outCommitter.setBatchSize(100);
        outCommitter.setSolrBatchSize(50);
        outCommitter.setSolrURL("http://solrurl.com/test");
        outCommitter.setUpdateUrlParam("uparam1", "uvalue1");
        outCommitter.setUpdateUrlParam("uparam2", "uvalue2");
        outCommitter.setDeleteUrlParam("dparam1", "dvalue1");
        outCommitter.setDeleteUrlParam("dparam2", "dvalue2");
        outCommitter.setSolrBatchSize(50);
        System.out.println("Writing/Reading this: " + outCommitter);
        ConfigurationUtil.assertWriteRead(outCommitter);
    }
}
