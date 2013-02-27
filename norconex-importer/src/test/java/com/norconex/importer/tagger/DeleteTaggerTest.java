package com.norconex.importer.tagger;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtils;

public class DeleteTaggerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        DeleteTagger tagger = new DeleteTagger();
        tagger.addField("potato");
        tagger.addField("potato");
        tagger.addField("carrot");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtils.assertWriteRead(tagger);
    }

}
