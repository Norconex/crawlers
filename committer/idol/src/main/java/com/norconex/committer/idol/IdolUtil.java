/* Copyright 2024 Norconex Inc.
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
package com.norconex.committer.idol;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.url.HttpURL;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class IdolUtil {

    private IdolUtil() {
    }

    @Data
    @Accessors(fluent = true)
    @Getter(value = AccessLevel.NONE)
    static class DeleteUrlBuilder {
        private List<CommitterRequest> batch;
        private HttpURL baseUrl;
        private String refField;
        private String refsParamName;
        private String refsDelimiter;

        private DeleteUrlBuilder() {
        }

        URL build() throws CommitterException {
            try {
                var b = new StringBuilder(baseUrl.toString());
                b.append("&").append(refsParamName).append("=");
                var sep = "";
                for (CommitterRequest req : batch) {
                    var ref = req.getReference();
                    if (StringUtils.isNotBlank(refField)) {
                        ref = req.getMetadata().getString(refField);
                        if (StringUtils.isBlank(ref)) {
                            LOG.warn("""
                                Source reference field '{}' has no value \
                                for deletion of document: '{}'. Using that \
                                original document reference instead.""",
                                    refField, req.getReference());
                            ref = req.getReference();
                        }
                    }
                    b.append(sep);
                    b.append(URLEncoder.encode(ref,
                            StandardCharsets.UTF_8.toString()));
                    sep = refsDelimiter;
                }
                return new URL(b.toString());
            } catch (MalformedURLException | UnsupportedEncodingException e) {
                throw new CommitterException(
                        "Could not create IDOL deletion URL.", e);
            }
        }
    }

    static DeleteUrlBuilder deleteUrlBuilder() {
        return new DeleteUrlBuilder();
    }

    static String resolveDreContent(UpsertRequest req, String contentField)
            throws CommitterException {
        if (StringUtils.isNotBlank(contentField)) {
            return StringUtils.trimToEmpty(String.join("\n\n",
                    req.getMetadata().getStrings(contentField)));
        }
        try {
            return IOUtils.toString(req.getContent(), UTF_8);
        } catch (IOException e) {
            throw new CommitterException(
                    "A problem occured reading the document content of "
                            + "an upsert request for %s".formatted(
                                    req.getReference()),
                    e);
        }
    }
}
