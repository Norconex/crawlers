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
package com.norconex.crawler.core.checksum.impl;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.checksum.AbstractMetadataChecksummer;
import com.norconex.crawler.core.checksum.ChecksumUtil;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.CrawlDocMetadata;

import lombok.Data;

/**
 * <p>
 * Generic implementation of {@link MetadataChecksummer} that uses
 * specified field names and their values to create a checksum. The name
 * and values are simply returned as is, joined using this format:
 * <code>fieldName=fieldValue;fieldName=fieldValue;...</code>.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field.
 * When {@link #setKeep(boolean)} is <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name
 * {@link CrawlDocMetadata#CHECKSUM_METADATA}.
 * </p>
 * {@nx.xml.usage
 * <metadataChecksummer
 *     class="com.norconex.crawler.core.checksum.impl.GenericMetadataChecksummer"
 *     keep="[false|true]"
 *     toField="(optional field to store the checksum)">
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression matching fields used to create the checksum)
 *   </fieldMatcher>
 * </metadataChecksummer>
 * }
 *
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 *
 * {@nx.xml.example
 * <metadataChecksummer class="GenericMetadataChecksummer">
 *   <fieldMatcher method="csv">docLastModified,docSize</fieldMatcher>
 * </metadataChecksummer>
 * }
 * <p>
 * The above example uses a combination of two (fictitious) fields called
 * "docLastModified" and "docSize" to make the checksum.
 * </p>
 *
 * <p>
 * A self-closing <code>&lt;metadataChecksummer/&gt;</code> tag without
 * any attributes is used to disable checksum generation.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
public class GenericMetadataChecksummer
        extends AbstractMetadataChecksummer<GenericMetadataChecksummerConfig> {

    private final GenericMetadataChecksummerConfig configuration =
            new GenericMetadataChecksummerConfig();

    @Override
    protected String doCreateMetaChecksum(Properties metadata) {
        return ChecksumUtil.metadataChecksumPlain(
                metadata, getConfiguration().getFieldMatcher());
    }
}
