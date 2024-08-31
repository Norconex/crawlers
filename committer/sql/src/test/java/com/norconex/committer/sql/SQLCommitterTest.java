/* Copyright 2017-2024 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.security.Credentials;

class SQLCommitterTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(SQLCommitterTest.class);

    private static final String TEST_ID = "3";
    private static final String TEST_CONTENT = "This is test content.";
    private static final String TEST_TABLE = "test_table";
    private static final String TEST_FLD_PK = "ID";
    private static final String TEST_FLD_CONTENT = "CONTENT";
    private static final String DRIVER_CLASS = "org.h2.Driver";

    private static String connectionURL;

    @TempDir
    File tempDir;

    @BeforeEach
    void beforeEach() {
        LOG.debug("Creating new database.");
        connectionURL = "jdbc:h2:"
                + tempDir.getAbsolutePath()
                + ";WRITE_DELAY=0;AUTOCOMMIT=ON;"
                + "AUTO_SERVER=TRUE;USER=sa;PASSWORD=123";
        try {
            withinDbSession(qr -> qr.update("DROP TABLE " + TEST_TABLE));
        } catch (SQLException e) {
            // OK if not found.
        }
    }

    @Test
    void testCommitAdd() throws Exception {
        // Add new doc to SQL table
        withinCommitterSession(c -> {
            c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
        });
        var docs = getAllDocs();
        assertThat(docs).hasSize(1);
        assertTestDoc(docs.get(0));
    }

    @Test
    void testCommitAdd_emptyConnUrl_throwsException() {
        // Setup
        Exception expectedException = null;

        // Execute
        try {
            withinCommitterSessionEmptyConnUrl(c -> {
                c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
            });
        } catch (Exception e) {
            expectedException = e;
        }

        // Verify
        assertThat(expectedException)
                .isNotNull()
                .isInstanceOf(CommitterException.class)
                .hasMessage("No connection URL specified.");
    }

    @Test
    void testCommitAdd_emptyTableName_throwsException() {
        // Setup
        Exception expectedException = null;

        // Execute
        try {
            withinCommitterSessionEmptyTableName(c -> {
                c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
            });
        } catch (Exception e) {
            expectedException = e;
        }

        // Verify
        assertThat(expectedException)
                .isNotNull()
                .isInstanceOf(CommitterException.class)
                .hasMessage("No table name specified.");
    }

    @Test
    void testCommitAdd_emptyPrimaryKey_throwsException() {
        // Setup
        Exception expectedException = null;

        // Execute
        try {
            withinCommitterSessionEmptyPrimaryKey(c -> {
                c.upsert(upsertRequest(TEST_ID, TEST_CONTENT));
            });
        } catch (Exception e) {
            expectedException = e;
        }

        // Verify
        assertThat(expectedException)
                .isNotNull()
                .isInstanceOf(CommitterException.class)
                .hasMessage("No primary key specified.");
    }

    @Test
    void testAddWithQueueContaining2documents() throws Exception {
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
        });

        //Check that there is 2 documents in SQL table
        assertThat(getAllDocs()).hasSize(2);
    }

    @Test
    void testCommitQueueWith3AddCommandAnd1DeleteCommand()
            throws Exception {
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
            c.delete(new DeleteRequest("1", new Properties()));
            c.upsert(upsertRequest("3", "Document 3"));
        });

        //Check that there are 2 documents in SQL table
        assertThat(getAllDocs()).hasSize(2);
    }

    @Test
    void testCommitQueueWith3AddCommandAnd2DeleteCommand()
            throws Exception {
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
            c.upsert(upsertRequest("2", "Document 2"));
            c.delete(new DeleteRequest("1", new Properties()));
            c.delete(new DeleteRequest("2", new Properties()));
            c.upsert(upsertRequest("3", "Document 3"));
        });

        //Check that there is 1 document in SQL table
        assertThat(getAllDocs()).hasSize(1);
    }

    @Test
    void testCommitDelete() throws Exception {

        // Add a document
        withinCommitterSession(c -> {
            c.upsert(upsertRequest("1", "Document 1"));
        });

        // Delete it in a new session.
        withinCommitterSession(c -> {
            c.delete(new DeleteRequest("1", new Properties()));
        });

        // Check that it's remove from SQL table
        assertThat(getAllDocs()).isEmpty();
    }

    @Test
    void testMultiValueFields() throws Exception {
        var metadata = new Properties();
        var fieldname = "MULTI";
        metadata.set(fieldname, "1", "2", "3");

        withinCommitterSession(c -> {
            c.getConfiguration().setMultiValuesJoiner("_");
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        // Check that it's in SQL table
        var docs = getAllDocs();
        assertThat(getAllDocs()).hasSize(1);
        var doc = docs.get(0);

        // Check multi values are still there
        assertThat(doc.get(fieldname).toString().split("_")).hasSize(3);
    }

    @Test
    void testFixFieldValues() throws Exception {
        withinCommitterSession(c -> {
            var metadata = new Properties();
            metadata.set("LONGSINGLE", StringUtils.repeat("a", 50));
            metadata.set(
                    "LONGMULTI",
                    StringUtils.repeat("a", 10),
                    StringUtils.repeat("b", 10),
                    StringUtils.repeat("c", 10),
                    StringUtils.repeat("d", 10));

            var config = c.getConfiguration();
            config.setFixFieldValues(true);
            config.setMultiValuesJoiner("-");
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        var docs = getAllDocs();
        assertThat(docs).hasSize(1);

        var doc = docs.get(0);
        assertThat(doc.get(TEST_FLD_PK))
                .isInstanceOf(String.class)
                .isEqualTo(TEST_ID);
        // Check values were truncated
        assertThat(doc.get("LONGSINGLE"))
                .isInstanceOf(String.class)
                .isEqualTo(StringUtils.repeat("a", 30));
        assertThat(doc.get("LONGMULTI"))
                .isInstanceOf(String.class)
                .isEqualTo(
                        StringUtils.repeat("a", 10) + "-"
                                + StringUtils.repeat("b", 10) + "-"
                                + StringUtils.repeat("c", 8));
    }

    @Test
    void testFixFieldNames() throws Exception {
        withinCommitterSession(c -> {
            var metadata = new Properties();
            metadata.set("A$B&C %E_F", "test1");
            metadata.set("99FIELD2", "test2");
            metadata.set("*FIELD3", "test3");

            var config = c.getConfiguration();
            config.setFixFieldNames(true);
            c.upsert(upsertRequest(TEST_ID, null, metadata));
        });

        var docs = getAllDocs();
        assertThat(docs).hasSize(1);

        var doc = docs.get(0);
        assertThat(doc.get(TEST_FLD_PK))
                .isInstanceOf(String.class)
                .isEqualTo(TEST_ID);

        // Check values were truncated
        assertThat(doc.get("A_B_C_E_F"))
                .isInstanceOf(String.class)
                .isEqualTo("test1");
        assertThat(doc.get("FIELD2"))
                .isInstanceOf(String.class)
                .isEqualTo("test2");
        assertThat(doc.get("FIELD3"))
                .isInstanceOf(String.class)
                .isEqualTo("test3");
    }

    private UpsertRequest upsertRequest(String id, String content) {
        return upsertRequest(id, content, null);
    }

    private UpsertRequest upsertRequest(
            String id, String content, Properties metadata) {
        var p = metadata == null ? new Properties() : metadata;
        return new UpsertRequest(
                id, p, content == null
                        ? new NullInputStream(0)
                        : toInputStream(content, UTF_8));
    }

    private void assertTestDoc(Map<String, Object> doc) {
        assertThat(doc.get(TEST_FLD_PK))
                .isInstanceOf(String.class)
                .isEqualTo(TEST_ID);
        assertThat(doc.get(TEST_FLD_CONTENT))
                .isInstanceOf(String.class)
                .isEqualTo(TEST_CONTENT);
    }

    private CommitterContext createCommitterContext() {
        return CommitterContext.builder()
                .setWorkDir(
                        new File(
                                tempDir,
                                "work-" + TimeIdGenerator.next()).toPath())
                .build();
    }

    private SQLCommitter createSQLCommitterNoInit()
            throws CommitterException {
        var committer = new SQLCommitter();
        var config = committer.getConfiguration();
        config.setConnectionUrl(connectionURL);
        config.setDriverClass(DRIVER_CLASS);
        config.setTableName(TEST_TABLE);
        config.setPrimaryKey(TEST_FLD_PK);
        config.setCredentials(new Credentials("sa", "123"));

        var props = new Properties();
        props.add("key1", "value1");
        config.setProperties(props);

        config.setCreateTableSQL(
                """
                        CREATE TABLE {tableName} (\
                          {primaryKey} VARCHAR(32672) NOT NULL,\s\
                          content CLOB,\s\
                          PRIMARY KEY ({primaryKey})\s\
                        )""");
        config.setCreateFieldSQL(
                "ALTER TABLE {tableName} ADD {fieldName} VARCHAR(30)");

        return committer;
    }

    private SQLCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        var committer = createSQLCommitterNoInit();
        committer.init(createCommitterContext());
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    private SQLCommitter withinCommitterSessionEmptyConnUrl(
            CommitterConsumer c) throws CommitterException {

        var committer = createSQLCommitterEmptyConnUrl();
        committer.init(createCommitterContext());
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    private SQLCommitter createSQLCommitterEmptyConnUrl() {
        var committer = new SQLCommitter();
        var config = committer.getConfiguration();
        config.setConnectionUrl("");
        config.setDriverClass(DRIVER_CLASS);

        return committer;
    }

    private SQLCommitter withinCommitterSessionEmptyTableName(
            CommitterConsumer c) throws CommitterException {

        var committer = createSQLCommitterEmptyTableName();
        committer.init(createCommitterContext());
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    private SQLCommitter createSQLCommitterEmptyTableName() {
        var committer = new SQLCommitter();
        var config = committer.getConfiguration();
        config.setConnectionUrl(connectionURL);
        config.setDriverClass(DRIVER_CLASS);
        config.setTableName("");

        return committer;
    }

    private SQLCommitter withinCommitterSessionEmptyPrimaryKey(
            CommitterConsumer c) throws CommitterException {

        var committer = createSQLCommitterEmptyPrimaryKey();
        committer.init(createCommitterContext());
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }

    private SQLCommitter createSQLCommitterEmptyPrimaryKey() {
        var committer = new SQLCommitter();
        var config = committer.getConfiguration();
        config.setConnectionUrl(connectionURL);
        config.setDriverClass(DRIVER_CLASS);
        config.setTableName(TEST_TABLE);
        config.setPrimaryKey("");

        return committer;
    }

    private <T> T withinDbSession(DbFunction<T> c) throws SQLException {
        var datasource = new BasicDataSource();
        datasource.setDriverClassName(DRIVER_CLASS);
        datasource.setUrl(connectionURL);
        datasource.setDefaultAutoCommit(true);
        var t = c.apply(new QueryRunner(datasource));
        datasource.close();
        return t;
    }

    @FunctionalInterface
    private interface CommitterConsumer {
        void accept(SQLCommitter c) throws Exception;
    }

    @FunctionalInterface
    private interface DbFunction<T> {
        T apply(QueryRunner q) throws SQLException;
    }

    private List<Map<String, Object>> getAllDocs() throws SQLException {
        return withinDbSession(
                qr -> qr.query(
                        "SELECT * FROM " + TEST_TABLE,
                        new MapListHandler(new ClobAwareRowProcessor())));
    }

    class ClobAwareRowProcessor extends BasicRowProcessor {
        @Override
        public Map<String, Object> toMap(ResultSet resultSet)
                throws SQLException {
            var resultSetMetaData = resultSet.getMetaData();
            var columnCount = resultSetMetaData.getColumnCount();
            Map<String, Object> map = new HashMap<>();
            for (var index = 1; index <= columnCount; ++index) {
                var columnName = resultSetMetaData.getColumnName(index);
                var object = resultSet.getObject(index);
                if (object instanceof Clob) {
                    object = resultSet.getString(index);
                }
                map.put(columnName, object);
            }
            return map;
        }
    }
}
