/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import static java.util.Optional.ofNullable;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.handler.BaseDocumentHandler;
import com.norconex.importer.handler.HandlerContext;

import lombok.Data;

/**
 * <p>Define and add constant values to documents.  To add multiple constant
 * values under the same constant name, repeat the constant entry with a
 * different value.
 * </p>
 * <h2>Storing values in an existing field</h2>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior
 * with {@link #setOnSet(PropertySetter)}.
 * </p>
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.ConstantTagger"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple constant tags allowed -->
 *   <constant name="CONSTANT_NAME">Constant Value</constant>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="ConstantTagger">
 *    <constant name="source">web</constant>
 *  </handler>
 * }
 * <p>
 * The above example adds a constant to incoming documents to identify they
 * were web documents.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
public class ConstantTransformer extends BaseDocumentHandler
        implements Configurable<ConstantTransformerConfig> {

    private final ConstantTransformerConfig configuration =
            new ConstantTransformerConfig();

    @Override
    public void handle(HandlerContext docCtx) {
        configuration.getConstants().forEach(c -> {
            if (c != null) {
                var setter = ofNullable(
                        c.getOnSet()).orElse(configuration.getOnSet());
                PropertySetter.orAppend(setter).apply(
                        docCtx.metadata(), c.getName(), c.getValues());
            }
        });
    }
}
