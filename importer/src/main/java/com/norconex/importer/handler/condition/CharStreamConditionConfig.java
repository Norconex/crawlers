/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.importer.handler.condition;

import java.nio.charset.Charset;

import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>Base class for conditions dealing with the document content as text.
 * Subclasses can safely be used as either pre-parse or post-parse handler
 * conditions restricted to text documents only.
 * </p>
 *
 * {@nx.block #charEncoding
 * <h3>Character encoding</h3>
 * <p>
 * When used as a pre-parse handler,
 * this class will use detected or previously set content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
 * </p>
 * }
 *
 * {@nx.xml.usage #attributes
 *  sourceCharset="(character encoding)"
 * }
 *
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} attribute(s).
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class CharStreamConditionConfig {

    /**
     * The presumed source character encoding.
     * @param sourceCharset character encoding of the source to be transformed
     * @return character encoding of the source to be transformed
     */
    private Charset sourceCharset = null;
}