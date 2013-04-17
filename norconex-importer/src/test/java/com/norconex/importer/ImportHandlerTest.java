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
