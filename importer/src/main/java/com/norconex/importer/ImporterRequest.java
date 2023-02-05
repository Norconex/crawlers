/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer;

import java.io.InputStream;
import java.nio.file.Path;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * An Importer request, unique for each document to be imported.
 * A <code>null</code> is accepted for the file or input stream. This can
 * sometimes be useful when dealing with meta data only.
 * A <code>null</code> reference can also be provided, in which case the
 * file name will be used as the reference when a file is provided, or an
 * empty string when dealing with an input stream.  It is recommended
 * to pass a reference that represents your document when possible (at least
 * just the filename, including appropriate extension).
 */
@EqualsAndHashCode
@ToString
public class ImporterRequest {

    private final InputStream inputStream;
    private final Path file;
    private ContentType contentType;
    private String contentEncoding;
    private Properties metadata;
    private String reference;

    public ImporterRequest(InputStream inputStream) {
        this.inputStream = inputStream;
        file = null;
    }
    public ImporterRequest(Path file) {
        inputStream = null;
        this.file = file;
    }

    public ContentType getContentType() {
        return contentType;
    }
    public ImporterRequest setContentType(ContentType contentType) {
        this.contentType = contentType;
        return this;
    }
    public String getContentEncoding() {
        return contentEncoding;
    }
    public ImporterRequest setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return this;
    }
    public Properties getMetadata() {
        return metadata;
    }
    public ImporterRequest setMetadata(Properties metadata) {
        this.metadata = metadata;
        return this;
    }
    public String getReference() {
        return reference;
    }
    public ImporterRequest setReference(String reference) {
        this.reference = reference;
        return this;
    }
    public InputStream getInputStream() {
        return inputStream;
    }
    public Path getFile() {
        return file;
    }
}
