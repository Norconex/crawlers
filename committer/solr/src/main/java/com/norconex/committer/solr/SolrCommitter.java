/* Copyright 2019-2024 Norconex Inc.
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

package com.norconex.committer.solr;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.time.DurationParser;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Commits documents to Apache Solr.
 * </p>
 *
 * <h3>Solr Client:</h3>
 * <p>
 * You can specify which type of
 * <a href="https://lucene.apache.org/solr/guide/8_1/using-solrj.html#types-of-solrclients">
 * Solr Client</a> to use.
 * The expected configuration value of "solrURL" is influenced
 * by the client type chosen.  Default client type is
 * <code>Http2SolrClient</code>. The clients are:
 * </p>
 * <dl>
 *   <dt>Http2SolrClient</dt>
 *     <dd>Default client. For direct access to a single Solr node. Ideal for
 *         local development or small setups. Expects a Solr URL.</dd>
 *   <dt>CloudSolrClient</dt>
 *     <dd>For use with a SolrCloud cluster. Expects a comma-separated list
 *         of Zookeeper hosts.</dd>
 *   <dt>LBHttp2SolrClient</dt>
 *     <dd>Simple load-balancing as an alternative to an external load balancer.
 *         Expects two or more Solr node URLs (comma-separated).</dd>
 *   <dt>ConcurrentUpdateHttp2SolrClient</dt>
 *     <dd>Optimized for mass upload on a single node.  Not best for queries.
 *         Expects a Solr URL.</dd>
 * </dl>
 *
 * <p>
 * The above client types are using the HTTP/2 protocol. They should be
 * favored over the following, which are using HTTP/1.x and are
 * considered <b>deprecated</b>:
 * </p>
 *
 * <dl>
 *   <dt>HttpSolrClient</dt>
 *     <dd>For direct access to a single Solr node. Ideal for
 *         local development. Expects a Solr URL.</dd>
 *   <dt>LBHttpSolrClient</dt>
 *     <dd>Simple load-balancing as an alternative to an external load balancer.
 *         Expects two or more Solr node URLs (comma-separated).</dd>
 *   <dt>ConcurrentUpdateSolrClient</dt>
 *     <dd>Optimized for mass upload on a single node.  Not best for queries.
 *         Expects a Solr URL.</dd>
 * </dl>
 *
 * <h3>Authentication</h3>
 * <p>
 * Basic authentication is supported for password-protected
 * Solr installations.
 * </p>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.solr.SolrCommitter">
 *   <solrClientType>
 *     (See class documentation for options. Default: HttpSolrClient.)
 *   </solrClientType>
 *   <solrURL>(URL to Solr)</solrURL>
 *   <solrUpdateURLParams>
 *     <param name="(parameter name)">(parameter value)</param>
 *     <!-- multiple param tags allowed -->
 *   </solrUpdateURLParams>
 *   <solrCommitDisabled>[false|true]</solrCommitDisabled>
 *
 *   <!-- Use the following if authentication is required. -->
 *   <credentials>
 *     {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   </credentials>
 *
 *   <sourceIdField>
 *     (Optional document field name containing the value that will be stored
 *     in Solr target ID field. Default is the document reference.)
 *   </sourceIdField>
 *   <targetIdField>
 *     (Optional name of Solr field where to store a document unique
 *     identifier (sourceIdField).  If not specified, default is "id".)
 *   </targetIdField>
 *   <targetContentField>
 *     (Optional Solr field name to store document content/body.
 *     Default is "content".)
 *   </targetContentField>
 *
 *   {@nx.include com.norconex.committer.core.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@Slf4j
public class SolrCommitter
        extends AbstractBatchCommitter<SolrCommitterConfig> {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private SolrClient solrClient;

    @Getter
    private final SolrCommitterConfig configuration =
            new SolrCommitterConfig();

    @Override
    protected void initBatchCommitter() throws CommitterException {
        solrClient = ObjectUtils.defaultIfNull(
                configuration.getSolrClientType(),
                SolrClientType.HTTP2
        ).create(configuration.getSolrURL());
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        // Add to request all operations in batch, and force a commit
        // whenever we do a "delete" after an "add" to eliminate the
        // risk of the delete being a no-op since added documents are
        // not visible until committed (thus nothing to delete).

        //MAYBE: before a delete, check if the same reference was previously
        //added before forcing a commit if any additions occurred.

        var docCount = 0;
        try {
            final var solrBatchRequest = new UpdateRequest();
            var previousWasAddition = false;
            while (it.hasNext()) {
                var r = it.next();
                if (r instanceof UpsertRequest upsert) {
                    addSolrUpsertRequest(solrBatchRequest, upsert);
                    previousWasAddition = true;
                } else if (r instanceof DeleteRequest delete) {
                    if (previousWasAddition) {
                        pushSolrRequest(solrBatchRequest);
                    }
                    addSolrDeleteRequest(solrBatchRequest, delete);
                    previousWasAddition = false;
                } else {
                    throw new CommitterException("Unsupported operation:" + r);
                }
                docCount++;
            }

            pushSolrRequest(solrBatchRequest);
            LOG.info("Sent {} committer operations to Solr.", docCount);

        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot push document batch to Solr.", e
            );
        }
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        IOUtil.closeQuietly(solrClient);
        solrClient = null;
        LOG.info("SolrClient closed.");
    }

    protected void pushSolrRequest(UpdateRequest solrBatchRequest)
            throws SolrServerException, IOException, CommitterException {

        if (configuration.getCredentials().isSet()) {
            solrBatchRequest.setBasicAuthCredentials(
                    configuration.getCredentials().getUsername(),
                    EncryptionUtil.decryptPassword(
                            configuration.getCredentials()
                    )
            );
        }
        for (Entry<String, String> entry : configuration.getUpdateUrlParams()
                .entrySet()) {
            solrBatchRequest.setParam(entry.getKey(), entry.getValue());
        }

        handleResponse(solrBatchRequest.process(solrClient));
        if (!configuration.isSolrCommitDisabled()) {
            handleResponse(solrBatchRequest.commit(solrClient, null));
        }
        solrBatchRequest.clear();
    }

    protected void addSolrUpsertRequest(
            UpdateRequest solrBatchRequest, UpsertRequest committerRequest
    )
            throws CommitterException {

        CommitterUtil.applyTargetId(
                committerRequest,
                configuration.getSourceIdField(),
                configuration.getTargetIdField()
        );
        CommitterUtil.applyTargetContent(
                committerRequest, configuration.getTargetContentField()
        );
        solrBatchRequest.add(buildSolrDocument(committerRequest.getMetadata()));
    }

    protected void addSolrDeleteRequest(
            UpdateRequest solrBatchRequest, DeleteRequest committerRequest
    ) {
        CommitterUtil.applyTargetId(
                committerRequest,
                configuration.getSourceIdField(),
                configuration.getTargetIdField()
        );
        solrBatchRequest.deleteById(committerRequest.getReference());
    }

    protected SolrInputDocument buildSolrDocument(Properties fields) {
        var doc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            var values = fields.getStrings(key);
            for (String value : values) {
                doc.addField(key, value);
            }
        }
        return doc;
    }

    private void handleResponse(UpdateResponse response)
            throws CommitterException {
        handleResponse(response.getResponse());
    }

    private void handleResponse(NamedList<Object> response)
            throws CommitterException {
        @SuppressWarnings("unchecked")
        var headers =
                (NamedList<Object>) response.get("responseHeader");
        if (headers == null) {
            throw new CommitterException(
                    "No response headers obtained from Solr request. "
                            + "Response: " + response
            );
        }
        var status = Objects.toString(headers.get("status"), null);
        if (!"0".equals(status)) {
            throw new CommitterException(
                    "Invalid Solr response status: " + status
            );
        }
    }

}
