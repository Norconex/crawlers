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
package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.IImportHandler;

/**
 * Transformers allow to manipulate and convert extracted text and
 * save the modified text back.
 * @author Pascal Essiembre
 */
public interface IDocumentTransformer extends IImportHandler {

    /**
     * Transforms document content and metadata.
     * @param reference document reference (e.g. URL)
     * @param input document to transform
     * @param output transformed document
     * @param metadata document metadata
     * @param parsed whether the document has been parsed already or not (a 
     *        parsed document should normally be text-based)
     */
    void transformDocument(
            String reference, InputStream input, 
            OutputStream output, Properties metadata, boolean parsed)
                    throws IOException;
}
