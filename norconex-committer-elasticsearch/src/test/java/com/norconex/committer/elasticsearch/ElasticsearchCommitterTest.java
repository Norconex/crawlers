/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Committer.
 * 
 * Norconex Committer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Committer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Committer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.elasticsearch;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.map.Properties;

public class ElasticsearchCommitterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ElasticsearchCommitter committer;

    private Client client;

    private String indexName = "crawl";

    private String typeName = "page";

    private File queue;

    @Before
    public void setup() throws Exception {

        // Create a local client
        Node node = nodeBuilder().local(true).node();
        client = node.client();

        committer = new ElasticsearchCommitter(new IClientFactory() {
            @Override
            public Client createClient(ElasticsearchCommitter committer) {
                return client;
            }
        });

        committer.setIndexName(indexName);
        committer.setTypeName(typeName);

        queue = tempFolder.newFolder("queue");
        committer.setQueueDir(queue.toString());
    }

    @Test
    public void testCommitAdd() throws Exception {
        String content = "hello world!";
        File file = tempFolder.newFile();
        FileUtils.write(file, content);

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to ES
        committer.queueAdd(id, file, metadata);

        committer.commit();

        // Check that it's in ES
        GetResponse response = client.prepareGet(indexName, typeName, id)
                .execute().actionGet();
        assertTrue(response.isExists());
        // Check content
        assertEquals(
                content,
                response.getSource().get(
                        ElasticsearchCommitter.DEFAULT_ES_CONTENT_FIELD));
        // Check id field is removed
        assertFalse(response.getSource().containsKey(
                ICommitter.DEFAULT_DOCUMENT_REFERENCE));
    }

    @Test
    public void testCommitDelete() throws Exception {

        // Add a document directly to ES
        IndexRequestBuilder request = client.prepareIndex(indexName, typeName);
        String id = "1";
        request.setId(id);
        request.setSource("content", "hello world!");
        request.execute();

        // Queue it to be deleted
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);
        committer.queueRemove(id, tempFolder.newFile(), metadata);

        committer.commit();

        // Check that it's removed from ES
        GetResponse response = client.prepareGet(indexName, typeName, id)
                .execute().actionGet();
        assertFalse(response.isExists());
    }

    @Test
    public void testRemoveQueuedFilesAfterAdd() throws Exception {

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to ES
        committer.queueAdd(id, tempFolder.newFile(), metadata);
        committer.commit();

        // After commit, make sure queue is emptied of all files
        assertTrue(FileUtils.listFiles(queue, null, true).isEmpty());
    }

    @Test
    public void testRemoveQueuedFilesAfterDelete() throws Exception {

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to ES
        committer.queueRemove(id, tempFolder.newFile(), metadata);
        committer.commit();

        // After commit, make sure queue is emptied of all files
        assertTrue(FileUtils.listFiles(queue, null, true).isEmpty());
    }

    @Test
    public void testUnsupportedIdTargetField() throws Exception {

        String xml = 
                "<committer><idTargetField>newid</idTargetField></committer>";
        XMLConfiguration config = ConfigurationLoader.loadXML(new StringReader(
                xml));
        try {
            committer.loadFromXml(config);
            fail("Expected exception because idTargetField is not supported");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testKeepIdSourceField() throws Exception {

        String id = "1";
        Properties metadata = new Properties();
        metadata.addString(ICommitter.DEFAULT_DOCUMENT_REFERENCE, id);

        // Add new doc to ES
        committer.setKeepContentSourceField(true);
        committer.queueAdd(id, tempFolder.newFile(), metadata);

        committer.commit();

        // Check that it's in ES
        GetResponse response = client.prepareGet(indexName, typeName, id)
                .execute().actionGet();
        assertTrue(response.isExists());
        // Check id field is kept
        assertFalse(response.getSource().containsKey(
                ICommitter.DEFAULT_DOCUMENT_REFERENCE));

    }

    @Test
    public void testWriteRead() throws Exception {
        committer.setQueueDir("my-queue-dir");
        committer.setContentSourceField("contentSourceField");
        committer.setContentTargetField("contentTargetField");
        committer.setIdSourceField("idField");
        committer.setKeepContentSourceField(true);
        committer.setKeepIdSourceField(false);
        committer.setBatchSize(10);
        committer.setElasticsearchBatchSize(1);
        committer.setClusterName("my-cluster");
        committer.setIndexName("my-inxed");
        committer.setTypeName("my-type");

        ConfigurationUtil.assertWriteRead(committer);
    }

}
