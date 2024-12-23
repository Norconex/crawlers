/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.committer.core.fs.impl;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.fs.AbstractFsCommitter;
import com.norconex.commons.lang.xml.EnhancedXmlStreamWriter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Commits documents to XML files. There are two kinds of entries
 * representing document upserts and deletions.
 * </p>
 * <p>
 * The generated file entries are never updated. Sending a modified document
 * with the same reference (typically unlikely) will create a new entry and
 * won't modify any existing ones. You can think of the generated files as a
 * set of commit instructions.
 * </p>
 *
 * <h2>Generated XML format:</h2>
 * <pre>
 * &lt;docs&gt;
 *   &lt;!-- Document additions: --&gt;
 *   &lt;upsert&gt;
 *     &lt;reference&gt;(document reference, e.g., URL)&lt;/reference&gt;
 *     &lt;metadata&gt;
 *       &lt;meta name="(meta field name)"&gt;(value)&lt;/meta&gt;
 *       &lt;meta name="(meta field name)"&gt;(value)&lt;/meta&gt;
 *       &lt;!-- meta is repeated for each metadata fields --&gt;
 *     &lt;/metadata&gt;
 *     &lt;content&gt;
 *       (document content goes here)
 *     &lt;/content&gt;
 *   &lt;/upsert&gt;
 *   &lt;upsert&gt;
 *     &lt;!-- upsert element is repeated for each additions --&gt;
 *   &lt;/upsert&gt;
 *
 *   &lt;!-- Document deletions: --&gt;
 *   &lt;delete&gt;
 *     &lt;reference&gt;(document reference, e.g., URL)&lt;/reference&gt;
 *     &lt;metadata&gt;
 *       &lt;meta name="(meta field name)"&gt;(value)&lt;/meta&gt;
 *       &lt;meta name="(meta field name)"&gt;(value)&lt;/meta&gt;
 *       &lt;!-- meta is repeated for each metadata fields --&gt;
 *     &lt;/metadata&gt;
 *   &lt;/delete&gt;
 *   &lt;delete&gt;
 *     &lt;!-- delete element is repeated for each deletions --&gt;
 *   &lt;/delete&gt;
 * </pre>
 * }
 */
@EqualsAndHashCode
@ToString
public class XmlFileCommitter extends
        AbstractFsCommitter<EnhancedXmlStreamWriter, XmlFileCommitterConfig> {

    @Getter
    private final XmlFileCommitterConfig configuration =
            new XmlFileCommitterConfig();

    @Override
    protected String getFileExtension() {
        return "xml";
    }

    @Override
    protected EnhancedXmlStreamWriter createDocWriter(Writer writer)
            throws IOException {
        var xml = new EnhancedXmlStreamWriter(
                writer, false, configuration.getIndent());
        xml.writeStartDocument();
        xml.writeStartElement("docs");
        return xml;
    }

    @Override
    protected void writeUpsert(
            EnhancedXmlStreamWriter xml,
            UpsertRequest upsertRequest) throws IOException {

        xml.writeStartElement("upsert");

        xml.writeElementString("reference", upsertRequest.getReference());

        xml.writeStartElement("metadata");
        for (Entry<String, List<String>> entry : upsertRequest.getMetadata()
                .entrySet()) {
            for (String value : entry.getValue()) {
                xml.writeStartElement("meta");
                xml.writeAttributeString("name", entry.getKey());
                xml.writeCharacters(value);
                xml.writeEndElement();
            }
        }
        xml.writeEndElement(); // </metadata>

        xml.writeElementString(
                "content", IOUtils.toString(
                        upsertRequest.getContent(), StandardCharsets.UTF_8)
                        .trim());

        xml.writeEndElement(); // </upsert>
        xml.flush();
    }

    @Override
    protected void writeDelete(
            EnhancedXmlStreamWriter xml,
            DeleteRequest deleteRequest) throws IOException {

        xml.writeStartElement("delete");

        xml.writeElementString("reference", deleteRequest.getReference());

        xml.writeStartElement("metadata");
        for (Entry<String, List<String>> entry : deleteRequest.getMetadata()
                .entrySet()) {
            for (String value : entry.getValue()) {
                xml.writeStartElement("meta");
                xml.writeAttributeString("name", entry.getKey());
                xml.writeCharacters(value);
                xml.writeEndElement(); //meta
            }
        }
        xml.writeEndElement(); // </metadata>

        xml.writeEndElement(); // </delete>
        xml.flush();
    }

    @Override
    protected void closeDocWriter(EnhancedXmlStreamWriter xml)
            throws IOException {
        if (xml != null) {
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        }
    }
}
