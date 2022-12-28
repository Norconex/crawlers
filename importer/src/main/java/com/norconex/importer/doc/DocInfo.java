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
package com.norconex.importer.doc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringSummary;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;

import lombok.Data;
import lombok.NonNull;

/**
 * Important information about a document that has specific meaning and purpose
 * for processing by the Importer and needs to be referenced in a
 * consistent way.
 * Those are information needing to be tracked independently from metadata,
 * which can be anything, and can be modified at will by implementors
 * (thus not always constant).
 * In most cases where light caching is involved, implementors can cache this
 * class data as opposed to caching {@link Doc}.
 */
@Data
public class DocInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    //TODO DocProperties?  misleading because of Properties
    //TODO DocAttributes?  seems distinct enough.  Use this.

    //MAYBE:
    // - private Locale locale?
    // - create interface IDocInfo?
    // - add parent reference info here???
    // - remove most Properties method and put them here.
    // - track original vs final here (useful for tracking deletions
    //   under a modified reference (and have dynamic committer targets).
    // - make final?

    @NonNull private String reference = null;
    private ContentType contentType;
    private String contentEncoding;

    //MAYBE: remove prefix "embedded" and just keep parent* ?

    // trail of parent references (first one is root/top-level)
    @ToStringSummary
    private List<String> embeddedParentReferences = new ArrayList<>();


    //MAYBE: above should just be parentReferences and below should be metadata?

    //MAYBE: add a method toMetadata or "asMetadata" as opposed to have
    // external conversion

    /**
     * Constructor.
     */
    public DocInfo() {}

    /**
     * Constructor.
     * @param reference document reference
     */
    public DocInfo(String reference) {
        setReference(reference);
    }
    /**
     * Copy constructor.
     * @param docInfo document details to copy
     */
    public DocInfo(@NonNull DocInfo docInfo) {
        copyFrom(docInfo);
    }

    public List<String> getEmbeddedParentReferences() {
        return Collections.unmodifiableList(embeddedParentReferences);
    }
    public void setEmbeddedParentReferences(
            List<String> embeddedParentReferences) {
        CollectionUtil.setAll(
                this.embeddedParentReferences, embeddedParentReferences);
    }
    public void addEmbeddedParentReference(String embeddedParentReference) {
        embeddedParentReferences.add(embeddedParentReference);
    }

    //MAYBE: use this new method instead of having clone functional
    //  interface on Crawler class.
    public DocInfo withReference(String reference, DocInfo docInfo) {
        var newDocInfo = new DocInfo(docInfo);
        newDocInfo.setReference(reference);
        return newDocInfo;
    }

    public void copyTo(DocInfo target) {
        BeanUtil.copyProperties(target, this);
    }
    public void copyFrom(DocInfo source) {
        BeanUtil.copyProperties(this, source);
    }
}
