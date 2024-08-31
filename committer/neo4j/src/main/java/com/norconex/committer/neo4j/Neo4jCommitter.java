/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.committer.neo4j;

import java.util.Iterator;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Commit documents/fields to a Neo4j graph database.
 * </p>
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
 * @author Sylvain Roussy
 * @author Pascal Essiembre
 */
@EqualsAndHashCode
@ToString
public class Neo4jCommitter
        extends AbstractBatchCommitter<Neo4jCommitterConfig> {

    @Getter
    private final Neo4jCommitterConfig configuration =
            new Neo4jCommitterConfig();

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Neo4jClient client;

    @Override
    protected void initBatchCommitter() throws CommitterException {
        client = new Neo4jClient(configuration);
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        client.post(it);
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        if (client != null) {
            client.close();
        }
    }
}