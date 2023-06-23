/* Copyright 2021 Norconex Inc.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Neo4j Committer configuration.
 * </p>
 * @author Sylvain Roussy
 * @author Pascal Essiembre
 */
public class Neo4jCommitterConfig implements Serializable {

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

    public String getDatabase() {
        return database;
    }
    public void setDatabase(String database) {
        this.database = database;
    }

    public Credentials getCredentials() {
        return credentials;
    }
    public void setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
    }

    public String getUri() {
        return uri;
    }
    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMultiValuesJoiner() {
        return multiValuesJoiner;
    }
    public void setMultiValuesJoiner(String multiValuesJoiner) {
        this.multiValuesJoiner = multiValuesJoiner;
    }

    public String getNodeIdProperty() {
        return nodeIdProperty;
    }
    public void setNodeIdProperty(String nodeIdProperty) {
        this.nodeIdProperty = nodeIdProperty;
    }

    public String getNodeContentProperty() {
        return nodeContentProperty;
    }
    public void setNodeContentProperty(String nodeContentProperty) {
        this.nodeContentProperty = nodeContentProperty;
    }

    public String getUpsertCypher() {
        return upsertCypher;
    }
    public void setUpsertCypher(String upsertCypher) {
        this.upsertCypher = upsertCypher;
    }

    public String getDeleteCypher() {
        return deleteCypher;
    }
    public void setDeleteCypher(String deleteCypher) {
        this.deleteCypher = deleteCypher;
    }

    public Set<String> getOptionalParameters() {
        return Collections.unmodifiableSet(optionalParameters);
    }
    public void setOptionalParameters(Set<String> optionalParameters) {
        CollectionUtil.setAll(this.optionalParameters, optionalParameters);
    }
    public void addOptionalParameter(String optionalParameter) {
        this.optionalParameters.add(optionalParameter);
    }

    void saveToXML(XML xml) {
        xml.addElement("uri", getUri());
        xml.addElement("database", getDatabase());
        credentials.saveToXML(xml.addElement("credentials"));
        xml.addElement("multiValuesJoiner", getMultiValuesJoiner());
        xml.addElement("nodeIdProperty", getNodeIdProperty());
        xml.addElement("nodeContentProperty", getNodeContentProperty());
        xml.addElement("upsertCypher", getUpsertCypher());
        xml.addElement("deleteCypher", getDeleteCypher());
        xml.addDelimitedElementList(
                "optionalParameters", new ArrayList<>(optionalParameters));
    }
    void loadFromXML(XML xml) {
        setUri(xml.getString("uri", getUri()));
        setDatabase(xml.getString("database", getDatabase()));
        xml.ifXML("credentials", x -> x.populate(credentials));
        setMultiValuesJoiner(xml.getString(
                "multiValuesJoiner", getMultiValuesJoiner()));
        setNodeIdProperty(xml.getString("nodeIdProperty", getNodeIdProperty()));
        setNodeContentProperty(
                xml.getString("nodeContentProperty", getNodeContentProperty()));
        setUpsertCypher(xml.getString("upsertCypher", getUpsertCypher()));
        setDeleteCypher(xml.getString("deleteCypher", getDeleteCypher()));
        List<String> params =
                xml.getDelimitedStringList("optionalParameters",
                        (List<String>) null);
        if (params != null) {
            setOptionalParameters(new HashSet<>(params));
        }
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