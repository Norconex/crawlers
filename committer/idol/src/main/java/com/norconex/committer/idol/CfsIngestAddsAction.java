/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.committer.idol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.equalsAny;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.url.HttpURL;

/* CFS "ingest" "adds" action:
 *
 *   http://server:port/action=ingest&adds=[URLencodedXML]
 *
 * The [URLencodedXML] is of this format:
 *
 *   <adds>
 *     <add>
 *       <document>
 *         <reference>http://www.example.com/</reference>
 *         <metadata name="Field1" value="Value1"/>
 *         <metadata name="Field2" value="Value2"/>
 *       </document>
 *       <source content="...base-64 string..."/>
 *     </add>
 *   </adds>
 *
 * Reference material:
 *
 * https://www.microfocus.com/documentation/idol/IDOL_12_7/
 * CFS_12.7_Documentation/Help/#Actions/CFS/Ingest.htm%3FTocPath%3D
 * Reference%7CActions%7CConnector%2520Framework%2520Server%7C_____2
 *
 * https://www.microfocus.com/documentation/idol/IDOL_12_0/CFS/Guides/pdf/
 * English/ConnectorFrameworkServer_12.0_Admin_en.pdf (Page 40-41)
 */
class CfsIngestAddsAction implements IdolIndexAction {

    private final IdolCommitterConfig config;

    CfsIngestAddsAction(IdolCommitterConfig config) {
        this.config = config;
    }

    @Override
    public URL url(List<CommitterRequest> batch, HttpURL url)
            throws CommitterException {
        try {
            url.getQueryString().set("action", "ingest");
            url.getQueryString().set("adds", toCfsXmlBatch(batch));
            return url.toURL();
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not convert committer batch to CFS XML.", e);
        }
    }

    @Override
    public void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException {
        //NOOP
    }

    private String toCfsXmlBatch(List<CommitterRequest> batch)
            throws XMLStreamException, CommitterException, IOException {
        var w = new StringWriter();
        var factory = XMLOutputFactory.newInstance();
        var xml = factory.createXMLStreamWriter(w);
        xml.writeStartElement("adds");
        for (CommitterRequest upsert : batch) {
            writeDocUpsert(xml, (UpsertRequest) upsert);
        }
        xml.writeEndElement();
        xml.flush();
        xml.close();
        return w.toString();
    }

    private void writeDocUpsert(XMLStreamWriter xml, UpsertRequest req)
            throws XMLStreamException, CommitterException, IOException {
        var refField = config.getSourceReferenceField();
        var contentField = config.getSourceContentField();

        xml.writeStartElement("add");
        xml.writeStartElement("document");

        //--- Document reference ---
        var ref = req.getReference();
        if (StringUtils.isNotBlank(refField)) {
            ref = req.getMetadata().getString(refField);
            if (StringUtils.isBlank(ref)) {
                throw new CommitterException(
                        ("Source reference field '%s' has no value for "
                                + "document: %s").formatted(
                                        refField, req.getReference()));
            }
        }
        xml.writeStartElement("reference");
        xml.writeCharacters(ref);
        xml.writeEndElement();

        //--- Document metadata ---
        for (Entry<String, List<String>> en : req.getMetadata().entrySet()) {
            var name = en.getKey();
            var values = en.getValue();
            if (values == null || equalsAny(name, refField, contentField)) {
                continue;
            }
            for (String value : values) {
                xml.writeStartElement("metadata");
                xml.writeAttribute("name", name);
                xml.writeAttribute("value", value);
                xml.writeEndElement();
            }
        }

        //--- IDOL Database ---
        if (StringUtils.isNotBlank(config.getDatabaseName())) {
            xml.writeStartElement("metadata");
            xml.writeAttribute("name", "DREDBNAME");
            xml.writeAttribute("value", config.getDatabaseName());
            xml.writeEndElement();
        }

        xml.writeEndElement(); // end "document"

        //--- Document content ---
        var content = IdolUtil.resolveDreContent(req, contentField);
        xml.writeStartElement("source");
        xml.writeAttribute(
                "content",
                Base64.encodeBase64String(content.getBytes(UTF_8)));
        xml.writeEndElement();

        xml.writeEndElement(); // end "add"
    }
}
