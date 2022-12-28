/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.importer.handler.tagger.impl;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.handler.tagger.AbstractStringTagger;
import com.norconex.importer.parser.ParseState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * <p>
 * Tag incoming documents using a scripting language.
 * </p><p>
 * Refer to {@link ScriptRunner} for more information on using a scripting
 * language with Norconex Importer.
 * </p>
 * <h3>How to tag documents with scripting:</h3>
 * <p>
 * The following are variables made available to your script for each
 * document:
 * </p>
 * <ul>
 *   <li><b>reference:</b> Document unique reference as a string.</li>
 *   <li><b>content:</b> Document content, as a string
 *       (of <code>maxReadSize</code> length).</li>
 *   <li><b>metadata:</b> Document metadata as an {@link Properties}
 *       object.</li>
 *   <li><b>parsed:</b> Whether the document was already parsed, as a
 *       boolean.</li>
 *   <li><b>sectionIndex:</b> Content section index if it had to be split
 *       (as per <code>maxReadSize</code>), as an integer.</li>
 * </ul>
 * <p>
 * There are no expected return value from your script. Returning
 * one has no effect.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.ScriptTagger"
 *     engineName="(script engine name)"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractStringTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <script>(your script)</script>
 *
 * </handler>
 * }
 *
 * <h4>Usage examples:</h4>
 *
 * <p>The following examples add new metadata field indicating which
 * fruit is a document about.</p>
 *
 * <h5>JavaScript:</h5>
 * {@nx.xml
 * <handler class="ScriptTagger" engineName="JavaScript">
 *   <script><![CDATA[
 *       metadata.add('fruit', 'apple');
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Lua:</h5>
 * {@nx.xml
 * <handler class="ScriptTagger" engineName="lua">
 *   <script><![CDATA[
 *       metadata:addString('fruit', {'apple'});
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Python:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="python">
 *   <script><![CDATA[
 *     metadata.addString('fruit', 'apple')
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Velocity:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="velocity">
 *   <script><![CDATA[
 *     $metadata.addString("fruit", "apple")
 *   ]]></script>
 * </handler>
 * }
 *
 * @see ScriptRunner
 */
@SuppressWarnings("javadoc")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptTagger extends AbstractStringTagger {

    @NonNull
    private ScriptRunner<String> scriptRunner;

    @Override
    protected void tagStringContent(HandlerDoc doc, StringBuilder content,
            ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {

        if (scriptRunner == null) {
            throw new ImporterHandlerException("No ScriptRunner defined.");
        }

        scriptRunner.eval(b -> {
            b.put("reference", doc.getReference());
            b.put("content", content.toString());
            b.put("metadata", doc.getMetadata());
            b.put("parsed", parseState);
            b.put("sectionIndex", sectionIndex);
        });
    }

    @Override
    protected void saveStringTaggerToXML(XML xml) {
        if (scriptRunner != null) {
            xml.setAttribute("engineName", scriptRunner.getEngineName());
            xml.addElement("script", scriptRunner.getScript());
        }
    }

    @Override
    protected void loadStringTaggerFromXML(XML xml) {
        scriptRunner = new ScriptRunner<>(
                xml.getString("@engineName"), xml.getString("script"));
    }
}
