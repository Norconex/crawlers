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

import static org.apache.commons.lang3.StringUtils.contains;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.url.QueryString;

class IdolClient {

    private static final Logger LOG = LoggerFactory.getLogger(IdolClient.class);

    private final IdolCommitterConfig config;
    private final IIdolIndexAction upsertAction;
    private final IIdolIndexAction deleteAction;

    IdolClient(IdolCommitterConfig config) {
        this.config = Objects.requireNonNull(
                config, "'config' must not be null"
        );
        if (StringUtils.isBlank(config.getUrl())) {
            throw new IllegalArgumentException(
                    "Configuration 'url' must be provided."
            );
        }
        if (config.isCfs()) {
            this.upsertAction = new CfsIngestAddsAction(config);
            this.deleteAction = new CfsIngestRemovesAction(config);
        } else {
            this.upsertAction = new DreAddDataAction(config);
            this.deleteAction = new DreDeleteRefAction(config);
        }
    }

    public void post(Iterator<CommitterRequest> iterator)
            throws CommitterException {
        // Because order of additions/deletions can sometimes be important,
        // we post the documents to IDOL the moment we switch from
        // add to/from delete.  That means when there is a mix of additions
        // and deletions, the number of operations sent at once does not
        // always match the desired batch size (would be smaller).

        Class<? extends CommitterRequest> prevType = null;
        int docCount = 0;
        final List<CommitterRequest> batch = new ArrayList<>();

        while (iterator.hasNext()) {
            CommitterRequest r = iterator.next();
            if (typeChanged(prevType, r)) {
                doPost(batch, prevType);
                batch.clear();
            }
            batch.add(r);
            prevType = r.getClass();
            docCount++;
        }
        doPost(batch, prevType);
        LOG.info("Sent {} upserts/deletes to IDOL.", docCount);
    }

    private boolean typeChanged(
            Class<? extends CommitterRequest> prevType,
            CommitterRequest req
    ) {
        return prevType != null && !(prevType.equals(req.getClass()));
    }

    private IIdolIndexAction actionForType(
            Class<? extends CommitterRequest> reqType
    ) {
        return UpsertRequest.class.isAssignableFrom(reqType)
                ? upsertAction
                : deleteAction;
    }

    private void doPost(
            List<CommitterRequest> batch,
            Class<? extends CommitterRequest> reqType
    )
            throws CommitterException {
        if (batch.isEmpty() || reqType == null) {
            return;
        }
        IIdolIndexAction indexAction = actionForType(reqType);
        HttpURL url = new HttpURL(config.getUrl());
        QueryString qs = url.getQueryString();
        if (UpsertRequest.class.isAssignableFrom(reqType)) {
            config.getDreAddDataParams().forEach(qs::add);
        } else {
            config.getDreDeleteRefParams().forEach(qs::add);
        }

        HttpURLConnection con = openConnection(indexAction.url(batch, url));
        try (Writer w = new BufferedWriter(
                new OutputStreamWriter(
                        con.getOutputStream(), StandardCharsets.UTF_8
                )
        )) {
            indexAction.writeTo(batch, w);
            w.flush();

            // Get the response
            int responseCode = con.getResponseCode();
            LOG.debug(
                    "Sending {} {} to URL: {}",
                    batch.size(), reqType.getSimpleName(), config.getUrl()
            );
            LOG.debug("Server Response Code: {}", responseCode);
            String response = IOUtils.toString(
                    con.getInputStream(), StandardCharsets.UTF_8
            );
            LOG.debug("Server Response Text: {}", response);
            if ((config.isCfs() && !contains(response, "SUCCESS"))
                    || (!config.isCfs() && !contains(response, "INDEXID"))) {
                throw new CommitterException(
                        "Unexpected HTTP response: " + response
                );
            }
        } catch (IOException e) {
            throw new CommitterException(
                    "Cannot post content to " + config.getUrl(), e
            );
        } finally {
            con.disconnect();
        }
    }

    private HttpURLConnection openConnection(URL url)
            throws CommitterException {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
            con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            con.setRequestMethod("POST");
            return con;
        } catch (IOException e) {
            throw new CommitterException(
                    "Cannot open HTTP connection to IDOL at: "
                            + config.getUrl(),
                    e
            );
        }
    }
}
