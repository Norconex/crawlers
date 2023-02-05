/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.checksum;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.doc.CrawlDocMetadata;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Abstract implementation of {@link MetadataChecksummer} giving the option
 * to keep the generated checksum.  The checksum can be stored
 * in a target field name specified.  If no target field name is specified,
 * it stores it under the
 * metadata field name {@link CrawlDocMetadata#CHECKSUM_METADATA}.
 * </p><p>
 * <b>Implementors do not need to store the checksum themselves, this abstract
 * class does it.</b>
 * </p><p>
 * Implementors should offer this XML configuration usage:
 * </p>
 *
 * {@nx.xml #usage
 * <metadataChecksummer class="(subclass)">
 *    keep="[false|true]"
 *    toField="(optional metadata field to store the checksum)"
 *    onSet="[append|prepend|replace|optional]" />
 * }
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 */
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class AbstractMetadataChecksummer
        implements MetadataChecksummer, XMLConfigurable {

	private boolean keep;
    private String toField = CrawlDocMetadata.CHECKSUM_METADATA;
    private PropertySetter onSet;

    @Override
    public final String createMetadataChecksum(Properties metadata) {
        var checksum = doCreateMetaChecksum(metadata);
        if (isKeep()) {
            var field = getToField();
            if (StringUtils.isBlank(field)) {
                field = CrawlDocMetadata.CHECKSUM_METADATA;
            }
            PropertySetter.orAppend(onSet).apply(metadata, field, checksum);
            LOG.debug("Meta checksum stored in {}", field);
        }
        return checksum;
    }

    protected abstract String doCreateMetaChecksum(Properties metadata);

	/**
	 * Whether to keep the metadata checksum value as a new metadata field.
	 * @return <code>true</code> to keep the checksum
	 */
	public boolean isKeep() {
        return keep;
    }
    /**
     * Sets whether to keep the metadata checksum value as a new metadata field.
     * @param keep <code>true</code> to keep the checksum
     */
    public void setKeep(boolean keep) {
        this.keep = keep;
    }

    /**
     * Gets the metadata field to use to store the checksum value.
     * Defaults to {@link CrawlDocMetadata#CHECKSUM_METADATA}.
     * Only applicable if {@link #isKeep()} returns {@code true}
     * @return metadata field name
     */
    public String getToField() {
        return toField;
    }
    /**
     * Sets the metadata field name to use to store the checksum value.
     * @param toField the metadata field name
     */
    public void setToField(String toField) {
        this.toField = toField;
    }

    /**
     * Gets the property setter to use when a value is set.
     * @return property setter
     */
    public PropertySetter getOnSet() {
        return onSet;
    }
    /**
     * Sets the property setter to use when a value is set.
     * @param onSet property setter
     */
    public void setOnSet(PropertySetter onSet) {
        this.onSet = onSet;
    }

    @Override
    public final void loadFromXML(XML xml) {
        setKeep(xml.getBoolean("@keep", keep));
        setToField(xml.getString("@toField", toField));
        setOnSet(PropertySetter.fromXML(xml, onSet));
        loadChecksummerFromXML(xml);
    }
    protected abstract void loadChecksummerFromXML(XML xml);

    @Override
    public final void saveToXML(XML xml) {
        xml.setAttribute("keep", isKeep());
        xml.setAttribute("toField", getToField());
        PropertySetter.toXML(xml, getOnSet());
        saveChecksummerToXML(xml);
    }
    protected abstract void saveChecksummerToXML(XML xml);
}
