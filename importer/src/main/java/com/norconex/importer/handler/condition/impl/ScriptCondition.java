/* Copyright 2021 Norconex Inc.
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
package com.norconex.importer.handler.condition.impl;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.handler.condition.AbstractStringCondition;
import com.norconex.importer.parser.ParseState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * A condition formulated using a scripting language.
 * </p>
 * <p>
 * Refer to {@link ScriptRunner} for more information on using a scripting
 * language with Norconex Importer.
 * </p>
 * <h3>How to create a condition with scripting:</h3>
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
 *   <li><b>sectionIndex:</b> Content section index (integer) if it had to be
 *       split because it was too large (as per <code>maxReadSize</code>).</li>
 * </ul>
 * <p>
 * The expected <b>return value</b> from your script is a boolean indicating
 * whether the document was matched or not.
 * </p>
 *
 * {@nx.xml.usage
 * <condition class="com.norconex.importer.handler.condition.impl.ScriptCondition"
 *   {@nx.include com.norconex.importer.handler.condition.AbstractStringCondition#attributes}
 *       engineName="(script engine name)">
 *   <script>(your script)</script>
 * </condition>
 * }
 *
 * <h4>Usage examples:</h4>
 *
 * <h5>JavaScript:</h5>
 * {@nx.xml
 * <condition class="ScriptCondition" engineName="JavaScript>
 *   <script><![CDATA[
 *     returnValue = metadata.getString('fruit') == 'apple'
 *             || content.indexOf('Apple') > -1;
 *   ]]></script>
 * </condition>
 * }
 *
 * <h5>Lua:</h5>
 * {@nx.xml
 * <condition class="ScriptCondition" engineName="lua">
 *   <script><![CDATA[
 *     returnValue = metadata:getString('fruit') == 'apple'
 *             or content:find('Apple') ~= nil;
 *   ]]></script>
 * </condition>
 * }
 *
 * <h5>Python:</h5>
 * {@nx.xml
 * <condition class="ScriptCondition" engineName="python">
 *   <script><![CDATA[
 *     returnValue = metadata.getString('fruit') == 'apple' \
 *             or content.__contains__('Apple');
 *   ]]></script>
 * </condition>
 * }
 *
 * <h5>Velocity:</h5>
 * {@nx.xml
 * <condition class="ScriptCondition" engineName="velocity">
 *   <script><![CDATA[
 *     #set($returnValue = $metadata.getString("fruit") == "apple"
 *             || $content.contains("Apple"))
 *   ]]></script>
 * </condition>
 * }
 *
 * @see ScriptRunner
 */
@SuppressWarnings("javadoc")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ScriptCondition extends AbstractStringCondition {

    //TODO consider using composition so most of the logic
    // can be shared with other ScriptXXX classes.
    // MAYBE: Use chaining like servlet filters... so you can wrap
    // logic over logic in any way you want (layers of execution)

    //TODO add testing XML config in various unit tests

    @NonNull
    private ScriptRunner<Object> scriptRunner;

    @Override
    protected boolean testDocument(HandlerDoc doc, String input,
            ParseState parseState, int sectionIndex)
            throws ImporterHandlerException {

        if (scriptRunner == null) {
            throw new ImporterHandlerException("No ScriptRunner defined.");
        }

        var returnValue = scriptRunner.eval(b -> {
            b.put("reference", doc.getReference());
            b.put("content", input);
            b.put("metadata", doc.getMetadata());
            b.put("parsed", parseState);
            b.put("sectionIndex", sectionIndex);
        });
        LOG.debug("Returned value from ScriptCondition: {}", returnValue);
        if (returnValue == null) {
            return false;
        }
        if (returnValue instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(returnValue.toString());
    }
    @Override
    protected void saveStringConditionToXML(final XML xml) {
        if (scriptRunner != null) {
            xml.setAttribute("engineName", scriptRunner.getEngineName());
            xml.addElement("script", scriptRunner.getScript());
        }
    }
    @Override
    protected void loadStringConditionFromXML(final XML xml) {
        scriptRunner = new ScriptRunner<>(
                xml.getString("@engineName"), xml.getString("script"));
    }
}
