package com.norconex.importer.transformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;
import com.norconex.importer.TestUtil;

public class StripBetweenTransformerTest {

    @Test
    public void testTransformTextDocument() throws IOException {
        StripBetweenTransformer t = new StripBetweenTransformer();
        t.addStripEndpoints("<h2>", "</h2>");
        t.addStripEndpoints("<P>", "</P>");
        t.addStripEndpoints("<head>", "</hEad>");
        t.addStripEndpoints("<Pre>", "</prE>");
        t.setCaseSensitive(false);
        t.setInclusive(true);
        File htmlFile = TestUtil.getAliceHtmlFile();
        FileInputStream is = new FileInputStream(htmlFile);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Properties metadata = new Properties();
        metadata.setString(Importer.DOC_CONTENT_TYPE, "text/html");
        t.transformDocument(
                htmlFile.getAbsolutePath(), 
                is, os, metadata, false);
        
        Assert.assertEquals(
                "Length of doc content after transformation is incorrect.",
                458, os.toString().length());

        //System.out.println(os.toString());
        is.close();
        os.close();
    }
    
    
    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        StripBetweenTransformer t = new StripBetweenTransformer();
        t.setInclusive(true);
        t.addStripEndpoints("<!-- NO INDEX", "/NOINDEX -->");
        t.addStripEndpoints("<!-- HEADER START", "HEADER END -->");
        t.addExtraTextContentType("application/xml");
        System.out.println("Writing/Reading this: " + t);
        ConfigurationUtil.assertWriteRead(t);
    }

}
