package com.norconex.importer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.tagger.impl.ConstantTagger;
import com.norconex.importer.response.DummyResponseProcessor;

class ImporterConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void testSaveLoadXML() {
        var config = new ImporterConfig();
        config.setMaxMemoryInstance(123L);
        config.setMaxMemoryPool(456L);
        config.getParseConfig().setErrorsSaveDir(tempDir.resolve("saveDir"));

        var preTagger = new ConstantTagger();
        preTagger.addConstant("test1", "abc");
        config.setPreParseConsumer(new HandlerConsumer(preTagger));
        var postTagger = new ConstantTagger();
        postTagger.addConstant("test2", "def");
        config.setPostParseConsumer(new HandlerConsumer(postTagger));
        config.setResponseProcessors(List.of(new DummyResponseProcessor()));
        config.setTempDir(tempDir.resolve("temp"));

        assertDoesNotThrow(() -> BeanMapper.DEFAULT.assertWriteRead(config);
    }
}
