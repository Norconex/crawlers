/* Copyright 2020-2024 Norconex Inc.
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

import java.io.Writer;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

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
class DreDeleteRefAction implements IdolIndexAction {

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
        return IdolUtil
                .deleteUrlBuilder()
                .batch(batch)
                .baseUrl(url)
                .refField(config.getSourceReferenceField())
                .refsParamName("Docs")
                .refsDelimiter("+")
                .build();
    }

    @Override
    public void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException {
        //NOOP
    }
}
