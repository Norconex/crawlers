/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.AbstractDocumentTransformer;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Get rid of the content stream and optionally store it as text into a
 * metadata field instead.
 * </p>
 * <h3>Storing content in an existing field</h3>
 * <p>
 * If a <code>toField</code> with the same name already exists for a document,
 * the value will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 * <p>
 * This class can be used both as a pre-parsing or post-parsing handler. To
 * store the content in a field, make sure pre-parsing is of a text
 * content-types.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.NoContentTransformer"
 *     toField="(Optionally store content into a field.)"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="NoContentTransformer"/>
 * }
 * <p>
 * The above example removes the content of all documents (leaving you with
 * metadata only).
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class NoContentTransformer extends AbstractDocumentTransformer
        implements XMLConfigurable {

    private String toField;
    private PropertySetter onSet;

    public String getToField() {
        return toField;
    }
    public void setToField(String toField) {
        this.toField = toField;
    }
    public PropertySetter getOnSet() {
        return onSet;
    }
    public void setOnSet(PropertySetter onSet) {
        this.onSet = onSet;
    }

    @Override
    protected void transformApplicableDocument(HandlerDoc doc,
            InputStream input, OutputStream output, ParseState parseState)
            throws ImporterHandlerException {
        try {
            if (StringUtils.isNotBlank(toField)) {
                doc.getMetadata().add(toField,
                        IOUtils.toString(input, StandardCharsets.UTF_8));
            }
            output.write(new byte[] {});
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Could not remove content for: " + doc.getReference(), e);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setToField(xml.getString("@toField", toField));
        setOnSet(PropertySetter.fromXML(xml, onSet));
    }
    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("toField", toField);
        PropertySetter.toXML(xml, onSet);
    }
}
