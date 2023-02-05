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
package com.norconex.importer.handler.filter.impl;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.handler.filter.AbstractStringFilter;
import com.norconex.importer.parser.ParseState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Filter incoming documents using a scripting language.
 * </p><p>
 * Refer to {@link ScriptRunner} for more information on using a scripting
 * language with Norconex Importer.
 * </p>
 * <h3>How to filter documents with scripting:</h3>
 * <p>
 * The following are variables made available to your script for each
 * document:
 * </p>
 * <ul>
 *   <li><b>reference:</b> Document unique reference as a string.</li>
 *   <li><b>content:</b> Document content, as a string
 *       (of <code>maxReadSize</code> length).</li>
 *   <li><b>metadata:</b> Document metadata as a {@link Properties}
 *       object.</li>
 *   <li><b>parsed:</b> Whether the document was already parsed, as a
 *       boolean.</li>
 *   <li><b>sectionIndex:</b> Content section index if it had to be split
 *        (as per <code>maxReadSize</code>), as an integer.</li>
 * </ul>
 * <p>
 * The expected <b>return value</b> from your script is a boolean indicating
 * whether the document was matched or not.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.ScriptFilter"
 *   {@nx.include com.norconex.importer.handler.filter.AbstractStringFilter#attributes}
 *       engineName="(script engine name)">
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *   <script>(your script)</script>
 * </handler>
 * }
 *
 * <h4>Usage examples:</h4>
 *
 * <h5>JavaScript:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="JavaScript">
 *   <script><![CDATA[
 *     returnValue = metadata.getString('fruit') == 'apple'
 *             || content.indexOf('Apple') > -1;
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Lua:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="lua">
 *   <script><![CDATA[
 *     returnValue = metadata:getString('fruit') == 'apple'
 *             or content:find('Apple') ~= nil;
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Python:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="python">
 *   <script><![CDATA[
 *     returnValue = metadata.getString('fruit') == 'apple' \
 *             or content.__contains__('Apple')
 *   ]]></script>
 * </handler>
 * }
 *
 * <h5>Velocity:</h5>
 * {@nx.xml
 * <handler class="ScriptFilter" engineName="velocity">
 *   <script><![CDATA[
 *     #set($returnValue = $metadata.getString("fruit") == "apple"
 *             || $content.contains("Apple"))
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
@Slf4j
public class ScriptFilter extends AbstractStringFilter {

    @NonNull
    private ScriptRunner<Object> scriptRunner;

    @Override
    protected boolean isStringContentMatching(HandlerDoc doc,
            StringBuilder content, ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {

        if (scriptRunner == null) {
            throw new ImporterHandlerException("No ScriptRunner defined.");
        }

        var returnValue = scriptRunner.eval(b -> {
            b.put("reference", doc.getReference());
            b.put("content", content.toString());
            b.put("metadata", doc.getMetadata());
            b.put("parsed", parseState);
            b.put("sectionIndex", sectionIndex);
        });
        LOG.debug("Returned object from ScriptFilter: {}", returnValue);
        if (returnValue == null) {
            return false;
        }
        if (returnValue instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(returnValue.toString());
    }

    @Override
    protected void saveStringFilterToXML(XML xml) {
        if (scriptRunner != null) {
            xml.setAttribute("engineName", scriptRunner.getEngineName());
            xml.addElement("script", scriptRunner.getScript());
        }
    }

    @Override
    protected void loadStringFilterFromXML(XML xml) {
        scriptRunner = new ScriptRunner<>(
                xml.getString("@engineName"), xml.getString("script"));
    }
}
