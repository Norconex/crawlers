/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.operations.checksum.impl;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.AbstractMetadataChecksummer;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.ChecksumUtil;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.MetadataChecksummer;

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
 * When {@link GenericMetadataChecksummerConfig#setKeep(boolean)} is
 * <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name
 * {@link CrawlDocMetadata#CHECKSUM_METADATA}.
 * </p>
 *
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 */
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
