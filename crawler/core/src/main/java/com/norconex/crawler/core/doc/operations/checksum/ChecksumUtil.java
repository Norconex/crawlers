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
package com.norconex.crawler.core.doc.operations.checksum;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * Checksum utility methods.
 */
public final class ChecksumUtil {

    //MAYBE move to Importer and have checksum handlers?

    private static final Logger LOG =
            LoggerFactory.getLogger(ChecksumUtil.class);

    private ChecksumUtil() {
    }

    public static String checksumMD5(InputStream is) throws IOException {
        try (var stream = is) {
            var checksum = DigestUtils.md5Hex(stream); //NOSONAR not sensitive
            LOG.debug("MD5 checksum from input stream: {}", checksum);
            return checksum;
        }
    }

    public static String checksumMD5(String text) {
        if (text == null) {
            return null;
        }
        var checksum = DigestUtils.md5Hex(text); //NOSONAR not sensitive
        LOG.debug("MD5 checksum from string: {}", checksum);
        return checksum;
    }

    public static String metadataChecksumMD5(
            Properties metadata, TextMatcher fieldMatcher) {
        var checksum =
                checksumMD5(metadataChecksumPlain(metadata, fieldMatcher));
        LOG.debug(
                "Metadata checksum (MD5) from {} : \"{}\".",
                fieldMatcher, checksum);
        return checksum;
    }

    public static String metadataChecksumPlain(
            Properties metadata, TextMatcher fieldMatcher) {
        if (metadata == null || fieldMatcher == null
                || isBlank(fieldMatcher.getPattern())) {
            return null;
        }

        var b = new StringBuilder();
        var props = metadata.matchKeys(fieldMatcher);
        if (!props.isEmpty()) {
            List<String> sortedFields = new ArrayList<>(props.keySet());
            // Sort to make sure field order does not affect checksum.
            Collections.sort(sortedFields);
            for (String field : sortedFields) {
                appendValues(b, field, metadata.getStrings(field));
            }
        }

        var checksum = b.toString();
        if (LOG.isDebugEnabled() && StringUtils.isNotBlank(checksum)) {
            LOG.debug(
                    "Metadata checksum (plain text) from {} : \"{}\".",
                    StringUtils.join(props.keySet(), ','), checksum);
        }
        return StringUtils.trimToNull(checksum);
    }

    private static void appendValues(
            StringBuilder b, String field, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                b.append(field).append('=');
                b.append(value).append(';');
            }
        }
    }
}
