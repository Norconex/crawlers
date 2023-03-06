/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.web.link;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.util.CharsetUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Base class for link extraction from text documents, providing common
 * configuration settings such as being able to apply extraction to specific
 * documents only, and being able to specify one or more metadata fields
 * from which to grab the text for extracting links.
 * </p>
 *
 * <p>
 * Not suitable for binary files.
 * </p>
 *
 * <p>
 * Subclasses inherit the following:
 * </p>
 *
 * {@nx.xml.usage
 * {@nx.include com.norconex.crawler.web.link.AbstractLinkExtractor@nx.xml.usage}
 *
 * <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *   (optional expression for fields used for links extraction instead
 *    of the document stream)
 * </fieldMatcher>
 * }
 *
 * {@nx.xml.example
 * {@nx.include com.norconex.importer.handler.AbstractImporterHandler@nx.xml.example}
 * }
 * <p>
 * The above will apply to any content type starting with "text/".
 * </p>
 *
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractTextLinkExtractor extends AbstractLinkExtractor {

    private TextMatcher fieldMatcher = new TextMatcher();

    @Override
    public final void extractLinks(Set<Link> links, CrawlDoc doc)
            throws IOException {

        var hdoc = new HandlerDoc(doc);
        if (fieldMatcher.getPattern() == null) {
            try (var reader = toReader(doc)) {
                extractTextLinks(links, hdoc, reader);
            }
        } else {
            for (String value : new HashSet<>(doc.getMetadata().matchKeys(
                    fieldMatcher).valueList())) {
                try (var reader = toReader(value)) {
                    extractTextLinks(links, hdoc, reader);
                }
            }
        }
    }

    public abstract void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException;

    private Reader toReader(CrawlDoc doc) throws IOException {
        var charset = CharsetUtil.detectCharsetIfBlank(
                doc.getDocRecord().getContentEncoding(), doc.getInputStream());
        return IOUtils.buffer(
                new InputStreamReader(doc.getInputStream(), charset));
    }
    private Reader toReader(String value) {
        return IOUtils.buffer(new StringReader(value));
    }



    /**
     * Gets field matcher identifying fields holding content used for
     * link extraction.  Default is <code>null</code>, using the document
     * content stream instead.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Gets field matcher identifying fields holding content used for
     * link extraction.  Default is <code>null</code>, using the document
     * content stream instead.
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    public final void loadLinkExtractorFromXML(XML xml) {
        loadTextLinkExtractorFromXML(xml);
        var fmXML = xml.getXML("fieldMatcher");
        if (fmXML != null) {
            fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        }
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml XML configuration
     */
    protected abstract void loadTextLinkExtractorFromXML(XML xml);

    @Override
    protected final void saveLinkExtractorToXML(XML xml) {
        saveTextLinkExtractorToXML(xml);
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
    }

    /**
     * Saves configuration settings specific to the implementing class.
     * @param xml the XML
     */
    protected abstract void saveTextLinkExtractorToXML(XML xml);
}
