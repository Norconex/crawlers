/* Copyright 2023 Norconex Inc.
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
package com.norconex.committer.sql;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * SQL Committer configuration.
 * </p>
 */
public class SQLCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default SQL content field */
    public static final String DEFAULT_SQL_CONTENT_FIELD = "content";
    /** Default multi-value join string */
    public static final String DEFAULT_MULTI_VALUES_JOINER = "|";

    private String driverPath;
    private String driverClass;
    private String connectionUrl;
    private final Credentials credentials = new Credentials();
    private final Properties properties = new Properties();

    private String tableName;
    private String primaryKey;
    private String createTableSQL;
    private String createFieldSQL;

    private boolean fixFieldNames;
    private boolean fixFieldValues;
    private String multiValuesJoiner = DEFAULT_MULTI_VALUES_JOINER;

    private String targetContentField = DEFAULT_SQL_CONTENT_FIELD;

    public String getDriverPath() {
        return driverPath;
    }
    public void setDriverPath(String driverPath) {
        this.driverPath = driverPath;
    }

    public String getDriverClass() {
        return driverClass;
    }
    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getConnectionUrl() {
        return connectionUrl;
    }
    public void setConnectionUrl(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    public Credentials getCredentials() {
        return credentials;
    }
    public void setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
    }

    public Properties getProperties() {
        return properties;
    }
    public void setProperties(Properties properties) {
        CollectionUtil.setAll(this.properties, properties);
    }

    public String getTableName() {
        return tableName;
    }
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getCreateTableSQL() {
        return createTableSQL;
    }
    public void setCreateTableSQL(String createTableSQL) {
        this.createTableSQL = createTableSQL;
    }

    public String getCreateFieldSQL() {
        return createFieldSQL;
    }
    public void setCreateFieldSQL(String createFieldSQL) {
        this.createFieldSQL = createFieldSQL;
    }

    public String getMultiValuesJoiner() {
        return multiValuesJoiner;
    }
    public void setMultiValuesJoiner(String multiValuesJoiner) {
        this.multiValuesJoiner = multiValuesJoiner;
    }

    public boolean isFixFieldNames() {
        return fixFieldNames;
    }
    public void setFixFieldNames(boolean fixFieldNames) {
        this.fixFieldNames = fixFieldNames;
    }

    public boolean isFixFieldValues() {
        return fixFieldValues;
    }
    public void setFixFieldValues(boolean fixFieldValues) {
        this.fixFieldValues = fixFieldValues;
    }

    public String getTargetContentField() {
        return targetContentField;
    }
    public void setTargetContentField(String targetContentField) {
        this.targetContentField = targetContentField;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }
    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    void saveToXML(XML xml) {
        xml.addElement("driverPath", getDriverPath());
        xml.addElement("driverClass", getDriverClass());
        xml.addElement("connectionUrl", getConnectionUrl());
        credentials.saveToXML(xml.addElement("credentials"));
        xml.addElementMap("properties", "property", "name", properties);
        xml.addElement("tableName", getTableName());
        xml.addElement("primaryKey", getPrimaryKey());
        xml.addElement("createTableSQL", getCreateTableSQL());
        xml.addElement("createFieldSQL", getCreateFieldSQL());
        xml.addElement("fixFieldNames", isFixFieldNames());
        xml.addElement("fixFieldValues", isFixFieldValues());
        xml.addElement("multiValuesJoiner", getMultiValuesJoiner());
        xml.addElement("targetContentField", getTargetContentField());
    }

    void loadFromXML(XML xml) {
        setDriverPath(xml.getString("driverPath", getDriverPath()));
        setDriverClass(xml.getString("driverClass", getDriverClass()));
        setConnectionUrl(xml.getString("connectionUrl", getConnectionUrl()));
        xml.ifXML("credentials", x -> x.populate(credentials));
        xml.ifXML("properties", xmlProps -> {
            properties.clear();
            xmlProps.getXMLList("property").forEach(xp -> {
                properties.add(xp.getString("@name"), xp.getString("."));
            });
        });
        setTableName(xml.getString("tableName", getTableName()));
        setPrimaryKey(xml.getString("primaryKey", getPrimaryKey()));
        setCreateTableSQL(xml.getString("createTableSQL", getCreateTableSQL()));
        setCreateFieldSQL(xml.getString("createFieldSQL", getCreateFieldSQL()));
        setFixFieldNames(xml.getBoolean("fixFieldNames", isFixFieldNames()));
        setFixFieldValues(xml.getBoolean("fixFieldValues", isFixFieldValues()));
        setMultiValuesJoiner(xml.getString(
                "multiValuesJoiner", getMultiValuesJoiner()));
        setTargetContentField(
                xml.getString("targetContentField", getTargetContentField()));
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
