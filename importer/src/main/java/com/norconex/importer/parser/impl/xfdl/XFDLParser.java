/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.importer.parser.impl.xfdl;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.DocumentParser;


/**
 * Parser for PureEdge Extensible Forms Description Language (XFDL).
 * This parser extracts any text found in the XFDL XML, whether that XML
 * is Base64 encoded or just plain XML (two possible format for XFDL).
 *
 */
public class XFDLParser implements DocumentParser {

    private static final char[] MAGIC_BASE64 =
          "application/vnd.xfdl;content-encoding=\"base64-gzip\"".toCharArray();

    @Override
    public List<Doc> parseDocument(Doc doc,
            Writer output) throws DocumentParserException {
        try {
            //TODO have a generic utility method for this?
            BufferedInputStream is =
                    new BufferedInputStream(doc.getInputStream());
            CharsetDetector detector = new CharsetDetector();
            detector.enableInputFilter(true);
            detector.setText(is);
            CharsetMatch match = detector.detect();
            String charset = StandardCharsets.UTF_8.toString();
            if (match != null && Charset.isSupported(match.getName())) {
                charset = match.getName();
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, charset));
            parse(reader, output, doc.getMetadata());
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new DocumentParserException(
                    "Could not parse " + doc.getReference(), e);
        }
        return null;
    }

    private void parse(
            BufferedReader reader, Writer out, Properties metadata)
            throws IOException, ParserConfigurationException, SAXException {
        reader.mark(MAGIC_BASE64.length);
        char[] signature = new char[MAGIC_BASE64.length];
        int num = reader.read(signature);
        reader.reset();
        if (num == -1) {
            return;
        }

        //--- Create XML DOM ---
        DocumentBuilder docBuilder =
                DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document dom = null;
        if (Arrays.equals(signature, MAGIC_BASE64)) {
            // skip first line
            reader.readLine();

            // un-encode first
            byte[] compressedContent =
                    Base64.decodeBase64(IOUtils.toString(reader));
            // deal with compression
            try (InputStream is = new GZIPInputStream(
                    new ByteArrayInputStream(compressedContent))) {
                dom = docBuilder.parse(is);
            }
        } else {
            dom = docBuilder.parse(new InputSource(reader));
        }
        parseXML(dom, out, metadata);
    }

    //TODO use a SAX parser instead for increased efficiency.
    private void parseXML(Document doc, Writer out, Properties metadata)
            throws IOException {

        // Grab the title
        NodeList xmlTitles = doc.getElementsByTagName("title");
        if (xmlTitles != null && xmlTitles.getLength() > 0) {
            Node titleItem = xmlTitles.item(0);
            if (titleItem instanceof Element) {
                metadata.add("title",
                        ((Element) titleItem).getTextContent());
            }
        }

        boolean isEmpty = true;
        NodeList xmlFields = doc.getElementsByTagName("field");
        for (int i = 0; i < xmlFields.getLength(); i++) {
            if (xmlFields.item(i) instanceof Element) {
                NodeList children = xmlFields.item(i).getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node childItem = children.item(j);
                    if (childItem instanceof Element) {
                        Element tag = ((Element) childItem);
                        String tagName = tag.getTagName();
                        if ("value".equalsIgnoreCase(tagName)) {
                            isEmpty = writeValue(
                                    out, tag.getTextContent(), isEmpty);
                        }
                    }
                }
            }
        }
    }

    private boolean writeValue(Writer out, String value, boolean isOuputEmpty)
            throws IOException {
        boolean stillEmpty = true;
        if (StringUtils.isNotBlank(value)) {
            if (isOuputEmpty) {
                // Add space at the beginning to avoid false
                // document type recognition like MILESTONE
                // for docs that start with MILES
                out.write(" ");
                stillEmpty = false;
            }
            out.append(value).append("\n");
        }
        return stillEmpty;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof XFDLParser)) {
            return false;
        }
        return new EqualsBuilder()
                .append(getClass(), other.getClass())
                .isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getClass().getCanonicalName().hashCode())
                .toHashCode();
    }
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .toString();
    }
}
