/* Copyright 2014-2023 Norconex Inc.
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
import java.util.regex.Pattern;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.DocumentTransformer;
import com.norconex.importer.util.DocChunkedTextReader;
import com.norconex.importer.util.DocContextUtil;

import lombok.Data;

/**
 * <p>Reduces specified consecutive characters or strings to only one
 * instance (document content only).
 * If reducing duplicate words, you usually have to add a space at the
 * Beginning or end of the word.
 * </p>
 * <p>
 * This class can be used as a pre-parsing (text content-types only)
 * or post-parsing handlers.
 * </p>
 * <p>
 * For more advanced replacement needs, consider using
 * {@link ReplaceTransformer} instead.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.ReduceConsecutivesTransformer"
 *     ignoreCase="[false|true]"
 *     {@nx.include com.norconex.importer.handler.transformer.AbstractStringTransformer#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple reduce tags allowed -->
 *   <reduce>(character or string to strip)</reduce>
 *
 * </handler>
 * }
 * <p>
 * In addition to regular characters, you can specify these special characters
 * in your XML:
 * </p>
 * <ul>
 *   <li>\r (carriage returns)</li>
 *   <li>\n (line feed)</li>
 *   <li>\t (tab)</li>
 *   <li>\s (space)</li>
 * </ul>
 * {@nx.xml.example
 * <handler class="ReduceConsecutivesTransformer">
 *   <reduce>\s</reduce>
 * </handler>
 * }
 * <p>
 * The above example reduces multiple spaces into a single one.
 * </p>
 *
 * @see ReplaceTransformer
 */
@SuppressWarnings("javadoc")
@Data
public class CollapseRepeatingTransformer implements
        DocumentTransformer, Configurable<CollapseRepeatingTransformerConfig> {

    private final CollapseRepeatingTransformerConfig configuration =
            new CollapseRepeatingTransformerConfig();

    @Override
    public void accept(DocContext docCtx) throws ImporterHandlerException {
        try {
            DocChunkedTextReader.builder()
                .fieldMatcher(configuration.getFieldMatcher())
                .maxChunkSize(configuration.getMaxReadSize())
                .charset(configuration.getSourceCharset())
                .build()
                .read(docCtx, chunk -> {
                    var out = new StringBuilder();
                    var in = chunk.getText();
                    Pattern pattern;
                    for (String str : configuration.getStrings()) {
                        var regex = "("
                                + escapeRegex(resolveControlChars(str)) + ")+";
                        if (configuration.isIgnoreCase()) {
                            pattern = Pattern.compile(
                                    regex, Pattern.CASE_INSENSITIVE);
                        } else {
                            pattern = Pattern.compile(regex);
                        }
                        in = pattern.matcher(in).replaceAll("$1");
                    }
                    out.append(in);
                    DocContextUtil.setTextChunk(docCtx, chunk, out.toString());
                    out.setLength(0);
                    return true;
                });
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot parse document into a DOM-tree.", e);
        }
    }

    private String resolveControlChars(String text) {
        return text
            .replaceAll("\\\\s", " ")  //NOSONAR test fails otherwise
            .replaceAll("\\\\t", "\t") //NOSONAR test fails otherwise
            .replaceAll("\\\\n", "\n") //NOSONAR test fails otherwise
            .replaceAll("\\\\r", "\r");//NOSONAR test fails otherwise
    }

    private String escapeRegex(String text) {
        return text.replaceAll(
                "([\\\\\\.\\[\\{\\(\\*\\+\\?\\^\\$\\|])", "\\\\$1");
    }
//
//    @Override
//    protected void loadStringTransformerFromXML(final XML xml) {
//        xml.checkDeprecated("@caseSensitive", "ignoreCase", true);
//
//        setIgnoreCase(xml.getBoolean("@ignoreCase", ignoreCase));
//
//        var nodes = xml.getXMLList("reduce");
//        for (XML node : nodes) {
//            var text = node.getString(".");
//            text = text.replaceAll("\\\\s", " ");
//            text = text.replaceAll("\\\\t", "\t");
//            text = text.replaceAll("\\\\n", "\n");
//            text = text.replaceAll("\\\\r", "\r");
//            addReduction(text);
//        }
//    }
//
//    @Override
//    protected void saveStringTransformerToXML(final XML xml) {
//        xml.setAttribute("ignoreCase", ignoreCase);
//        for (String reduction : reductions) {
//            if (reduction != null) {
//                var text = reduction;
//                text = text.replaceAll(" ", "\\\\s");
//                text = text.replaceAll("\t", "\\\\t");
//                text = text.replaceAll("\n", "\\\\n");
//                text = text.replaceAll("\r", "\\\\r");
//                xml.addElement("reduce", text);
//            }
//        }
//    }
}
