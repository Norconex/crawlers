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
package com.norconex.importer.tagger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;
import com.norconex.importer.TestUtil;
import com.norconex.importer.tagger.impl.TextBetweenTagger;

public class TextBetweenTaggerTest {

    @Test
    public void testTagTextDocument() throws IOException {
        TextBetweenTagger t = new TextBetweenTagger();
        t.addTextEndpoints("headings", "<h1>", "</H1>");
        t.addTextEndpoints("headings", "<h2>", "</H2>");
        t.addTextEndpoints("strong", "<b>", "</B>");
        t.addTextEndpoints("strong", "<i>", "</I>");
        t.setCaseSensitive(false);
        t.setInclusive(true);
        File htmlFile = TestUtil.getAliceHtmlFile();
        FileInputStream is = new FileInputStream(htmlFile);

        Properties metadata = new Properties();
        metadata.setString(Importer.DOC_CONTENT_TYPE, "text/html");
        t.tagDocument(htmlFile.getAbsolutePath(), is, metadata, false);

        is.close();

        List<String> headings = metadata.getStrings("headings");
        List<String> strong = metadata.getStrings("strong");
        
        Assert.assertTrue("Failed to return: <h2>Down the Rabbit-Hole</h2>", 
                headings.contains("<h2>Down the Rabbit-Hole</h2>"));
        Assert.assertTrue("Failed to return: <h2>CHAPTER I</h2>",
                headings.contains("<h2>CHAPTER I</h2>"));
        Assert.assertTrue("Should have returned 17 <i> and <b> pairs",
                strong.size() == 17);
    }
    
    @Test
    public void testWriteRead() throws IOException {
        TextBetweenTagger tagger = new TextBetweenTagger();
        tagger.addTextEndpoints("headings", "<h1>", "</h1>");
        tagger.addTextEndpoints("headings", "<h2>", "</h2>");
        tagger.setContentTypeRegex("fakeRegex");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtil.assertWriteRead(tagger);
    }

}
