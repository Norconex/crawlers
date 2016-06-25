/* Copyright 2015 Norconex Inc.
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
package com.norconex.collector.http.checksum.impl;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.checksum.AbstractMetadataChecksummer;
import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.checksum.impl.GenericMetadataChecksummer;
import com.norconex.collector.core.doc.CollectorMetadata;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>
 * Default implementation of {@link IMetadataChecksummer} for the 
 * Norconex HTTP Collector which simply
 * returns the exact value of the "Last-Modified" HTTP header field, or
 * <code>null</code> if not present.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field. 
 * When {@link #setKeep(boolean)} is <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name 
 * {@link CollectorMetadata#COLLECTOR_CHECKSUM_METADATA}. 
 * </p>
 * <p>
 * To use different fields (one or several) to constitute a checksum,
 * you can instead use the {@link GenericMetadataChecksummer}.
 * </p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;metadataChecksummer 
 *      class="com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer"
 *      disabled="[false|true]"
 *      keep="[false|true]"
 *      targetField="(field to store checksum)" /&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 2.2.0
 * @see GenericMetadataChecksummer
 */
public class LastModifiedMetadataChecksummer 
        extends AbstractMetadataChecksummer {

    private static final Logger LOG = LogManager.getLogger(
            LastModifiedMetadataChecksummer.class);

    /** HTTP header name used to perform checksum. */
    private static final String LAST_MODIFIED_FIELD = "Last-Modified";
    private boolean disabled;
    
    @Override
    protected String doCreateMetaChecksum(Properties metadata) {
        if (disabled) {
            return null;
        }
        String checksum = metadata.getString(LAST_MODIFIED_FIELD);
        LOG.debug("HTTP Header \"Last-Modified\" value: " + checksum);
        if (StringUtils.isNotBlank(checksum)) {
            return checksum;
        }
        return null;
    }

    /**
     * Whether this checksummer is disabled or not. When disabled, not
     * checksum will be created (the checksum will be <code>null</code>).
     * @return <code>true</code> if disabled
     */
    public boolean isDisabled() {
        return disabled;
    }
    /**
     * Sets whether this checksummer is disabled or not. When disabled, not
     * checksum will be created (the checksum will be <code>null</code>).
     * @param disabled <code>true</code> if disabled
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }   
    
    @Override
    protected void loadChecksummerFromXML(XMLConfiguration xml) {
        setDisabled(xml.getBoolean("[@disabled]", disabled));
    }

    @Override
    protected void saveChecksummerToXML(EnhancedXMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeAttributeBoolean("disabled", isDisabled());
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof LastModifiedMetadataChecksummer)) {
            return false;
        }
        LastModifiedMetadataChecksummer castOther = 
                (LastModifiedMetadataChecksummer) other;
        return new EqualsBuilder()
                .appendSuper(super.equals(castOther))
                .append(disabled, castOther.disabled)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(disabled)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("disabled", disabled)
                .toString();
    }    
}
