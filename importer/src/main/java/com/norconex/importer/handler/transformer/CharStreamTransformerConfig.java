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

import java.nio.charset.Charset;

import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>Base class for transformers dealing with text documents only.
 * Subclasses can safely be used as either pre-parse or post-parse handlers
 * restricted to text documents only (see {@link AbstractImporterHandler}).
 * </p>
 *
 * <p>
 * Sub-classes can restrict to which document to apply this transformation
 * based on document metadata (see {@link AbstractImporterHandler}).
 * </p>
 *
 * <p><b>Since 2.5.0</b>, when used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}.  If the character
 * set cannot be established, UTF-8 is assumed.
 * Since document
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
@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class CharStreamTransformerConfig {

    /**
     * The presumed source character encoding.
     * @param sourceCharset character encoding of the source to be transformed
     * @return character encoding of the source to be transformed
     */
    private Charset sourceCharset = null;
}