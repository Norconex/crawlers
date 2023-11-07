/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.importer.response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.importer.ImporterException;
import com.norconex.importer.doc.Doc;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

//@Builder
@Data
@Accessors(chain = true)
public class ImporterResponse {

    //TODO have part of it immutable... response could be modified to
    // add children.. so maybe there are no risk to be able to change it all?


    public enum Status { SUCCESS, REJECTED, ERROR }

    public static final ImporterResponse[] EMPTY_RESPONSES = {};


    private Status status;
//    private final DocumentFilter filter;
    private Object rejectCause; // e.g., Condition, or handler.
    private ImporterException exception;
    private String description;

    private String reference;
    private Doc doc;
    @NonNull
//    @Singular
    private final List<ImporterResponse> nestedResponses = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Setter(value = AccessLevel.NONE)
    private ImporterResponse parentResponse;

//    public ImporterResponse(String reference, ImporterStatus status) {
//        this.reference = reference;
//        this.status = status;
//        doc = null;
//    }
//    public ImporterResponse(Doc doc) {
//        reference = doc.getReference();
//        this.doc = doc;
//        status = new ImporterStatus();
//    }
//
//    public Doc getDocument() {
//        return doc;
//    }

    public boolean isSuccess() {
        return status != null && status == Status.SUCCESS;
    }
    public boolean isRejected() {
        return status == Status.REJECTED;
    }
    public boolean isError() {
        return status == Status.ERROR;
    }
//
//    public ImporterResponse getParentResponse() {
//        return parentResponse;
//    }
//
//    public void addNestedResponse(ImporterResponse response) {
//        response.setParentResponse(this);
//        nestedResponses.add(response);
//    }
//    public void removeNestedResponse(String reference) {
//        ImporterResponse response = null;
//        for (ImporterResponse nestedResponse : nestedResponses) {
//            if (nestedResponse.getReference().equals(reference)) {
//                response = nestedResponse;
//            }
//        }
//        if (response == null) {
//            return;
//        }
//        nestedResponses.remove(response);
//        response.setParentResponse(null);
//    }
//
//    public ImporterResponse[] getNestedResponses() {
//        return nestedResponses.toArray(EMPTY_RESPONSES);
//    }

//    private void setParentResponse(ImporterResponse parentResponse) {
//        this.parentResponse = parentResponse;
//    }

    public List<ImporterResponse> getNestedResponses() {
        return Collections.unmodifiableList(nestedResponses);
    }

//    public static ImporterResponseBuilder builderFrom(
//            ImporterResponse from) {
//        return new ImporterResponseBuilder()
//                .description(from.getDescription())
//                .exception(from.getException())
//                .nestedResponses(from.getNestedResponses())
//                .doc(from.getDoc())
//                .reference(from.getReference())
//                .rejectCause(from.getRejectCause())
//                .parentResponse(from.getParentResponse())
//                .status(from.status)
//                ;
//    }

    public ImporterResponse setNestedResponses(
            List<ImporterResponse> nestedResponses) {
        CollectionUtil.setAll(this.nestedResponses, nestedResponses);
        this.nestedResponses.forEach(nr -> nr.parentResponse = ImporterResponse.this);
        return this;
    }
}
