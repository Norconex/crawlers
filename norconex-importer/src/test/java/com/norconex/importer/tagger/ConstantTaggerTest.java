package com.norconex.importer.tagger;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtils;

public class ConstantTaggerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        ConstantTagger tagger = new ConstantTagger();
        tagger.addConstant("constant1", "value1");
        tagger.addConstant("constant1", "value2");
        tagger.addConstant("constant2", "valueA");
        tagger.addConstant("constant2", "valueA");
        tagger.addConstant("constant3", "valueZ");
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtils.assertWriteRead(tagger);
    }

}
