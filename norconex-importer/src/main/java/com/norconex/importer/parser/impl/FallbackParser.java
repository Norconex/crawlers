/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.parser.impl;

import org.apache.tika.parser.AutoDetectParser;

/**
 * Parser using auto-detection of document content-type to figure out
 * which specific parser to invoke to best parse a document.  
 * Use this class only when you
 * do not know the content-type of a document to be imported.  
 * @author Pascal Essiembre
 */
public class FallbackParser extends AbstractTikaParser {

    private static final long serialVersionUID = 673866160238948126L;

    /**
     * Creates a new parser.
     * @param format one of parser's supported format
     */
    public FallbackParser(String format) {
        super(new AutoDetectParser(), format);
    }

}
