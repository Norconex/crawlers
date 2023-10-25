/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.importer.parser.impl;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.output.WriterOutputStream;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.ExternalHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.impl.ExternalTransformer;
import com.norconex.importer.handler.transformer.impl.ExternalTransformerConfig;
import com.norconex.importer.parser.DocumentParser;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.ParseOptions;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Parses and extracts text from a file using an external application to do so.
 * </p>
 * <p>
 * This class relies on {@link ExternalHandler} for most of the work.
 * Refer to {@link ExternalHandler} for full documentation.
 * </p>
 * <p>
 * This parser can be made configurable via XML. See
 * {@link GenericDocumentParserFactory} for general indications how
 * to configure parsers.
 * </p>
 * <p>
 * To use an external application to change a file content after parsing has
 * already occurred, consider using {@link ExternalTransformer} instead.
 * </p>
 *
 * {@nx.xml.usage
 * <parser contentType="(content type this parser is associated to)"
 *     class="com.norconex.importer.parser.impl.ExternalParser" >
 *
 *   <command>
 *     c:\Apps\myapp.exe ${INPUT} ${OUTPUT} ${INPUT_META} ${OUTPUT_META} ${REFERENCE}
 *   </command>
 *
 *   <metadata
 *       inputFormat="[json|xml|properties]"
 *       outputFormat="[json|xml|properties]"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *     <!-- pattern only used when no output format is specified -->
 *     <pattern {@nx.include com.norconex.commons.lang.text.RegexFieldValueExtractor#attributes}>
 *       (regular expression)
 *     </pattern>
 *     <!-- repeat pattern tag as needed -->
 *   </metadata>
 *
 *   <environment>
 *     <variable name="(environment variable name)">
 *       (environment variable value)
 *     </variable>
 *     <!-- repeat variable tag as needed -->
 *   </environment>
 *
 * </parser>
 * }
 *
 *
 * {@nx.xml.example
 * <parser contentType="text/plain"
 *     class="com.norconex.importer.parser.impl.ExternalParser" >
 *   <command>/path/transform/app ${INPUT} ${OUTPUT}</command>
 *   <metadata>
 *     <pattern field="docnumber" valueGroup="1">DocNo:(\d+)</pattern>
 *   </metadata>
 * </parser>
 * }
 *
 * <p>
 * The above example invokes an external application processing for
 * simple text files that accepts two files as arguments:
 * the first one being the file to
 * transform, the second one being holding the transformation result.
 * It also extract a document number from STDOUT, found as "DocNo:1234"
 * and storing it as "docnumber".
 * </p>
 *
 * @see ExternalHandler
 */
@SuppressWarnings("javadoc")
@ToString
@EqualsAndHashCode
public class ExternalParser implements
        DocumentParser, Configurable<ExternalTransformerConfig> {

    //TODO what about conditionally disabling some parsers? already covered?

    private final ExternalTransformer t = new ExternalTransformer();

    //TODO change all configuration so they can be set, but never null
    @Override
    public ExternalTransformerConfig getConfiguration() {
        return t.getConfiguration();
    }

    //TODO pass DocContext isntead? and throw IOException?
    //TODO should parser just be another handler?  Then drop ParseState,
    // or let handlers set it on the DocContext for those that are interested
    // to know (like Parser)?  Yeah... I like that! That would allow us to use conditions
    // around parsing as well, on top of being able to use parsing more
    // than once.
    @Override
    public List<Doc> parseDocument(Doc doc,
            Writer output) throws DocumentParserException {
        try {
//            t.accept(DocContext.builder()
//                    .doc(doc)
//                    .eventManager(null)
//                    .build());
            h.handleDocument(new HandlerDoc(doc), doc.getInputStream(),
                    WriterOutputStream.builder()
                        .setCharset(StandardCharsets.UTF_8)
                        .setWriter(output)
                        .get());
        } catch (ImporterHandlerException | IOException e) {
            throw new DocumentParserException(
                    "Could not parse document: " + doc.getReference(), e);
        }
        return Collections.emptyList();
    }

    @Override
    public void init(@NonNull ParseOptions parseOptions)
            throws DocumentParserException {
        //NOOP
    }
}
