package com.norconex.importer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.norconex.commons.lang.map.Properties;

public class ImportHandlerTest {

    private Importer importer;
    private Properties metadata;

    @Before
    public void setUp() throws Exception {
        importer = TestUtil.getTestConfigImporter();
        metadata = new Properties();
    }

    @After
    public void tearDown() throws Exception {
        importer = null;
        metadata = null;
    }
    
    @Test
    public void testHandlers() throws IOException {
        FileInputStream is = new FileInputStream(TestUtil.getAliceHtmlFile());
        StringWriter writer = new StringWriter();
        importer.importDocument(is, writer, metadata);
        is.close();

        // Test Constant
        Assert.assertEquals(metadata.getString("Author"), "Lewis Carroll");
    }
}
