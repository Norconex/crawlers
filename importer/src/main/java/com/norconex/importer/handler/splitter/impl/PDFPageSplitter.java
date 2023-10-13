/* Copyright 2018-2023 Norconex Inc.
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

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.splitter.DocumentSplitter;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.MatchUtil;

import lombok.Data;

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
public class PDFPageSplitter
        implements DocumentSplitter, Configurable<PDFPageSplitterConfig> {

    public static final String DOC_PDF_PAGE_NO = "document.pdf.pageNumber";
    public static final String DOC_PDF_TOTAL_PAGES =
            "document.pdf.numberOfPages";

    public static final String DEFAULT_REFERENCE_PAGE_PREFIX = "#";

    private final PDFPageSplitterConfig configuration =
            new PDFPageSplitterConfig();

    @Override
    public List<Doc> splitDocument(HandlerDoc doc, InputStream docInput,
            OutputStream docOutput, ParseState parseState)
            throws ImporterHandlerException {

        if (!MatchUtil.matchesContentType(
                configuration.getContentTypeMatcher(), doc.getDocRecord())) {
            return List.of();
        }

        List<Doc> pageDocs = new ArrayList<>();

        // Make sure we are not splitting a page that was already split
        if (doc.getMetadata().getInteger(DOC_PDF_PAGE_NO, 0) > 0) {
            return pageDocs;
        }

        try (var document = PDDocument.load(docInput)) {

            // Make sure we are not splitting single pages.
            if (document.getNumberOfPages() <= 1) {
                doc.getMetadata().set(DOC_PDF_PAGE_NO, 1);
                doc.getMetadata().set(DOC_PDF_TOTAL_PAGES, 1);
                return pageDocs;
            }

            var splitter = new Splitter();
            var splittedDocuments = splitter.split(document);
            var pageNo = 0;
            for (PDDocument page : splittedDocuments) {
                pageNo++;

                var pageRef = doc.getReference()
                        + trimToEmpty(configuration.getReferencePagePrefix())
                        + pageNo;

                // metadata
                var pageMeta = new Properties();
                pageMeta.loadFromMap(doc.getMetadata());

                var pageInfo = new DocRecord(pageRef);

                pageMeta.set(DocMetadata.EMBEDDED_REFERENCE,
                        Integer.toString(pageNo));


                pageInfo.addEmbeddedParentReference(doc.getReference());

                pageMeta.set(DOC_PDF_PAGE_NO, pageNo);
                pageMeta.set(DOC_PDF_TOTAL_PAGES, document.getNumberOfPages());

                // a single page should not be too big to store in memory
                var os = new ByteArrayOutputStream();
                try(page) {
                    page.save(os);
                }
                var pageDoc = new Doc(
                        pageInfo,
                        doc.getStreamFactory().newInputStream(
                                os.toInputStream()),
                        pageMeta);
                pageDocs.add(pageDoc);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Could not split PDF: " + doc.getReference(), e);
        }
        return pageDocs;
    }
}
