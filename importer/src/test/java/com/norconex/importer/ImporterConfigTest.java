/* Copyright 2024 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.norconex.importer;

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.importer.handler.transformer.impl.Constant;
import com.norconex.importer.handler.transformer.impl.ConstantTransformer;
import com.norconex.importer.response.DummyResponseProcessor;

class ImporterConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void testWriteRead() {

        var config = new ImporterConfig()
                .setMaxMemoryInstance(123L)
                .setMaxMemoryPool(456L)
                .setHandlers(List.of(
                        configure(
                                new ConstantTransformer(),
                                cfg -> cfg.setConstants(List.of(
                                        Constant.of("test1", "abc"))))
                //                        ,
                //                        configure(new DefaultParser(), cfg -> {
                //                            cfg.setErrorsSaveDir(
                //                                    tempDir.resolve("saveDir"));
                //                            cfg.getEmbeddedConfig()
                //                                    .setMaxEmbeddedDepth(2)
                //                                    .setSkipEmbeddedContentTypes(List.of(
                //                                            TextMatcher.basic("one"),
                //                                            TextMatcher.regex(".*two.*")))
                //                                    .setSkipEmbeddedOfContentTypes(List.of(
                //                                            TextMatcher.basic("three"),
                //                                            TextMatcher.regex(".*four.*")))
                //                                    .setSplitContentTypes(List.of(
                //                                            TextMatcher.basic("five"),
                //                                            TextMatcher.regex(".*six.*")));
                //                            cfg.getOcrConfig()
                //                                    .setApplyRotation(true)
                //                                    .setColorSpace("blah")
                //                                    .setDensity(12);
                //                        }),
                //                        configure(
                //                                new ConstantTransformer(),
                //                                cfg -> cfg.setConstants(List.of(
                //                                        Constant.of("test2", "def"))))
                ))
                .setResponseProcessors(List.of(new DummyResponseProcessor()))
                .setTempDir(tempDir.resolve("temp"));
        assertDoesNotThrow(() -> BeanMapper.DEFAULT.assertWriteRead(config));

        //        assertDoesNotThrow(
        //                () -> BeanMapper.builder()
        //                        .polymorphicType(
        //                                ImporterResponseProcessor.class,
        //                                n -> n.endsWith("DummyResponseProcessor"))
        //                        .build()
        //                        .assertWriteRead(config));
    }
}
