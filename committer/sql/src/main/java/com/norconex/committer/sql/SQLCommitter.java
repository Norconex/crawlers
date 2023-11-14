/* Copyright 2017-2023 Norconex Inc.
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

import java.util.Iterator;

import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ToStringExclude;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.time.DurationParser;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Commit documents to an SQL table. Document metadata fields
 * are mapped to table columns.
 * </p>
 *
 * <h3>Handling of missing table/fields</h3>
 * <p>
 * By default, this Committer will throw an exception when trying to insert
 * values into non-existing database table or fields. It is recommended you
 * make sure your database table exists and the document fields being sent
 * to the committer match your database fields.
 * </p>
 * <p>
 * Alternatively, you can provide the necessary SQLs to create a new
 * table as well as new fields as needed using
 * {@link SQLCommitterConfig#setCreateTableSQL(String)} and
 * {@link SQLCommitterConfig#setCreateFieldSQL(String)}
 * respectively. Make sure to use the following placeholder variables
 * as needed in the provided SQL(s) table and field creation, respectively:
 * </p>
 *
 * <h4>Table creation</h4>
 * <dl>
 *   <dt>{tableName}</dt>
 *   <dd>
 *     Your table name, to be replaced with the value supplied with
 *     {@link SQLCommitterConfig#setTableName(String)}.
 *   </dd>
 *   <dt>{primaryKey}</dt>
 *   <dd>
 *     Your table primary key field name, to be replaced with the value
 *     supplied with {@link SQLCommitterConfig#setPrimaryKey(String)}.
 *   </dd>
 * </dl>
 * <h4>Field creation</h4>
 * <dl>
 *   <dt>{fieldName}</dt>
 *   <dd>
 *     A field name to be created if you provided an SQL for creating new
 *     fields.
 *   </dd>
 * </dl>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.sql.SQLCommitter">
 *   <!-- Mandatory settings -->
 *   <driverClass>
 *     (Class name of the JDBC driver to use.)
 *   </driverClass>
 *   <connectionUrl>
 *     (JDBC connection URL.)
 *   </connectionUrl>
 *   <tableName>
 *     (The target database table name where documents will be committed.)
 *   </tableName>
 *   <primaryKey>
 *     (The name of the table primary key field where the document
 *     reference will be stored, unless it is already set by a field of
 *     the same name in the source document. At a minimum, this "primaryKey"
 *     field name should be "unique", ideally indexed.).
 *   </primaryKey>
 *
 *   <!-- Other settings -->
 *   <driverPath>
 *     (Path to JDBC driver. Not required if already in classpath.)
 *   </driverPath>
 *   <properties>
 *     <property name="(property name)">(Property value.)</property>
 *     <!-- You can have multiple property. -->
 *   </properties>
 *
 *   <createTableSQL>
 *     <!--
 *       The CREATE statement used to create a table if it does not
 *       already exist. If you need fields of specific data types,
 *       specify them here. You can use the variable placeholders {tableName}
 *       and {primaryKey} which will be replaced with the configuration option
 *       of the same name. If you do not use those variables, make sure you use
 *       the same names.
 *       See usage sample.
 *       -->
 *   </createTableSQL>
 *   <createFieldSQL>
 *     <!--
 *       The ALTER statement used to create missing table fields.
 *       The {tableName} variable will be replaced with
 *       the configuration option of the same name. The {fieldName}
 *       variable will be replaced by newly encountered field names.
 *       See usage sample.
 *       -->
 *   </createFieldSQL>
 *   <multiValuesJoiner>
 *     (One or more characters to join multi-value fields.
 *     Default is "|".)
 *   </multiValuesJoiner>
 *   <fixFieldNames>
 *     [false|true]
 *     (Attempts to prevent insertion errors by converting characters that
 *     are not underscores or alphanumeric to underscores.
 *     Will also remove all non-alphabetic characters that prefixes
 *     a field name.)
 *   </fixFieldNames>
 *   <fixFieldValues>
 *     [false|true]
 *     (Attempts to prevent insertion errors by truncating values
 *     that are larger than their defined maximum field length.)
 *   </fixFieldValues>
 *
 *   <!-- Use the following if authentication is required. -->
 *   <credentials>
 *     {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   </credentials>
 *
 *   <targetContentField>
 *     (Table field name where to store the document content stream.
 *     Make it empty or a self-closed tag if you do not want to store the
 *     document content. Since document content can sometimes be quite
 *     large, a CLOB field is usually advised.
 *     If there is already a document field with the same name, that
 *     document field takes precedence and the content stream is ignored.
 *     Default is "content".)
 *   </targetContentField>
 *
 *   {@nx.include com.norconex.committer.core.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.sql.SQLCommitter">
 *   <driverPath>/path/to/driver/h2.jar</driverPath>
 *   <driverClass>org.h2.Driver</driverClass>
 *   <connectionUrl>jdbc:h2:file:///path/to/db/h2</connectionUrl>
 *   <tableName>test_table</tableName>
 *   <createTableSQL>
 *     CREATE TABLE {tableName} (
 *         {primaryKey} VARCHAR(32672) NOT NULL,
 *         content CLOB,
 *         PRIMARY KEY ( {primaryKey} ),
 *         title   VARCHAR(256)
 *         author  VARCHAR(256)
 *     )
 *   </createTableSQL>
 *   <createFieldSQL>
 *     ALTER TABLE {tableName} ADD {fieldName} VARCHAR(5000)
 *   </createFieldSQL>
 *   <fixFieldValues>true</fixFieldValues>
 * </committer>
 * }
 * <p>
 * The above example uses an H2 database and creates the table and fields
 * as they are encountered, storing all new fields as VARCHAR, making sure
 * those new fields are no longer than 5000 characters.
 * </p>
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class SQLCommitter extends AbstractBatchCommitter<SQLCommitterConfig> {

    @Getter
    private final SQLCommitterConfig configuration = new SQLCommitterConfig();

    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private SQLClient client;

    @Override
    protected void initBatchCommitter() throws CommitterException {
        client = new SQLClient(configuration);
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
        client = null;
    }
}
