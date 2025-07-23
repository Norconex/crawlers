/* Copyright 2020-2025 Norconex Inc.
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
 * Minimum information required to uniquely identify a document
 * or define its nature.
 */
@Data
public class DocContext {

    @NonNull
    private String reference = null;
    private ContentType contentType;
    private Charset charset;

    /**
     * If this is an embedded document, holds the chain of parent
     * references up to the immediate parent (does not include this
     * signature reference).
     */
    @ToString.Exclude
    private List<String> parentReferences = new ArrayList<>();

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

    public List<String> getParentReferences() {
        return Collections.unmodifiableList(parentReferences);
    }

    public void setParentReferences(
            List<String> parentReferences) {
        CollectionUtil.setAll(
                this.parentReferences, parentReferences);
    }

    public void addParentReference(String parentReference) {
        parentReferences.add(parentReference);
    }

    //MAYBE: use this new method instead of having clone functional
    //  interface on Crawler class.
    public DocContext withReference(String reference,
            DocContext docRecord) {
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
