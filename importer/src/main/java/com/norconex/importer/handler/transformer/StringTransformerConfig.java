/* Copyright 2010-2023 Norconex Inc.
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

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.Data;
import lombok.experimental.Accessors;

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
 * {@nx.xml
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * }
 * </pre>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class StringTransformerConfig extends CharStreamTransformerConfig {

    /**
     * The maximum number of characters to read and transform
     * at once. Default is {@link TextReader#DEFAULT_MAX_READ_SIZE}.
     * @param maxReadSize maximum read size
     * @return maximum read size
     */
    private int maxReadSize = TextReader.DEFAULT_MAX_READ_SIZE;

}