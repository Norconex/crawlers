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
import com.norconex.committer.core.fs.AbstractFSCommitter;
import com.norconex.commons.lang.xml.EnhancedXmlStreamWriter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Commits documents to XML files.  There are two kinds of document
 * representations: upserts and deletions.
 * </p>
 * <p>
 * If you request to split upserts and deletions into separate files,
 * the generated files will start with "upsert-" (for additions/modifications)
 * and "delete-" (for deletions).
 * </p>
 * <p>
 * The generated files are never updated.  Sending a modified document with the
 * same reference will create a new entry and won't modify any existing ones.
 * You can think of the generated files as a set of commit instructions.
 * </p>
 * <p>
 * The generated XML file names are made of a timestamp and a sequence number.
 * </p>
 * <p>
 * You have the option to give a prefix or suffix to
 * files that will be created (default does not add any).
 * </p>
 *
 * <h3>Generated XML format:</h3>
 * {@nx.xml
 * <docs>
 *   <!-- Document additions: -->
 *   <upsert>
 *     <reference>(document reference, e.g., URL)</reference>
 *     <metadata>
 *       <meta name="(meta field name)">(value)</meta>
 *       <meta name="(meta field name)">(value)</meta>
 *       <!-- meta is repeated for each metadata fields -->
 *     </metadata>
 *     <content>
 *       (document content goes here)
 *     </content>
 *   </upsert>
 *   <upsert>
 *     <!-- upsert element is repeated for each additions -->
 *   </upsert>
 *
 *   <!-- Document deletions: -->
 *   <delete>
 *     <reference>(document reference, e.g., URL)</reference>
 *     <metadata>
 *       <meta name="(meta field name)">(value)</meta>
 *       <meta name="(meta field name)">(value)</meta>
 *       <!-- meta is repeated for each metadata fields -->
 *     </metadata>
 *   </delete>
 *   <delete>
 *     <!-- delete element is repeated for each deletions -->
 *   </delete>
 * </docs>
 * }
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.core.fs.impl.XMLFileCommitter">
 *   {@nx.include com.norconex.committer.core.fs.AbstractFSCommitter#options}
 *   <indent>(number of indentation spaces, default does not indent)</indent>
 * </committer>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class XMLFileCommitter extends
        AbstractFSCommitter<EnhancedXmlStreamWriter, XMLFileCommitterConfig> {

    @Getter
    private final XMLFileCommitterConfig configuration =
            new XMLFileCommitterConfig();

    @Override
    protected String getFileExtension() {
        return "xml";
    }

    @Override
    protected EnhancedXmlStreamWriter createDocWriter(Writer writer)
            throws IOException {
        var xml = new EnhancedXmlStreamWriter(
                writer, false, configuration.getIndent()
        );
        xml.writeStartDocument();
        xml.writeStartElement("docs");
        return xml;
    }

    @Override
    protected void writeUpsert(
            EnhancedXmlStreamWriter xml,
            UpsertRequest upsertRequest
    ) throws IOException {

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
                        upsertRequest.getContent(), StandardCharsets.UTF_8
                ).trim()
        );

        xml.writeEndElement(); // </upsert>
        xml.flush();
    }

    @Override
    protected void writeDelete(
            EnhancedXmlStreamWriter xml,
            DeleteRequest deleteRequest
    ) throws IOException {

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
