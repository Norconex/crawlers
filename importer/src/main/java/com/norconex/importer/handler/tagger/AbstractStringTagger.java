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

import java.io.IOException;
import java.io.Reader;

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Base class to facilitate creating taggers based on text content, loading
 * text into {@link StringBuilder} for memory processing.
 *
 * <p><b>Since 2.2.0</b> this class limits the memory used for analysing
 * content by reading one section of text at a time.  Each
 * sections are sent for tagging once they are read,
 * so that no two sections exists in memory at once.  Sub-classes should
 * respect this approach.  Each of them have a maximum number of characters
 * equal to the maximum read size defined using {@link #setMaxReadSize(int)}.
 * When none is set, the default read size is defined by
 * {@link TextReader#DEFAULT_MAX_READ_SIZE}.
 * </p>
 *
 * <p>An attempt is made to break sections nicely after a paragraph, sentence,
 * or word.  When not possible, long text will be cut at a size equal
 * to the maximum read size.
 * </p>
 *
 * <p>
 * Implementors should be conscious about memory when dealing with the string
 * builder.
 * </p>
 * {@nx.xml.usage #attributes
 *   maxReadSize="(max characters to read at once)"
 *   {@nx.include com.norconex.importer.handler.tagger.AbstractCharStreamTagger#attributes}
 * }
 *
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} attribute(s),
 * in addition to <a href="../AbstractImporterHandler.html#nx-xml-restrictTo">
 * &lt;restrictTo&gt;</a>.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractStringTagger extends AbstractCharStreamTagger {

    private int maxReadSize = TextReader.DEFAULT_MAX_READ_SIZE;

    @Override
    protected final void tagTextDocument(
            HandlerDoc doc, Reader input, ParseState parseState)
                    throws ImporterHandlerException {

        var sectionIndex = 0;
        var b = new StringBuilder();
        String text = null;
        var atLeastOnce = false;
        try (var reader = new TextReader(input, maxReadSize)) {
            while ((text = reader.readText()) != null) {
                b.append(text);
                tagStringContent(doc, b, parseState, sectionIndex);
                sectionIndex++;
                b.setLength(0);
                atLeastOnce = true;
            }
            // If no content, go at least once in it in case the tagger
            // supports has metadata-related operations that should work
            // even if no content exists.
            if (!atLeastOnce) {
                tagStringContent(doc, b, parseState, 0);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot tag text document.", e);
        }
        b.setLength(0);
        b = null;
    }

    /**
     * Gets the maximum number of characters to read from content for tagging
     * at once. Default is {@link TextReader#DEFAULT_MAX_READ_SIZE}.
     * @return maximum read size
     */
    public int getMaxReadSize() {
        return maxReadSize;
    }
    /**
     * Sets the maximum number of characters to read from content for tagging
     * at once.
     * @param maxReadSize maximum read size
     */
    public void setMaxReadSize(int maxReadSize) {
        this.maxReadSize = maxReadSize;
    }

    protected abstract void tagStringContent(
           HandlerDoc doc, StringBuilder content, ParseState parseState,
           int sectionIndex) throws ImporterHandlerException;

    @Override
    protected final void saveCharStreamTaggerToXML(XML xml) {
        xml.setAttribute("maxReadSize", maxReadSize);
        saveStringTaggerToXML(xml);
    }
    /**
     * Saves configuration settings specific to the implementing class.
     * The parent tag along with the "class" attribute are already written.
     * Implementors must not close the writer.
     *
     * @param xml the XML
     */
    protected abstract void saveStringTaggerToXML(XML xml);

    @Override
    protected final void loadCharStreamTaggerFromXML(XML xml) {
        setMaxReadSize(xml.getInteger("@maxReadSize", maxReadSize));
        loadStringTaggerFromXML(xml);
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml xml configuration
     */
    protected abstract void loadStringTaggerFromXML(XML xml);
}