/* Copyright 2020-2023 Norconex Inc.
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

import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.commons.lang.url.HttpURL;

/* IDOL "DREDELETEREF" index action.
 *
 *   http://server:port/DREDELETEREF?Docs=[CSVURLencodedRefs]
 *
 * The [CSVURLencodedRefs] is made of plus-separated URL-encoded
 * document references.
 *
 * Reference material:
 *
 * https://www.microfocus.com/documentation/idol/IDOL_12_7/
 * DIH_12.7_Documentation/Help/#Index%20Actions/RemoveContent/
 * _IX_DREDELETEREF.htm%3FTocPath%3DIndex%2520Actions%7CRemove
 * %2520Content%7C_____3
 */
class DreDeleteRefAction implements IIdolIndexAction {

    private static final Logger LOG =
            LoggerFactory.getLogger(DreDeleteRefAction.class);

    private final IdolCommitterConfig config;

    DreDeleteRefAction(IdolCommitterConfig config) {
        this.config = config;
    }

    @Override
    public URL url(List<CommitterRequest> batch, HttpURL url)
            throws CommitterException {
        url.setPath(StringUtils.appendIfMissing(
                url.getPath(), "/") + "DREDELETEREF");
        url.getQueryString().set("DREDbName", config.getDatabaseName());
        try {
            return addDeletesToUrl(batch, url.toString());
        } catch (MalformedURLException | UnsupportedEncodingException e) {
            throw new CommitterException(
                    "Could not create CFS Ingest Removes URL.", e);
        }
    }

    private URL addDeletesToUrl(List<CommitterRequest> batch, String url)
            throws MalformedURLException, UnsupportedEncodingException {
        StringBuilder b = new StringBuilder(url);
        b.append("&Docs=");
        String sep = "";
        for (CommitterRequest req : batch) {
            String refField = config.getSourceReferenceField();
            String ref = req.getReference();
            if (StringUtils.isNotBlank(refField)) {
                ref = req.getMetadata().getString(refField);
                if (StringUtils.isBlank(ref)) {
                    LOG.warn("Source reference field '{}' has no value "
                            + "for deletion of document: '{}'. Using that "
                            + "original document reference instead.",
                            refField, req.getReference());
                    ref = req.getReference();
                }
            }
            b.append(sep);
            b.append(URLEncoder.encode(ref, StandardCharsets.UTF_8.toString()));
            sep = "+";
        }
        return new URL(b.toString());
    }

    @Override
    public void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException {
        //NOOP
    }
}
