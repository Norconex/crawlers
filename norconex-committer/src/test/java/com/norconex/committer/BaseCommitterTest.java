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
package com.norconex.committer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.committer.FileSystemQueueCommitter.QueuedAddedDocument;
import com.norconex.commons.lang.map.Properties;

@SuppressWarnings("nls")
public class BaseCommitterTest {

    class StubCommitter extends BaseCommitter {

        private static final long serialVersionUID = 5395010993071444611L;

        @Override
        protected void commitAddedDocument(QueuedAddedDocument document) 
                throws IOException {
            listCommitAdd.add(document);
        }

        @Override
        protected void commitDeletedDocument(QueuedDeletedDocument document) 
                throws IOException {
            //TODO implement me
        }

        @Override
        protected void commitComplete() {
            committed = true;
        }

        @Override
        protected void saveToXML(XMLStreamWriter writer)
                throws XMLStreamException {
            // no saving
        }

        @Override
        protected void loadFromXml(XMLConfiguration xml) {
            // no loading
        }
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BaseCommitter committer;

    private boolean committed;

    private List<QueuedAddedDocument> listCommitAdd = 
            new ArrayList<QueuedAddedDocument>();

    private Properties metadata = new Properties();

    private String defaultReference = "1";

    @Before
    public void setup() throws Exception {
        committer = new StubCommitter();
        File queue = tempFolder.newFolder("queue");
        committer.setQueueDir(queue.toString());

        committed = false;
        listCommitAdd.clear();

        metadata.clear();
        metadata.addString(
                ICommitter.DEFAULT_DOCUMENT_REFERENCE, defaultReference);
    }

    /**
     * Test no commit if not enough document
     */
    @Test
    public void testNoCommit() throws Exception {

        // Default batch size is 1000, so no commit should occur
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        assertFalse(committed);
    }

    /**
     * Test commit if there is enough document
     */
    @Test
    public void testCommit() throws Exception {

        committer.setBatchSize(1);
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        assertTrue(committed);
    }

    @Test
    public void testSetSourceAndTargetId() throws Exception {

        // Set a different source and target id
        String customSourceId = "mysourceid";
        committer.setIdSourceField(customSourceId);
        String customTargetId = "mytargetid";
        committer.setIdTargetField(customTargetId);

        // Store the source id value in metadata
        metadata.addString(customSourceId, defaultReference);

        // Add a doc (it should trigger a commit because batch size is 1)
        committer.setBatchSize(1);
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        // Get the map generated
        assertEquals(1, listCommitAdd.size());
        QueuedAddedDocument doc = listCommitAdd.get(0);
        Properties docMeta = doc.getMetadata();
        
        // Check that customTargetId was used
        assertEquals(defaultReference, docMeta.getString(customTargetId));

        // Check that customSourceId was removed (default behavior)
        assertFalse(defaultReference, docMeta.containsKey(customSourceId));
    }

    @Test
    public void testKeepSourceId() throws Exception {

        committer.setKeepIdSourceField(true);

        // Add a doc (it should trigger a commit because batch size is 1)
        committer.setBatchSize(1);
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        // Get the map generated
        assertEquals(1, listCommitAdd.size());
        QueuedAddedDocument doc = listCommitAdd.get(0);

        // Check that the source id is still there
        assertTrue(doc.getMetadata().containsKey(
                ICommitter.DEFAULT_DOCUMENT_REFERENCE));
    }
}
