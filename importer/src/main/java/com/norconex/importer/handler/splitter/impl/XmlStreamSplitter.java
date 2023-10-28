/* Copyright 2020 Norconex Inc.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.norconex.commons.lang.io.CachedOutputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XMLUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.DocumentHandlerException;
import com.norconex.importer.handler.splitter.AbstractDocumentSplitter;
import com.norconex.importer.util.MatchUtil;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Splits XML document on a specific element.
 * </p>
 * <p>
 * This class is suited for large XML documents. It will read the XML as a
 * stream and split as it is read, preserving memory during parsing.
 * For this reason, element matching is not as flexible as DOM-based XML
 * parsers, such as {@link DomSplitter}, but is more efficient on large
 * documents.
 * </p>
 *
 * <h3>Element matching</h3>
 * <p>
 * To identify the element to split on, you give the full path to it from
 * the document root, where each element is separated by a forward slash.
 * Let's take this XML as an example:
 * </p>
 * <pre>
 * &lt;animals&gt;
 *   &lt;species name="mouse"&gt;
 *     &lt;animal&gt;
 *       &lt;name&gt;Itchy&lt;/name&gt;
 *       &lt;race&gt;cartoon&lt;/race&gt;
 *     &lt;/animal&gt;
 *   &lt;/species&gt;
 *   &lt;species name="cat"&gt;
 *     &lt;animal&gt;
 *       &lt;name&gt;Scratchy&lt;/name&gt;
 *       &lt;race&gt;cartoon&lt;/race&gt;
 *     &lt;/animal&gt;
 *   &lt;/species&gt;
 * &lt;/animals&gt;
 * </pre>
 * <p>
 * To split on <code>&lt;animal&gt;</code>, you would use this path:
 * </p>
 * <pre>
 * /animals/species/animal
 * </pre>
 *
 * <p>Should be used as a pre-parse handler.</p>
 *
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#xmlContentTypes(String)}.
 * You can specify your own restrictions to further narrow, or loosen what
 * documents this splitter applies to.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.splitter.impl.XMLStreamSplitter"
 *     path="(XML path)">
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="XMLStreamSplitter" path="/animals/species/animal" />
 * }
 *
 * <p>
 * The above example will create one document per animals, based on the
 * sample XML given above.
 * </p>
 *
 * @see DomSplitter
 */
@SuppressWarnings("javadoc")
@Slf4j
@Data
public class XmlStreamSplitter
        extends AbstractDocumentSplitter<XmlStreamSplitterConfig> {

    private final XmlStreamSplitterConfig configuration =
            new XmlStreamSplitterConfig();

    @Override
    public void split(DocContext docCtx) throws DocumentHandlerException {

        if (!MatchUtil.matchesContentType(
                configuration.getContentTypeMatcher(), docCtx.docRecord())) {
        }

        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            for (Entry<String, List<String>> en : docCtx.metadata().matchKeys(
                    configuration.getFieldMatcher()).entrySet()) {
                for (String val : en.getValue()) {
                    doSplit(docCtx, new ByteArrayInputStream(val.getBytes()));
                }
            }
        } else {
            // Body
            doSplit(docCtx, docCtx.input().inputStream());
        }
    }

    private void doSplit(DocContext docCtx, InputStream is)
            throws DocumentHandlerException {
        try (is) {
            var h = new XmlHandler(docCtx, Arrays.asList(StringUtils.split(
                    configuration.getPath(), '/')), docCtx.childDocs());
            XMLUtil.createSaxParserFactory().newSAXParser().parse(is, h);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new DocumentHandlerException(
                    "Could not split XML document: " + docCtx.reference(), e);
        }
    }

    class XmlHandler extends DefaultHandler {

        private final List<String> splitPath;
        private final List<Doc> splitDocs;
        private final DocContext xmlDoc;
        private final List<String> currentPath = new ArrayList<>();
        private PrintWriter w;
        private CachedOutputStream out;

        public XmlHandler(
                DocContext xmlDoc,
                List<String> splitPath,
                List<Doc> splitDocs) {
            this.xmlDoc = xmlDoc;
            this.splitDocs = splitDocs;
            this.splitPath = splitPath;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {

            currentPath.add(qName);

            if (currentPath.equals(splitPath)) {
                out = xmlDoc.streamFactory().newOuputStream();
                w = new PrintWriter(out);
            }

            if (w != null) {
                w.print('<');
                w.print(esc(qName));
                for (var i = 0; i < attributes.getLength(); i++) {
                    w.print(' ' + esc(attributes.getQName(i)) + "=\""
                            + esc(attributes.getValue(i)) + "\"");
                }
                w.print('>');
            }
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (w != null) {
                var ctnt = new String(ch, start, length);
                ctnt = ctnt.replaceFirst("^\\s+$", "");
                w.write(esc(ctnt));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            try {
                if (w != null) {
                    w.print("</" + esc(qName) + ">");
                    if (currentPath.equals(splitPath)) {
                        w.flush();
                        var childMeta = new Properties();
                        childMeta.loadFromMap(xmlDoc.metadata());
                        var embedRef = Integer.toString(splitDocs.size());
                        var childDoc = new Doc(
                                xmlDoc.reference() + "!" + embedRef,
                                out.getInputStream(),
                                childMeta);
                        w.close();
                        out = null;
                        w = null;
                        var childInfo = childDoc.getDocRecord();
                        childInfo.addEmbeddedParentReference(
                                xmlDoc.reference());
                        childMeta.set(
                                DocMetadata.EMBEDDED_REFERENCE, embedRef);
                        splitDocs.add(childDoc);
                    }
                }
            } catch (IOException e) {
                throw new SAXException("Cannot parse XML for document: "
                        + xmlDoc.reference(), e);
            }

            if (!currentPath.isEmpty()) {
                currentPath.remove(currentPath.size() - 1);
            }
        }
        @Override
        public void warning(SAXParseException e) throws SAXException {
            LOG.warn("XML warning: {}.", e.getMessage(), e);
        }
        @Override
        public void error(SAXParseException e) throws SAXException {
            LOG.error("XML error: {}.", e.getMessage(), e);
        }
        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            LOG.error("XML fatal error: {}.", e.getMessage(), e);
        }

        private String esc(String txt) {
            return StringEscapeUtils.escapeXml11(txt);
        }
    }
}
