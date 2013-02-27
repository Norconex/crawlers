package com.norconex.importer.tagger;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtils;

public class ReplaceTaggerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        ReplaceTagger tagger = new ReplaceTagger();
        tagger.addReplacement("fromValue1", "toValue1", "fromName1");
        tagger.addReplacement("fromValue2", "toValue2", "fromName1");
        tagger.addReplacement("fromValue1", "toValue1", "fromName2", "toName2");
        tagger.addReplacement("fromValue3", "toValue3", "fromName3", "toName3");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtils.assertWriteRead(tagger);
    }

}
