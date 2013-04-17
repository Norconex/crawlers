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
package com.norconex.importer.parser;

import java.io.Serializable;

import com.norconex.importer.ContentType;

/**
 * Factory providing document parsers for documents.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentParserFactory extends Serializable{

    /**
     * Gets a document parser, optionally based on its reference or content
     * type.
     * @param documentReference document reference
     * @param contentType content type
     * @return document parser
     */
    IDocumentParser getParser(
            String documentReference, ContentType contentType);
}
