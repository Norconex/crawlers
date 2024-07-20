package com.norconex.importer;

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.parser.impl.DefaultParser;
import com.norconex.importer.handler.transformer.impl.Constant;
import com.norconex.importer.handler.transformer.impl.ConstantTransformer;
import com.norconex.importer.response.DummyResponseProcessor;
import com.norconex.importer.response.ImporterResponseProcessor;

class ImporterConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void testWriteRead() {

        var config = new ImporterConfig()
            .setMaxMemoryInstance(123L)
            .setMaxMemoryPool(456L)
            .setHandlers(List.of(
                configure(new ConstantTransformer(), cfg ->
                    cfg.setConstants(List.of(Constant.of("test1", "abc")))),
                configure(new DefaultParser(), cfg -> {
                    cfg.setErrorsSaveDir(tempDir.resolve("saveDir"));
                    cfg.getEmbeddedConfig()
                        .setMaxEmbeddedDepth(2)
                        .setSkipEmbeddedContentTypes(List.of(
                                TextMatcher.basic("one"),
                                TextMatcher.regex(".*two.*")
                        ))
                        .setSkipEmbeddedOfContentTypes(List.of(
                                TextMatcher.basic("three"),
                                TextMatcher.regex(".*four.*")
                        ))
                        .setSplitContentTypes(List.of(
                                TextMatcher.basic("five"),
                                TextMatcher.regex(".*six.*")
                        ));
                    cfg.getOcrConfig()
                        .setApplyRotation(true)
                        .setColorSpace("blah")
                        .setDensity(12);
                }),
                configure(new ConstantTransformer(), cfg ->
                    cfg.setConstants(List.of(Constant.of("test2", "def"))))
            ))
            .setResponseProcessors(List.of(new DummyResponseProcessor()))
            .setTempDir(tempDir.resolve("temp"));


        assertDoesNotThrow(() ->
            BeanMapper.builder()
                .polymorphicType(ImporterResponseProcessor.class, n ->
                        n.endsWith("DummyResponseProcessor"))
                .build()
                .assertWriteRead(config));
    }
}
