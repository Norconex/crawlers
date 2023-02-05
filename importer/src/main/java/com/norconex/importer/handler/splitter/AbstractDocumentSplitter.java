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
package com.norconex.importer.handler.splitter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.AbstractImporterHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Base class for splitters.</p>
 *
 * <p>Subclasses inherit this {@link XMLConfigurable} configuration:</p>
 * {@nx.xml
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * }
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractDocumentSplitter extends AbstractImporterHandler
            implements DocumentSplitter {

    @Override
    public final List<Doc> splitDocument(
            HandlerDoc doc,
            InputStream docInput,
            OutputStream docOutput,
            ParseState parseState)
                    throws ImporterHandlerException {

        if (!isApplicable(doc, parseState)) {
            return Collections.emptyList();
        }
        return splitApplicableDocument(
                doc, docInput, docOutput, parseState);
    }

    protected abstract List<Doc> splitApplicableDocument(
            HandlerDoc doc, InputStream input, OutputStream output,
            ParseState parseState)
                    throws ImporterHandlerException;
}