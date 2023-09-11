/* Copyright 2021-2023 Norconex Inc.
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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.norconex.committer.core.batch.BaseBatchCommitterConfig;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.security.Credentials;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Neo4j Committer configuration.
 * </p>
 * @author Sylvain Roussy
 * @author Pascal Essiembre
 */
@Data
@Accessors(chain = true)
public class Neo4jCommitterConfig
        extends BaseBatchCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_NEO4J_ID_PROPERTY = "id";
    public static final String DEFAULT_NEO4J_CONTENT_PROPERTY = "content";

    private String uri;
    private String database;
    private final Credentials credentials = new Credentials();
    private String multiValuesJoiner;
    private String nodeIdProperty = DEFAULT_NEO4J_ID_PROPERTY;
    private String nodeContentProperty = DEFAULT_NEO4J_CONTENT_PROPERTY;
    private String upsertCypher;
    private String deleteCypher;
    private final Set<String> optionalParameters = new HashSet<>();

    public void setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
    }

    public Set<String> getOptionalParameters() {
        return Collections.unmodifiableSet(optionalParameters);
    }
    public void setOptionalParameters(Set<String> optionalParameters) {
        CollectionUtil.setAll(this.optionalParameters, optionalParameters);
    }
    public void addOptionalParameter(String optionalParameter) {
        optionalParameters.add(optionalParameter);
    }
}