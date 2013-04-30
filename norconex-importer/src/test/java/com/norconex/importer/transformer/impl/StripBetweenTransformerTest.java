/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.transformer.impl;

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
import com.norconex.importer.transformer.impl.StripBetweenTransformer;

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
        System.out.println(os.toString());
        
        Assert.assertEquals(
                "Length of doc content after transformation is incorrect.",
                458, os.toString().length());

        is.close();
        os.close();
    }
    
    
    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        StripBetweenTransformer t = new StripBetweenTransformer();
        t.setInclusive(true);
        t.addStripEndpoints("<!-- NO INDEX", "/NOINDEX -->");
        t.addStripEndpoints("<!-- HEADER START", "HEADER END -->");
        t.setContentTypeRegex("application/xml");
        System.out.println("Writing/Reading this: " + t);
        ConfigurationUtil.assertWriteRead(t);
    }

}
