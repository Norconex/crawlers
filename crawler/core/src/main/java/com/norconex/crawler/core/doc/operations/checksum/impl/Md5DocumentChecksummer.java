/* Copyright 2014-2025 Norconex Inc.
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

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.doc.operations.checksum.AbstractDocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.ChecksumUtil;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;
import com.norconex.importer.doc.Doc;

import lombok.Data;

/**
 * <p>Implementation of {@link DocumentChecksummer} which
 * returns a MD5 checksum value of the extracted document content unless
 * one or more given source fields are specified, in which case the MD5
 * checksum value is constructed from those fields.  This checksum is normally
 * performed right after the document has been imported.
 * </p>
 * <p>
 * You have the option to keep the checksum as a document metadata field.
 * When {@link Md5DocumentChecksummerConfig#setKeep(boolean)} is
 * <code>true</code>, the checksum will be
 * stored in the target field name specified. If you do not specify any,
 * it stores it under the metadata field name
 * {@value CrawlDocMetaConstants#CHECKSUM_METADATA}.
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
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 */
@Data
public class Md5DocumentChecksummer
        extends AbstractDocumentChecksummer<Md5DocumentChecksummerConfig> {

    private final Md5DocumentChecksummerConfig configuration =
            new Md5DocumentChecksummerConfig();

    @Override
    public String doCreateDocumentChecksum(Doc document) {

        // fields
        var fm = new TextMatcher(getConfiguration().getFieldMatcher());
        var isSourceFieldsSet = isFieldMatcherSet();
        if (getConfiguration().isCombineFieldsAndContent()
                && !isSourceFieldsSet) {
            fm.setMethod(Method.REGEX);
            fm.setPattern(".*");
        }
        var b = new StringBuilder();
        if (isSourceFieldsSet
                || getConfiguration().isCombineFieldsAndContent()) {
            var checksum = ChecksumUtil.metadataChecksumMD5(
                    document.getMetadata(), fm);
            if (checksum != null) {
                b.append(checksum);
                b.append('|');
            }
        }

        // document
        if (getConfiguration().isCombineFieldsAndContent()
                || !isSourceFieldsSet) {
            try {
                b.append(ChecksumUtil.checksumMD5(document.getInputStream()));
            } catch (IOException e) {
                throw new CrawlerException(
                        "Cannot create document checksum on : "
                                + document.getReference(),
                        e);
            }
        }

        return StringUtils.trimToNull(b.toString());
    }

    private boolean isFieldMatcherSet() {
        return StringUtils.isNotBlank(
                getConfiguration().getFieldMatcher().getPattern());
    }
}
