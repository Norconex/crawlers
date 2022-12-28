/* Copyright 2010-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter;

import java.io.InputStream;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.AbstractImporterHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

import lombok.Data;

/**
 * <p>Base class for document filters.  Subclasses can be set an attribute
 * called "onMatch".  The logic whether to include or exclude a document
 * upon matching it is handled by this class.  Subclasses only
 * need to focus on whether the document gets matched or not by
 * implementing the
 * {@link #isDocumentMatched(HandlerDoc, InputStream, ParseState)}
 * method.</p>
 *
 * <h3 id="logic">Inclusion/exclusion logic:</h3>
 * <p>The logic for accepting or rejecting documents when a subclass condition
 * is met ("matches") is as follow:</p>
 * <table border="1" summary="Inclusion/exclusion logic">
 *  <tr>
 *   <td><b>Matches?</b></td>
 *   <td><b>On match</b></td>
 *   <td><b>Expected behavior</b></td>
 *  </tr>
 *  <tr>
 *   <td>yes</td><td>exclude</td><td>Document is rejected.</td>
 *  </tr>
 *  <tr>
 *   <td>yes</td><td>include</td><td>Document is accepted.</td>
 *  </tr>
 *  <tr>
 *   <td>no</td><td>exclude</td><td>Document is accepted.</td>
 *  </tr>
 *  <tr>
 *   <td>no</td><td>include</td>
 *   <td>Document is accepted if it was accepted by at least one filter with
 *       onMatch="include". If no other one exists or if none matched,
 *       the document is rejected.</td>
 *  </tr>
 * </table>
 * <p>
 * When multiple filters are defined and a combination of both "include" and
 * "exclude" are possible, the "exclude" will always take precedence.
 * In other words, it only take one matching "exclude" to reject a document,
 * not matter how many matching "include" were triggered.
 * </p>
 *
 * {@nx.xml.usage #attributes
 *  onMatch="[include|exclude]"
 * }
 *
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} attribute(s),
 * in addition to <a href="../AbstractImporterHandler.html#nx-xml-restrictTo">
 * &lt;restrictTo&gt;</a>.
 * </p>
 *
 */
@Data
public abstract class AbstractDocumentFilter extends AbstractImporterHandler
            implements DocumentFilter, OnMatchFilter {

    private OnMatch onMatch = OnMatch.INCLUDE;

    @Override
    public boolean acceptDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
            throws ImporterHandlerException {
        if (!isApplicable(doc, parseState)) {
            return true;
        }
        var matched = isDocumentMatched(doc, input, parseState);
        var safeOnMatch = OnMatch.includeIfNull(onMatch);
        if (matched) {
            return safeOnMatch == OnMatch.INCLUDE;
        }
        return safeOnMatch == OnMatch.EXCLUDE;
    }

    protected abstract boolean isDocumentMatched(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException;

    @Override
    protected final void saveHandlerToXML(XML xml) {
        xml.setAttribute("onMatch", onMatch);
        saveFilterToXML(xml);
    }
    protected abstract void saveFilterToXML(XML xml);

    @Override
    protected final void loadHandlerFromXML(XML xml) {
        setOnMatch(xml.getEnum("@onMatch", OnMatch.class, onMatch));
        loadFilterFromXML(xml);
    }
    protected abstract void loadFilterFromXML(XML xml);
}