/* Copyright 2018-2024 Norconex Inc.
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
package com.norconex.importer.handler.splitter.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.splitter.BaseDocumentSplitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Split PDFs pages so each pages are treated as individual documents. May not
 * work on all PDFs (e.g., encrypted PDFs).
 * </p>
 *
 * <p>
 * The original PDF is kept intact. If you want to eliminate it to keep only
 * the split pages, make sure to filter it.  You can do so by filtering
 * out PDFs without one of these two fields added to each pages:
 * <code>document.pdf.pageNumber</code> or
 * <code>document.pdf.numberOfPages</code>.  A filtering example:
 * </p>
 *
 * {@nx.xml.example
 * <filter class="com.norconex.importer.handler.filter.impl.EmptyFilter"
 *         onMatch="exclude">
 *   <fieldMatcher matchWhole="true">document.pdf.pageNumber</fieldMatcher>
 * </filter>
 * }
 *
 * <p>
 * By default this splitter restricts its use to
 * <code>document.contentType</code> matching <code>application/pdf</code>.
 * </p>
 *
 * <p>Should be used as a pre-parse handler.</p>
 *
 * {@nx.xml.usage
 *  <handler class="com.norconex.importer.handler.splitter.impl.PDFPageSplitter">
 *    {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}

 *    <referencePagePrefix>
 *      (String to put before the page number is appended to the document
 *      reference. Default is "#".)
 *    </referencePagePrefix>
 *
 *  </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="PDFPageSplitter">
 *   <referencePagePrefix>#page</referencePagePrefix>
 * </handler>
 * }
 * <p>The above example will split PDFs and will append the page number
 * to the original PDF reference as "#page1", "#page2", etc.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class PDFPageSplitterConfig extends BaseDocumentSplitterConfig {

    private String referencePagePrefix =
            PDFPageSplitter.DEFAULT_REFERENCE_PAGE_PREFIX;

    /**
     * The matcher of content types to apply splitting on. No attempt to
     * split documents of any other content types will be made. Default is
     * <code>application/pdf</code>.
     * @param contentTypeMatcher content type matcher
     * @return content type matcher
     */
    private final TextMatcher contentTypeMatcher =
            TextMatcher.basic("application/pdf");

    /**
     * The matcher of content types to apply splitting on. No attempt to
     * split documents of any other content types will be made. Default is
     * <code>application/pdf</code>.
     * @param contentTypeMatcher content type matcher
     * @return this
     */
    public PDFPageSplitterConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }
}
