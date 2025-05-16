/* Copyright 2020-2024 Norconex Inc.
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;

import lombok.Data;
import lombok.NonNull;
import lombok.ToString;

/**
 * Important information about a document that has specific meaning and purpose
 * for processing by the Importer and needs to be stored/referenced in a
 * consistent way. In some contexts, a document record is a lightweight,
 * cacheable version of a document ({@link Doc}).
 */
@Data
//@JsonAutoDetect(
//    getterVisibility = JsonAutoDetect.Visibility.NONE,
//    isGetterVisibility = JsonAutoDetect.Visibility.NONE
//)
public class DocContext implements Serializable {

    private static final long serialVersionUID = 1L;

    // DocRecord?

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

    @NonNull
    private String reference = null;
    private ContentType contentType;
    private Charset charset;

    //MAYBE: remove prefix "embedded" and just keep parent* ?

    // trail of parent references (first one is root/top-level)
    //    @ToStringSummary
    @ToString.Exclude
    private List<String> embeddedParentReferences = new ArrayList<>();

    //MAYBE: above should just be parentReferences and below should be metadata?

    //MAYBE: add a method toMetadata or "asMetadata" as opposed to have
    // external conversion

    /**
     * Constructor.
     */
    public DocContext() {
    }

    /**
     * Constructor.
     * @param reference document reference
     */
    public DocContext(String reference) {
        setReference(reference);
    }

    /**
     * Copy constructor.
     * @param docRecord document details to copy
     */
    public DocContext(@NonNull DocContext docRecord) {
        copyFrom(docRecord);
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
    public DocContext withReference(String reference, DocContext docRecord) {
        var newDocInfo = new DocContext(docRecord);
        newDocInfo.setReference(reference);
        return newDocInfo;
    }

    public void copyTo(DocContext target) {
        BeanUtil.copyProperties(target, this);
    }

    public void copyFrom(DocContext source) {
        BeanUtil.copyProperties(this, source);
    }
}
