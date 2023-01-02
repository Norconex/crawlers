/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.importer.doc;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ImporterRuntimeException;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/**
 * A document being imported.
 */
@EqualsAndHashCode
@ToString
public class Doc {

    //MAYBE: still allow String reference in constructor and create?
    //MAYBE: add parent reference info here?

    private final DocRecord docRecord;
    private final Properties metadata;
    @ToString.Exclude
    private CachedInputStream content;

    public Doc(String reference, CachedInputStream content) {
        this(reference, content, null);
    }
    public Doc(@NonNull String reference, CachedInputStream content,
            Properties metadata) {
        this(new DocRecord(reference), content, metadata);
    }

    /**
     * Creates a blank importer document using the supplied input stream
     * to handle content.
     * The document reference automatically gets added to the metadata.
     * @param docRecord document details
     * @param content content input stream
     */
    public Doc(DocRecord docRecord, CachedInputStream content) {
        this(docRecord, content, null);
    }
    /**
     * Creates a blank importer document using the supplied input stream
     * to handle content.
     * @param docRecord document details
     * @param content content input stream
     * @param metadata importer document metadata
     */
    public Doc(@NonNull DocRecord docRecord, @NonNull CachedInputStream content,
            Properties metadata) {
        this.docRecord = docRecord;
        this.content = content;
        if (metadata == null) {
            this.metadata = new Properties();
        } else {
            this.metadata = metadata;
        }
    }

    /**
     * Disposes of any resources associated with this document (like
     * disk or memory cache).
     * @throws IOException could not dispose of document resources
     */
    //MAYBE: implement "closeable" instead?
    public synchronized void dispose() throws IOException {
        content.dispose();
    }

    public CachedInputStream getInputStream() {
        content.rewind();
        return content;
    }
    public void setInputStream(@NonNull InputStream inputStream) {
        if (content == inputStream) {
            return;
        }
        try {
            content.dispose();
            if (inputStream instanceof CachedInputStream cis) {
                content = cis;
            } else {
                try (var os = content.getStreamFactory().newOuputStream()) {
                    IOUtils.copy(inputStream, os);
                    content = os.getInputStream();
                }
            }
        } catch (IOException e) {
            throw new ImporterRuntimeException(
                    "Could set content input stream.", e);
        }
    }

    public CachedStreamFactory getStreamFactory() {
        return content.getStreamFactory();
    }

    public DocRecord getDocInfo() {
        return docRecord;
    }

    public Properties getMetadata() {
        return metadata;
    }

    /**
     * Gets the document reference. Same as
     * invoking <code>getDocInfo().getReference()</code>.
     * @return reference
     * @see #getDocInfo()
     */
    public String getReference() {
        return docRecord.getReference();
    }
}
