package com.norconex.committer;
import java.io.File;
import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtils;


public class FileSystemCommitterTest {

    private File tempFile;
    
    @Before
    public void setUp() throws Exception {
        tempFile = File.createTempFile("FileSystemCommitterTest", ".xml");
    }

    @After
    public void tearDown() throws Exception {
        tempFile.delete();
    }

    @Test
    public void testWriteRead() throws IOException, ConfigurationException {

        FileSystemCommitter outCommitter = new FileSystemCommitter();
        outCommitter.setDirectory("C:\\FakeTestDirectory\\");
        System.out.println("Writing/Reading this: " + outCommitter);
        ConfigurationUtils.assertWriteRead(outCommitter);
    }

}
