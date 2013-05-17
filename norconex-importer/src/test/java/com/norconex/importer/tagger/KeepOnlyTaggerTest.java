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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.tagger.impl.KeepOnlyTagger;

public class KeepOnlyTaggerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        KeepOnlyTagger tagger = new KeepOnlyTagger();
        tagger.addField("field1");
        tagger.addField("field2");
        tagger.addField("field3");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtil.assertWriteRead(tagger);
    }

    @Test
    public void test_keep_all_fields() throws Exception {

        Properties metadata = new Properties();
        metadata.addString("key1", "value1");
        metadata.addString("key2", "value2");
        metadata.addString("key3", "value3");

        // Should only keep all keys
        KeepOnlyTagger tagger = new KeepOnlyTagger();
        tagger.addField("key1");
        tagger.addField("key2");
        tagger.addField("key3");
        tagger.tagDocument("reference", null, metadata, true);

        assertEquals(3, metadata.size());
    }
    
    @Test
    public void test_keep_single_field() throws Exception {

        Properties metadata = new Properties();
        metadata.addString("key1", "value1");
        metadata.addString("key2", "value2");
        metadata.addString("key3", "value3");

        // Should only keep key1
        KeepOnlyTagger tagger = new KeepOnlyTagger();
        tagger.addField("key1");
        tagger.tagDocument("reference", null, metadata, true);

        assertEquals(1, metadata.size());
        assertTrue(metadata.containsKey("key1"));
    }

    @Test
    public void test_delete_all_metadata() throws Exception {

        Properties metadata = new Properties();
        metadata.addString("key1", "value1");
        metadata.addString("key2", "value2");
        metadata.addString("key3", "value3");

        // Because we are not adding any field to keep, all metadata should be
        // deleted
        KeepOnlyTagger tagger = new KeepOnlyTagger();
        tagger.tagDocument("reference", null, metadata, true);

        assertTrue(metadata.isEmpty());
    }

}
