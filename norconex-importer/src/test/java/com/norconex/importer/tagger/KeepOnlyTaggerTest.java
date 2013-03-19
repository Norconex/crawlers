package com.norconex.importer.tagger;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;

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

}
