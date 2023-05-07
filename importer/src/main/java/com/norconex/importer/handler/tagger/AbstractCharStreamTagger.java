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
package com.norconex.importer.handler.tagger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apache.commons.io.input.NullInputStream;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.AbstractImporterHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Base class for taggers dealing with the body of text documents only.
 * Subclasses can safely be used as either pre-parse or post-parse handlers
 * restricted to text documents only (see {@link AbstractImporterHandler}).
 * </p>
 *
 * <p><b>Since 2.5.0</b>, when used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
 * </p>
 *
 * {@nx.xml.usage #attributes
 *  sourceCharset="(character encoding)"
 * }
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} attribute(s),
 * in addition to <a href="../AbstractImporterHandler.html#nx-xml-restrictTo">
 * &lt;restrictTo&gt;</a>.
 * </p>
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractCharStreamTagger extends AbstractDocumentTagger {

    private String sourceCharset = null;

    /**
     * Gets the assumed source character encoding.
     * @return character encoding of the source to be transformed
         */
    public String getSourceCharset() {
        return sourceCharset;
    }
    /**
     * Sets the assumed source character encoding.
     * @param sourceCharset character encoding of the source to be transformed
         */
    public void setSourceCharset(String sourceCharset) {
        this.sourceCharset = sourceCharset;
    }

    @Override
    protected final void tagApplicableDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {
        var nonNullDocument = input;
        if (nonNullDocument == null) {
            nonNullDocument = new NullInputStream(0); //NOSONAR nothing to close
        }

        var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                parseState,
                sourceCharset,
                doc.getDocInfo().getContentEncoding());
        try {
            var reader =
                    new InputStreamReader(nonNullDocument, inputCharset);
            tagTextDocument(doc, reader, parseState);
        } catch (UnsupportedEncodingException e) {
            throw new ImporterHandlerException(e);
        }
    }

    protected abstract void tagTextDocument(
            HandlerDoc doc, Reader input, ParseState parseState)
                    throws ImporterHandlerException;


    @Override
    protected final void saveHandlerToXML(XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        saveCharStreamTaggerToXML(xml);
    }
    /**
     * Saves configuration settings specific to the implementing class.
     * The parent tag along with the "class" attribute are already written.
     * Implementors must not close the writer.
     *
     * @param xml the XML
     */
    protected abstract void saveCharStreamTaggerToXML(XML xml);

    @Override
    protected final void loadHandlerFromXML(XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        loadCharStreamTaggerFromXML(xml);
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml xml configuration
     */
    protected abstract void loadCharStreamTaggerFromXML(XML xml);
}