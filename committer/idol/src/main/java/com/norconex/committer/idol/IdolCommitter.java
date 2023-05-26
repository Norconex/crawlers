/* Copyright 2010-2023 Norconex Inc.
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

import java.util.Iterator;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Commits documents to IDOL Server/DIH or Connector
 * Framework Server (CFS).   Specifying either the index port or the cfs port
 * determines which of the two will be the documents target.
 * </p>
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.idol.IdolCommitter">
 *   <url>
 *     (IDOL/DIH/CFS index action URL. Default is "http://localhost:9001")
 *   </url>
 *   <cfs>[false|true](whether URL points to a Connector Framework Server)</cfs>
 *   <databaseName>
 *     (Optional IDOL Database Name where to store documents)
 *   </databaseName>
 *   <dreAddDataParams>
 *     <param name="(parameter name)">(parameter value)</param>
 *   </dreAddDataParams>
 *   <dreDeleteRefParams>
 *     <param name="(parameter name)">(parameter value)</param>
 *   </dreDeleteRefParams>
 *   <sourceReferenceField>
 *     (Optional name of the field holding the value to be stored in the
 *     IDOL "DREREFERENCE" field. Default is the document reference.)
 *   </sourceReferenceField>
 *   <sourceContentField>
 *     (Optional name of the field holding the value to be stored in the
 *     IDOL "DRECONTENT" field. Default is the document content stream.)
 *   </sourceContentField>
 *
 *   {@nx.include com.norconex.committer.core3.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.idol.IdolCommitter">
 *   <url>http://some_host:9100</url>
 *   <databaseName>some_database</databaseName>
 * </committer>
 * }
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
public class IdolCommitter extends AbstractBatchCommitter {

    private static final Logger LOG =
            LoggerFactory.getLogger(IdolCommitter.class);

    private final IdolCommitterConfig config = new IdolCommitterConfig();
    private static final String ELEMENT_NAME_PARAM = "param";

    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private IdolClient idolClient;

    public IdolCommitterConfig getConfig() {
        return config;
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        // IDOL Client
        this.idolClient = new IdolClient(config);
        LOG.info("IDOL {}URL: {}",
                config.isCfs() ? "CFS " : "", config.getUrl());
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        idolClient.post(it);
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        config.setUrl(xml.getString("url", config.getUrl()));
        config.setCfs(xml.getBoolean("cfs", config.isCfs()));
        config.setDatabaseName(
                xml.getString("databaseName", config.getDatabaseName()));
        xml.ifXML("dreAddDataParams", x -> CollectionUtil.setAll(
                config.getDreAddDataParams(),
                x.getStringMap(ELEMENT_NAME_PARAM, "@name", ".")));
        xml.ifXML("dreDeleteRefParams", x -> CollectionUtil.setAll(
                config.getDreDeleteRefParams(),
                x.getStringMap(ELEMENT_NAME_PARAM, "@name", ".")));
        config.setSourceReferenceField(xml.getString(
                "sourceReferenceField", config.getSourceReferenceField()));
        config.setSourceContentField(xml.getString(
                "sourceContentField", config.getSourceContentField()));
    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        xml.addElement("url", config.getUrl());
        xml.addElement("cfs", config.isCfs());
        xml.addElement("databaseName", config.getDatabaseName());
        if (!config.getDreAddDataParams().isEmpty()) {
            XML x = xml.addElement("dreAddDataParams");
            config.getDreAddDataParams().forEach(
                    (k, v) -> x.addElement(ELEMENT_NAME_PARAM, v).setAttribute("name", k));
        }
        if (!config.getDreDeleteRefParams().isEmpty()) {
            XML x = xml.addElement("dreDeleteRefParams");
            config.getDreDeleteRefParams().forEach(
                    (k, v) -> x.addElement(ELEMENT_NAME_PARAM, v).setAttribute("name", k));
        }
        xml.addElement(
                "sourceReferenceField", config.getSourceReferenceField());
        xml.addElement("sourceContentField", config.getSourceContentField());
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