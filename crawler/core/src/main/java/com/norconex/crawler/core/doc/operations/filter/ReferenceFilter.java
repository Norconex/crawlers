/* Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.filter;

/**
 * <p>
 * Filter a document based on its reference, before its properties or content
 * gets read or otherwise acquired.
 * </p>
 * <p>
 * <b>Note to implementors:</b> It is highly recommended to overwrite the
 * <code>toString()</code> method
 * to representing this filter properly in human-readable form
 * (e.g. for logging by a crawler).
 * It is a good idea to include specifics of this filter so crawler users
 * can know exactly why documents got accepted/rejected rejected if need be.
 * </p>
 */
@FunctionalInterface
public interface ReferenceFilter {

    /**
     * Whether to accept this reference.
     * @param reference the reference to accept/reject
     * @return <code>true</code> if accepted, <code>false</code> otherwise
     */
    boolean acceptReference(String reference);

}
