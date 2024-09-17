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

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.commons.lang.url.HttpURL;

/* CFS "ingest" "removes" action:
 *
 *   http://server:port/action=ingest&removes=[CSVURLencodedRefs]
 *
 * The [CSVURLencodedRefs] is made of comma-separated URL-encoded
 * document references.
 *
 * Reference material:
 *
 * https://www.microfocus.com/documentation/idol/IDOL_12_7/
 * CFS_12.7_Documentation/Help/#Actions/CFS/Ingest.htm%3FTocPath%3D
 * Reference%7CActions%7CConnector%2520Framework%2520Server%7C_____2
 */
class CfsIngestRemovesAction implements IdolIndexAction {

    private final IdolCommitterConfig config;

    CfsIngestRemovesAction(IdolCommitterConfig config) {
        this.config = config;
    }

    @Override
    public URL url(List<CommitterRequest> batch, HttpURL url)
            throws CommitterException {
        url.getQueryString().set("action", "ingest");
        url.getQueryString().set("DREDbName", config.getDatabaseName());
        return IdolUtil
                .deleteUrlBuilder()
                .batch(batch)
                .baseUrl(url)
                .refField(config.getSourceReferenceField())
                .refsParamName("removes")
                .refsDelimiter(",")
                .build();
    }

    @Override
    public void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException {
        //NOOP
    }
}
