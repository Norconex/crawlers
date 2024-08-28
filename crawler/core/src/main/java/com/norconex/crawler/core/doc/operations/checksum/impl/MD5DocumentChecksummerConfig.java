/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.checksum.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.operations.checksum.BaseChecksummerConfig;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>Implementation of {@link DocumentChecksummer} which
 * returns a MD5 checksum value of the extracted document content unless
 * one or more given source fields are specified, in which case the MD5
 * checksum value is constructed from those fields.  This checksum is normally
 * performed right after the document has been imported.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field.
 * When {@link #setKeep(boolean)} is <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name
 * {@link CrawlDocMetadata#CHECKSUM_METADATA}.
 * </p>
 *
 * <p>
 * <b>Since 1.9.0</b>, it is possible to use a combination of document content
 * and fields to create the checksum by setting
 * <code>combineFieldsAndContent</code> to <code>true</code>.
 * If you combine fields and content but you don't define a field matcher,
 * it will be the equivalent of adding all fields.
 * If you do not combine the two, specifying a field matcher
 * will ignore the content while specifying none will only use the content.
 * </p>
 *
 * {@nx.xml.usage
 * <documentChecksummer
 *     class="com.norconex.crawler.core.checksum.impl.MD5DocumentChecksummer"
 *     combineFieldsAndContent="[false|true]"
 *     keep="[false|true]"
 *     toField="(optional metadata field to store the checksum)">
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression matching fields used to create the checksum)
 *   </fieldMatcher>
 * </documentChecksummer>
 * }
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 *
 * {@nx.xml.example
 * <documentChecksummer class="MD5DocumentChecksummer" />
 * }
 *
 * <p>
 * The above example uses the document body (default) to make the checksum.
 * </p>
 *
 * <p>
 * <b>Since 2.0.0</b>, a self-closing
 * <code>&lt;documentChecksummer/&gt;</code> tag without any attributes
 * is used to disable checksum generation.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class MD5DocumentChecksummerConfig extends BaseChecksummerConfig {

    /**
     * The field matcher.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    public MD5DocumentChecksummerConfig setFieldMatcher(
            TextMatcher fieldMatcher
    ) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * Whether we are combining the fields and content checksums.
     * @param combineFieldsAndContent <code>true</code> if combining fields
     *        and content checksums
     * @return <code>true</code> if combining fields and content checksums
     */
    private boolean combineFieldsAndContent;

}
