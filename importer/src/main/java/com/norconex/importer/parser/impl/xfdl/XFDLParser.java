/* Copyright 2015-2023 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XMLUtil;
import com.norconex.importer.charset.CharsetDetector;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.parser.DocumentParser;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.ParseOptions;

import lombok.Data;
import lombok.NonNull;


/**
 * Parser for PureEdge Extensible Forms Description Language (XFDL).
 * This parser extracts any text found in the XFDL XML, whether that XML
 * is Base64 encoded or just plain XML (two possible format for XFDL).
 */
@Data
public class XFDLParser implements DocumentParser {

    private static final char[] MAGIC_BASE64 =
          "application/vnd.xfdl;content-encoding=\"base64-gzip\"".toCharArray();

    @Override
    public void init(@NonNull ParseOptions parseOptions)
            throws DocumentParserException {
        //NOOP
    }

    @Override
    public List<Doc> parseDocument(Doc doc,
            Writer output) throws DocumentParserException {
        Charset charset;
        try {
            charset = CharsetDetector.builder()
                    .priorityCharset(() -> doc.getDocRecord().getCharset())
                    .build()
                    .detect(doc);
        } catch (IOException e) {
            charset = UTF_8;
        }
        try (var is = new BufferedInputStream(doc.getInputStream());
                var reader = new BufferedReader(
                        new InputStreamReader(is, charset));) {
            parse(reader, output, doc.getMetadata());
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new DocumentParserException(
                    "Could not parse " + doc.getReference(), e);
        }
        return Collections.emptyList();
    }

    private void parse(
            BufferedReader reader, Writer out, Properties metadata)
            throws IOException, ParserConfigurationException, SAXException {
        reader.mark(MAGIC_BASE64.length);
        var signature = new char[MAGIC_BASE64.length];
        var num = reader.read(signature);
        reader.reset();
        if (num == -1) {
            return;
        }

        //--- Create XML DOM ---
        var docBuilder =
                XMLUtil.createDocumentBuilderFactory().newDocumentBuilder();
        Document dom = null;
        if (Arrays.equals(signature, MAGIC_BASE64)) {
            // skip first line
            reader.readLine();  //NOSONAR no use for storing the output

            // un-encode first
            var compressedContent =
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
        var xmlTitles = doc.getElementsByTagName("title");
        if (xmlTitles != null && xmlTitles.getLength() > 0) {
            var titleItem = xmlTitles.item(0);
            if (titleItem instanceof Element titleEl) {
                metadata.add("title", titleEl.getTextContent());
            }
        }

        var isEmpty = true;
        var xmlFields = doc.getElementsByTagName("field");
        for (var i = 0; i < xmlFields.getLength(); i++) {
            if (xmlFields.item(i) instanceof Element) {
                var children = xmlFields.item(i).getChildNodes();
                for (var j = 0; j < children.getLength(); j++) {
                    var childItem = children.item(j);
                    if (childItem instanceof Element tag) {
                        var tagName = tag.getTagName();
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
        var stillEmpty = true;
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
}
