package com.norconex.collector.http.handler.impl;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Test;

import com.norconex.collector.http.handler.impl.GenericURLNormalizer.Normalization;
import com.norconex.commons.lang.config.ConfigurationUtil;

public class GenericURLNormallizerTest {

    @Test
    public void testWriteRead() throws ConfigurationException, IOException {
        GenericURLNormalizer n = new GenericURLNormalizer();
        n.setNormalizations(
                Normalization.lowerCaseSchemeHost,
                Normalization.addTrailingSlash,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDotSegments,
                Normalization.removeDuplicateSlashes,
                Normalization.removeSessionIds);
        n.setReplaces(
                n.new Replace("\\.htm", ".html"),
                n.new Replace("&debug=true"));
        System.out.println("Writing/Reading this: " + n);
        ConfigurationUtil.assertWriteRead(n);
    }

}
