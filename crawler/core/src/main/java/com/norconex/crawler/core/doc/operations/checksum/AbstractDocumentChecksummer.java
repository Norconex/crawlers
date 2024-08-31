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
package com.norconex.crawler.core.doc.operations.checksum;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.importer.doc.Doc;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Abstract implementation of {@link DocumentChecksummer} giving the option
 * to keep the generated checksum in a metadata field.
 * The checksum can be stored
 * in a target field name specified.  If no target field name is specified,
 * it stores it under the
 * metadata field name {@link CrawlDocMetadata#CHECKSUM_DOC}.
 * </p><p>
 * <b>Implementors do not need to store the checksum themselves, this abstract
 * class does it.</b>
 * </p><p>
 * Implementors should offer this XML configuration usage:
 * </p>
 * {@nx.xml #usage
 * <documentChecksummer
 *    class="(subclass)"
 *    keep="[false|true]"
 *    toField="(optional metadata field to store the checksum)"
 *    onSet="[append|prepend|replace|optional]" />
 * }
 * <p>
 * <code>toField</code> is ignored unless the <code>keep</code>
 * attribute is set to <code>true</code>.
 * </p>
 * @param <T> configurable type
 */
@Data
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractDocumentChecksummer<
        T extends BaseChecksummerConfig>
        implements DocumentChecksummer, Configurable<T> {

    @Override
    public final String createDocumentChecksum(Doc document) {
        var checksum = doCreateDocumentChecksum(document);
        if (getConfiguration().isKeep()) {
            var field = getConfiguration().getToField();
            if (StringUtils.isBlank(field)) {
                field = CrawlDocMetadata.CHECKSUM_DOC;
            }
            PropertySetter.orAppend(getConfiguration().getOnSet()).apply(
                    document.getMetadata(), field, checksum);
            LOG.debug("Document checksum stored in {}", field);
        }
        return checksum;
    }

    protected abstract String doCreateDocumentChecksum(Doc document);
}
