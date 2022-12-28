package com.norconex.importer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.tagger.impl.ConstantTagger;
import com.norconex.importer.parser.GenericDocumentParserFactory;
import com.norconex.importer.response.DummyResponseProcessor;

class ImporterConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void testSaveLoadXML() {
        var config = new ImporterConfig();
        config.setMaxMemoryInstance(123L);
        config.setMaxMemoryPool(456L);
        config.setParseErrorsSaveDir(tempDir.resolve("saveDir"));
        config.setParserFactory(new GenericDocumentParserFactory());

        var preTagger = new ConstantTagger();
        preTagger.addConstant("test1", "abc");
        config.setPreParseConsumer(new HandlerConsumer(preTagger));
        var postTagger = new ConstantTagger();
        postTagger.addConstant("test2", "def");
        config.setPostParseConsumer(new HandlerConsumer(postTagger));
        config.setResponseProcessors(Arrays.asList(
                new DummyResponseProcessor()));
        config.setTempDir(tempDir.resolve("temp"));

        assertDoesNotThrow(() -> XML.assertWriteRead(config, "importer"));
    }
}
