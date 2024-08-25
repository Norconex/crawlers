/* Copyright 2020-2024 Norconex Inc.
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

import java.io.IOException;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.importer.handler.BaseDocumentHandler;
import com.norconex.importer.handler.HandlerContext;
import com.norconex.importer.util.chunk.ChunkedTextReader;

import lombok.Data;

/**
 * <p>
 * Extracts field names and their values with regular expression.
 * This is done by using
 * match groups in your regular expressions (parenthesis).  For each pattern
 * you define, you can specify which match group hold the field name and
 * which one holds the value.
 * Specifying a field match group is optional if a <code>field</code>
 * is provided.  If no match groups are specified, a <code>field</code>
 * is expected.
 * </p>
 *
 * <p>
 * If "fieldMatcher" is specified, it will use content from matching fields and
 * storing all text extracted into the target field, multi-value.
 * Else, the document content is used.
 * </p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * <p>
 * This class can be used as a pre-parsing handler on text documents only
 * or a post-parsing handler.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.RegexTagger"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractStringTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression matching source fields on which to perform extraction)
 *   </fieldMatcher>
 *
 *   <!-- multiple pattern tags allowed -->
 *   <pattern
 *       {@nx.include com.norconex.commons.lang.text.RegexFieldValueExtractor#attributes}>
 *     (regular expression)
 *   </pattern>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="RegexTagger" >
 *   <pattern toField="emails">
 *     [A-Za-z0-9+_.-]+?@[a-zA-Z0-9.-]+
 *   </pattern>
 *   <pattern fieldGroup="1" valueGroup="2"><![CDATA[
 *     <tr><td class="label">(.*?)</td><td class="value">(.*?)</td></tr>
 *   ]]></pattern>
 * </handler>
 * }
 * <p>
 * The first pattern in the above example extracts what look like email
 * addresses in to an "email" field (simplified regex). The second pattern
 * extracts field names and values from "label" and "value" cells on
 * a given HTML table.
 * </p>
 *
 * @see RegexFieldValueExtractor
 */
@SuppressWarnings("javadoc")
@Data
public class RegexTransformer
        extends BaseDocumentHandler
        implements Configurable<RegexTransformerConfig> {

    private final RegexTransformerConfig configuration =
            new RegexTransformerConfig();

    @Override
    public void handle(HandlerContext docCtx) throws IOException {
        ChunkedTextReader.from(configuration).read(docCtx, chunk -> {
            RegexFieldValueExtractor.extractFieldValues(
                    docCtx.metadata(),
                    chunk.getText(),
                    configuration.getPatterns()
            );
            return true;
        });
    }
}
