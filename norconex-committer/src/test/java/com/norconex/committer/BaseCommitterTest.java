package com.norconex.committer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.commons.lang.map.Properties;

public class BaseCommitterTest {

    class StubCommitter extends BaseCommitter {

        private static final long serialVersionUID = 5395010993071444611L;

        @Override
        protected void commitAdd(File file, Map<String, String> map) {
            listCommitAdd.add(map);
        }

        @Override
        protected void commitDelete(File file, String id) {
        }

        @Override
        protected void commitComplete() {
            committed = true;
        }

        @Override
        protected void saveToXML(XMLStreamWriter writer)
                throws XMLStreamException {
        }

        @Override
        protected void loadFromXml(XMLConfiguration xml) {
        }
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BaseCommitter committer;

    private boolean committed;

    private List<Map<String, String>> listCommitAdd = new ArrayList<Map<String, String>>();

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
        metadata.addString(BaseCommitter.DEFAULT_DOCUMENT_REFERENCE,
                defaultReference);
    }

    /**
     * Test no commit if not enough document
     */
    @Test
    public void test_no_commit() throws Exception {

        // Default batch size is 1000, so no commit should occur
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        assertFalse(committed);
    }

    /**
     * Test commit if there is enough document
     */
    @Test
    public void test_commit() throws Exception {

        committer.setBatchSize(1);
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        assertTrue(committed);
    }

    @Test
    public void test_set_source_and_target_id() throws Exception {

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
        Map<String, String> map = listCommitAdd.get(0);

        // Check that customTargetId was used
        assertEquals(defaultReference, map.get(customTargetId));

        // Check that customSourceId was removed (default behavior)
        assertFalse(map.containsKey(customSourceId));

        // Check that the default target id was not used
        assertFalse(map.containsKey(BaseCommitter.DEFAULT_TARGET_ID));
    }

    @Test
    public void test_keep_source_id() throws Exception {

        committer.setKeepIdSourceField(true);

        // Add a doc (it should trigger a commit because batch size is 1)
        committer.setBatchSize(1);
        committer.queueAdd(defaultReference, tempFolder.newFile(), metadata);

        // Get the map generated
        assertEquals(1, listCommitAdd.size());
        Map<String, String> map = listCommitAdd.get(0);

        // Check that the source id is still there
        assertTrue(map.containsKey(BaseCommitter.DEFAULT_DOCUMENT_REFERENCE));
    }
}
