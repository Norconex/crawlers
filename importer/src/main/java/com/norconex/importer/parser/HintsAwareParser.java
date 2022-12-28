/* Copyright 2016 Norconex Inc.
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
package com.norconex.importer.parser;

/**
 * Indicates that a parser can be initialized with generic parser configuration
 * settings and it will try to apply any such settings the best it can
 * when possible to do so.  Those settings are typically general settings
 * that applies to more than one parser and can thus be configured "globally"
 * for all applicable parsers.
 * It should be left to {@link DocumentParserFactory} implementations
 * to initialize parsers as they see fit.
 * The default {@link GenericDocumentParserFactory} will always invoke the
 * {@link #initialize(ParseHints)} method at least once per configured parsers. 
 * 
 */
public interface HintsAwareParser extends DocumentParser {

    /**
     * Initialize this parser with the given parse hints.  While not mandatory,
     * aware parsers are strongly encouraged to support applicable hints.
     * @param parserHints configuration settings influencing parsing when 
     * possible or appropriate
     */
    void initialize(ParseHints parserHints);
    
}
