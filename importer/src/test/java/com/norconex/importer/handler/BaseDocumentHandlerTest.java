/* Copyright 2023 Norconex Inc.
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
package com.norconex.importer.handler;

import static com.norconex.commons.lang.config.Configurable.configure;

import org.junit.jupiter.api.Test;

import com.norconex.importer.Importer;
import com.norconex.importer.handler.transformer.impl.TruncateTransformer;

class BaseDocumentHandlerTest {

    @Test
    void test() {
        var importer = configure(new Importer(), cfg -> cfg
            .setPreParseConsumer(
                BaseDocumentHandler.decorate(configure(
                    new TruncateTransformer(), t -> t
                        .setMaxLength(10)))));

        System.err.println("IMPORTER: " + importer);


//        var cfg = Configurable.configure(new TruncateTransformer(), t -> {
//            new ImporterConfig()
//            .setPreParseConsumer(
//        });
    }

}
