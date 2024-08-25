/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.importer.handler.splitter;

import java.io.IOException;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.handler.BaseDocumentHandler;
import com.norconex.importer.handler.HandlerContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public abstract class AbstractDocumentSplitter<
        T extends BaseDocumentSplitterConfig>
        extends BaseDocumentHandler
        implements Configurable<T> {

    @Override
    public final void handle(HandlerContext docCtx) throws IOException {
        split(docCtx);
        if (!docCtx.childDocs().isEmpty()
                && getConfiguration().isDiscardOriginal()) {
            docCtx.rejectedBy(this);
        }
    }

    public abstract void split(HandlerContext docCtx)
            throws IOException;
}
