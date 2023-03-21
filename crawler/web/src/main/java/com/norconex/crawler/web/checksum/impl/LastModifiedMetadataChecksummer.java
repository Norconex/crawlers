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
package com.norconex.crawler.web.checksum.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.checksum.AbstractMetadataChecksummer;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.CrawlDocMetadata;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Default implementation of {@link MetadataChecksummer} for the
 * Norconex Web Crawler which simply
 * returns the exact value of the "Last-Modified" HTTP header field, or
 * <code>null</code> if not present.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field.
 * When {@link #setKeep(boolean)} is <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name
 * {@link CrawlDocMetadata#CHECKSUM_METADATA}.
 * </p>
 * <p>
 * To use different fields (one or several) to constitute a checksum,
 * you can instead use the {@link GenericMetadataChecksummer}.
 * </p>
 * {@nx.xml.usage
 * <metadataChecksummer
 *     class="com.norconex.crawler.web.checksum.impl.LastModifiedMetadataChecksummer"
 *     keep="[false|true]"
 *     toField="(field to store checksum)"
 *     onSet="[append|prepend|replace|optional]" />
 * }
 *
 * {@nx.xml.example
 * <metadataChecksummer keep="true" toField="metaChecksum" />
 * }
 * <p>
 * The above example will store the last modified date used for checksum
 * purposes in a field called "metaChecksum".
 * </p>
 *
 * <p>
 * <b>Since 2.0.0</b>, a self-closing
 * <code>&lt;metadataChecksummer/&gt;</code> tag without any attributes
 * is used to disable checksum generation.
 * </p>
 *
 * @since 2.2.0
 * @see GenericMetadataChecksummer
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class LastModifiedMetadataChecksummer
        extends AbstractMetadataChecksummer {

    /** HTTP header name used to perform checksum. */
    public static final String LAST_MODIFIED_FIELD = HttpHeaders.LAST_MODIFIED;

    @Override
    protected String doCreateMetaChecksum(Properties metadata) {
        var checksum = metadata.getString(LAST_MODIFIED_FIELD);
        LOG.debug("HTTP Header \"Last-Modified\" value: {}", checksum);
        if (StringUtils.isNotBlank(checksum)) {
            return checksum;
        }
        return null;
    }

    @Override
    protected void loadChecksummerFromXML(XML xml) {
        //NOOP
    }

    @Override
    protected void saveChecksummerToXML(XML xml) {
        //NOOP
    }
}
