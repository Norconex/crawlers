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
package com.norconex.importer.handler.transformer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Base class to facilitate creating transformers on text content, loading
 * text into a {@link StringBuilder} for memory processing.
 * </p>
 *
 * <p><b>Since 2.2.0</b> this class limits the memory used for content
 * transformation by reading one section of text at a time.  Each
 * sections are sent for transformation once they are read,
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
 *
 * {@nx.xml.usage #attributes
 *   maxReadSize="(max characters to read at once)"
 *   {@nx.include com.norconex.importer.handler.transformer.AbstractCharStreamTransformer#attributes}
 * }
 *
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} attribute(s),
 * in addition to <a href="../AbstractImporterHandler.html#nx-xml-restrictTo">
 * &lt;restrictTo&gt;</a>.
 * </p>
 *
 * <p>
 * Subclasses inherit this {@link XMLConfigurable} configuration:
 * </p>
 * <pre>
 *  &lt;!-- parent tag has these attribute:
 *      maxReadSize="(max characters to read at once)"
 *      sourceCharset="(character encoding)"
 *    --&gt;
 *  &lt;restrictTo caseSensitive="[false|true]"
 *          field="(name of header/metadata field name to match)" &gt;
 *      (regular expression of value to match)
 *  &lt;/restrictTo&gt;
 *  &lt;!-- multiple "restrictTo" tags allowed (only one needs to match) --&gt;
 * </pre>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractStringTransformer
            extends AbstractCharStreamTransformer {

    private int maxReadSize = TextReader.DEFAULT_MAX_READ_SIZE;

    @Override
    protected final void transformTextDocument(
            HandlerDoc doc, final Reader input,
            final Writer output, final ParseState parseState)
                    throws ImporterHandlerException {

        var sectionIndex = 0;
        var b = new StringBuilder();
        String text = null;
        var atLeastOnce = false;
        try (var reader = new TextReader(input, maxReadSize)) {
            while ((text = reader.readText()) != null) {
                b.append(text);
                transformStringContent(doc, b, parseState, sectionIndex);
                output.append(b);
                sectionIndex++;
                b.setLength(0);
                atLeastOnce = true;
            }
            // If no content, go at least once in it in case the transformer
            // is writing content regardless.
            if (!atLeastOnce) {
                transformStringContent(doc, b, parseState, 0);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot transform text document.", e);
        }
        b.setLength(0);
        b = null;
    }

    /**
     * Gets the maximum number of characters to read and transform
     * at once. Default is {@link TextReader#DEFAULT_MAX_READ_SIZE}.
     * @return maximum read size
     */
    public int getMaxReadSize() {
        return maxReadSize;
    }
    /**
     * Sets the maximum number of characters to read and transform
     * at once.
     * @param maxReadSize maximum read size
     */
    public void setMaxReadSize(final int maxReadSize) {
        this.maxReadSize = maxReadSize;
    }

    protected abstract void transformStringContent(
            HandlerDoc doc, StringBuilder content,
            ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException;

    @Override
    protected final void saveCharStreamTransformerToXML(final XML xml) {
        xml.setAttribute("maxReadSize", maxReadSize);
        saveStringTransformerToXML(xml);
    }
    /**
     * Saves configuration settings specific to the implementing class.
     * The parent tag along with the "class" attribute are already written.
     * Implementors must not close the writer.
     *
     * @param xml the XML
     */
    protected abstract void saveStringTransformerToXML(XML xml);

    @Override
    protected final void loadCharStreamTransformerFromXML(final XML xml) {
        setMaxReadSize(xml.getInteger("@maxReadSize", maxReadSize));
        loadStringTransformerFromXML(xml);
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml XML configuration
     */
    protected abstract void loadStringTransformerFromXML(XML xml);
}