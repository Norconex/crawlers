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
package com.norconex.importer.handler.parser.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.buffer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
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
import com.norconex.importer.handler.BaseDocumentHandler;
import com.norconex.importer.handler.DocContext;

import lombok.Data;


/**
 * Parser for PureEdge Extensible Forms Description Language (XFDL).
 * This parser extracts any text found in the XFDL XML, whether that XML
 * is Base64 encoded or just plain XML (two possible format for XFDL).
 */
@Data
public class XFDLParser extends BaseDocumentHandler {

    private static final char[] MAGIC_BASE64 =
          "application/vnd.xfdl;content-encoding=\"base64-gzip\"".toCharArray();

    @Override
    public void handle(DocContext ctx) throws IOException {
        Charset charset;
        try {
            charset = CharsetDetector.builder()
                    .priorityCharset(() -> ctx.docRecord().getCharset())
                    .build()
                    .detect(ctx.input().inputStream());
        } catch (IOException e) {
            charset = UTF_8;
        }
        try (var reader = buffer(
                new InputStreamReader(ctx.input().inputStream(), charset));
                var writer = ctx.output().writer()) {
            parse(reader, writer, ctx.metadata());
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IOException("Could not parse " + ctx.reference(), e);
        }
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
