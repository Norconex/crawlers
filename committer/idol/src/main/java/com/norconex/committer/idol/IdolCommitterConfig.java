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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * IDOL Committer configuration.
 * @author Pascal Essiembre
 */
public class IdolCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_URL = "http://localhost:9001";

    private final Map<String, String> dreAddDataParams = new HashMap<>();
    private final Map<String, String> dreDeleteRefParams = new HashMap<>();
    private String databaseName;
    private String url = DEFAULT_URL;
    private boolean cfs;
    private String sourceReferenceField;
    private String sourceContentField;

    public IdolCommitterConfig() {
        super();
    }
    /**
     * Gets the IDOL index URL (default is <code>http://localhost:9001</code>).
     * @return IDOL URL
     */
    public String getUrl() {
        return url;
    }
    /**
     * Sets the IDOL index URL (default is <code>http://localhost:9001</code>).
     * @param url the IDOL URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets whether the IDOL index URL points to a Connector Framework Server
     * (CFS).
     * @return <code>true</code> if committing to a CFS server
     */
    public boolean isCfs() {
        return cfs;
    }
    /**
     * Sets whether the IDOL index URL points to a Connector Framework Server
     * (CFS).
     * @param cfs <code>true</code> if committing to a CFS server
     */
    public void setCfs(boolean cfs) {
        this.cfs = cfs;
    }

    /**
     * Gets IDOL database name.
     * @return IDOL database name
     */
    public String getDatabaseName() {
        return databaseName;
    }
    /**
     * Sets IDOL database name.
     * @param databaseName IDOL database name
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Gets the <code>DREADDDATA</code> optional URL parameters.
     * @return URL parameters (key=parameter name; value=parameter value)
     */
    public Map<String, String> getDreAddDataParams() {
        return dreAddDataParams;
    }
    /**
     * Gets the <code>DREDELETEREF</code> optional URL parameters.
     * @return URL parameters (key=parameter name; value=parameter value)
     */
    public Map<String, String> getDreDeleteRefParams() {
        return dreDeleteRefParams;
    }

    /**
     * Gets the document field name containing the value to be stored
     * in IDOL <code>DREREFERENCE</code> field. Default is not a field,
     * but rather the document reference.
     * @return name of field containing id value
     */
    public String getSourceReferenceField() {
        return sourceReferenceField;
    }
    /**
     * Sets the document field name containing the value to be stored
     * in IDOL <code>DREREFERENCE</code> field. Set to <code>null</code>
     * in order to use the document reference instead of a field (default).
     * @param sourceReferenceField name of field containing reference value,
     *        or <code>null</code>
     */
    public void setSourceReferenceField(String sourceReferenceField) {
        this.sourceReferenceField = sourceReferenceField;
    }

    /**
     * Gets the document field name containing the value to be stored
     * in IDOL <code>DRECONTENT</code> field. Default is not a field, but
     * rather the document content stream.
     * @return name of field containing content value
     */
    public String getSourceContentField() {
        return sourceContentField;
    }
    /**
     * Sets the document field name containing the value to be stored
     * in IDOL <code>DRECONTENT</code> field. Set to <code>null</code> in
     * order to use the document content stream instead of a field (default).
     * @param sourceContentField name of field containing content value,
     *        or <code>null</code>
     */
    public void setSourceContentField(String sourceContentField) {
        this.sourceContentField = sourceContentField;
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}