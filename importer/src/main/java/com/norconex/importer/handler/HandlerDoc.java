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
package com.norconex.importer.handler;

import java.util.Objects;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.builder.ToStringSummary;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;

import lombok.EqualsAndHashCode;

/**
 * Lighter version of {@link Doc} which leaves content out to let each
 * handler dictate how content should be referenced.
 */
@EqualsAndHashCode
public class HandlerDoc {

    @ToStringSummary
    private final Doc doc;

    public HandlerDoc(Doc doc) {
        this.doc = Objects.requireNonNull(doc, "'doc' must not be null.");
    }

    public DocRecord getDocInfo() {
        return doc.getDocInfo();
    }
    public Properties getMetadata() {
        return doc.getMetadata();
    }
    public String getReference() {
        return doc.getReference();
    }
    public CachedStreamFactory getStreamFactory() {
        return doc.getStreamFactory();
    }
    @Override
    public String toString() {
        ReflectionToStringBuilder b = new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE);
        b.setExcludeNullValues(true);
        return b.toString();

    }
}
