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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.AbstractRestrictiveHandler;
import com.norconex.importer.AbstractTextRestrictiveHandler;
import com.norconex.importer.Importer;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * <p>Base class for transformers dealing with text documents only.  Subclasses
 * can safely be used as either pre-parse or post-parse handlers.
 * </p>
 * <p>
 * For pre-parsing, non-text documents will simply be ignored and no
 * transformation will occur.  To find out if a document is a text-one, the
 * metadata {@link Importer#DOC_CONTENT_TYPE} value is used. By default
 * any content type starting with "text/" is considered text.  This default
 * behavior can be changed with the {@link #setContentTypeRegex(String)} method.
 * One must make sure to only match text documents to parsing exceptions.
 * </p>
 * <p>
 * For post-parsing, all documents are assumed to be text.
 * </p>
 * <p>
 * Sub-classes can restrict to which document to apply this transformation
 * based on document metadata (see {@link AbstractRestrictiveHandler}).
 * </p>
 * <p>
 * <p>Subclasses implementing {@link IXMLConfigurable} should allow this inner 
 * configuration:</p>
 * <pre>
 *  &lt;contentTypeRegex&gt;
 *      (regex to identify text content-types, overridding default)
 *  &lt;/contentTypeRegex&gt;
 *  &lt;restrictTo
 *          caseSensitive="[false|true]" &gt;
 *          property="(name of header/metadata name to match)"
 *      (regular expression of value to match)
 *  &lt;/restrictTo&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public abstract class AbstractCharStreamTransformer 
            extends AbstractTextRestrictiveHandler
            implements IDocumentTransformer {

    private static final long serialVersionUID = -7465364282740091371L;
    
    @Override
    public final void transformDocument(String reference, InputStream input,
            OutputStream output, Properties metadata, boolean parsed)
            throws IOException {
        
        if (!documentAccepted(reference, metadata, parsed)) {
            return;
        }
        InputStreamReader is = new InputStreamReader(input);
        OutputStreamWriter os = new OutputStreamWriter(output);
        transformTextDocument(reference, is, os, metadata, parsed);
        os.flush();
    }

    protected abstract void transformTextDocument(
            String reference, Reader input,
            Writer output, Properties metadata, boolean parsed)
            throws IOException;

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof AbstractCharStreamTransformer)) {
            return false;
        }
        return new EqualsBuilder().appendSuper(super.equals(other)).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode()).toHashCode();
    } 
}