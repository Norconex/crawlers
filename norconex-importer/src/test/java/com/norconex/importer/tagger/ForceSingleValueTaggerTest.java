package com.norconex.importer.tagger;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtils;

public class ForceSingleValueTaggerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        ForceSingleValueTagger tagger = new ForceSingleValueTagger();
        tagger.addSingleValueField("field1", "keepFirst");
        tagger.addSingleValueField("field2", "keepFirst");
        tagger.addSingleValueField("field3", "keepFirst");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtils.assertWriteRead(tagger);
    }

}
