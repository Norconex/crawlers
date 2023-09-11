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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.norconex.committer.core.batch.BaseBatchCommitterConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IDOL Committer configuration.
 * @author Pascal Essiembre
 */
@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class IdolCommitterConfig extends BaseBatchCommitterConfig
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_URL = "http://localhost:9001";

    /**
     * Gets the <code>DREADDDATA</code> optional URL parameters.
     * @return URL parameters (key=parameter name; value=parameter value)
     */
    private final Map<String, String> dreAddDataParams = new HashMap<>();

    /**
     * Gets the <code>DREDELETEREF</code> optional URL parameters.
     * @return URL parameters (key=parameter name; value=parameter value)
     */
    private final Map<String, String> dreDeleteRefParams = new HashMap<>();

    /**
     * The IDOL database name.
     * @param databaseName IDOL database name
     * @return IDOL database name
     */
    private String databaseName;

    /**
     * The IDOL index URL (default is <code>http://localhost:9001</code>).
     * @param url the IDOL URL
     * @return IDOL URL
     */
    private String url = DEFAULT_URL;

    /**
     * Whether the IDOL index URL points to a Connector Framework Server
     * (CFS).
     * @param cfs <code>true</code> if committing to a CFS server
     * @return <code>true</code> if committing to a CFS server
     */
    private boolean cfs;

    /**
     * The document field name containing the value to be stored
     * in IDOL <code>DREREFERENCE</code> field. Set to <code>null</code>
     * in order to use the document reference instead of a field (default).
     * @param sourceReferenceField name of field containing reference value,
     *        or <code>null</code>
     * @return name of field containing id value
     */
    private String sourceReferenceField;

    /**
     * The document field name containing the value to be stored
     * in IDOL <code>DRECONTENT</code> field. Set to <code>null</code> in
     * order to use the document content stream instead of a field (default).
     * @param sourceContentField name of field containing content value,
     *        or <code>null</code>
     * @return name of field containing content value
     */
    private String sourceContentField;
}