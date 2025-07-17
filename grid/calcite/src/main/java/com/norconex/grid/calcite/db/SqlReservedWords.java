package com.norconex.grid.calcite.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SqlReservedWords {

    private static Set<String> allReserved;
    private static boolean initialized = false;

    private SqlReservedWords() {
    }

    // ANSI SQL:2003 reserved words (add more as needed)
    private static final Set<String> ANSI_RESERVED = Set.of(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTHORIZATION",
            "BACKUP", "BEGIN", "BETWEEN", "BREAK", "BROWSE", "BULK", "BY",
            "CASCADE", "CASE", "CHECK", "CHECKPOINT", "CLOSE", "CLUSTERED",
            "COALESCE", "COLLATE", "COLUMN", "COMMIT", "COMPUTE", "CONSTRAINT",
            "CONTAINS", "CONTAINSTABLE", "CONTINUE", "CONVERT", "CREATE",
            "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIME",
            "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATABASE", "DBCC",
            "DEALLOCATE", "DECLARE", "DEFAULT", "DELETE", "DENY", "DESC",
            "DISK", "DISTINCT", "DISTRIBUTED", "DOUBLE", "DROP", "DUMP", "ELSE",
            "END", "ERRLVL", "ESCAPE", "EXCEPT", "EXEC", "EXECUTE", "EXISTS",
            "EXIT", "EXTERNAL", "FETCH", "FILE", "FILLFACTOR", "FOR", "FOREIGN",
            "FREETEXT", "FREETEXTTABLE", "FROM", "FULL", "FUNCTION", "GOTO",
            "GRANT", "GROUP", "HAVING", "HOLDLOCK", "IDENTITY",
            "IDENTITY_INSERT", "IDENTITYCOL", "IF", "IN", "INDEX",
            "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "KILL",
            "LEFT", "LIKE", "LINENO", "LOAD", "MERGE", "NATIONAL", "NOCHECK",
            "NONCLUSTERED", "NOT", "NULL", "NULLIF", "OF", "OFF", "OFFSETS",
            "ON", "OPEN", "OPENDATASOURCE", "OPENQUERY", "OPENROWSET",
            "OPENXML", "OPTION", "OR", "ORDER", "OUTER", "OVER", "PERCENT",
            "PIVOT", "PLAN", "PRECISION", "PRIMARY", "PRINT", "PROC",
            "PROCEDURE", "PUBLIC", "RAISERROR", "READ", "READTEXT",
            "RECONFIGURE", "REFERENCES", "REPLICATION", "RESTORE",
            "RESTRICT", "RETURN", "REVERT", "REVOKE", "RIGHT", "ROLLBACK",
            "ROWCOUNT", "ROWGUIDCOL", "RULE", "SAVE", "SCHEMA", "SECURITYAUDIT",
            "SELECT", "SEMANTICKEYPHRASETABLE",
            "SEMANTICSIMILARITYDETAILSTABLE", "SEMANTICSIMILARITYTABLE",
            "SESSION_USER", "SET", "SETUSER", "SHUTDOWN", "SOME", "STATISTICS",
            "SYSTEM_USER", "TABLE", "TABLESAMPLE", "TEXTSIZE",
            "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER", "TRUNCATE",
            "TRY_CONVERT", "TSEQUAL", "UNION", "UNIQUE", "UNPIVOT", "UPDATE",
            "UPDATETEXT", "USE", "USER", "VALUES", "VARYING", "VIEW", "WAITFOR",
            "WHEN", "WHERE", "WHILE", "WITH", "WITHIN GROUP", "WRITETEXT");

    /**
     * Returns a set of reserved words (ANSI + vendor-specific) for the
     * given connection. Results are cached so it is safe to invoke
     * multiple times: only the first invokation will use the connection.
     */
    public static synchronized Set<String> getReservedWords(Connection conn) {
        if (!initialized) {
            Set<String> reserved = new HashSet<>();
            // Add ANSI reserved words
            for (String word : ANSI_RESERVED) {
                reserved.add(word.toUpperCase(Locale.ROOT));
            }
            // Add vendor-specific keywords
            try {
                DatabaseMetaData meta = conn.getMetaData();
                String keywords = meta.getSQLKeywords();
                if (keywords != null && !keywords.isEmpty()) {
                    Arrays.stream(keywords.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(word -> reserved
                                    .add(word.toUpperCase(Locale.ROOT)));
                }
            } catch (SQLException e) {
                LOG.warn("Cannot get reserved words from database.", e);
            }
            SqlReservedWords.allReserved =
                    Collections.unmodifiableSet(reserved);
            initialized = true;
        }
        return SqlReservedWords.allReserved;
    }

    /**
     * Checks if the given word is a reserved SQL keyword. Results are cached
     * so it is safe to invoke multiple times: only the first invokation will
     * use the connection.
     */
    public static boolean isReservedWord(Connection conn, String word) {
        return getReservedWords(conn).contains(word.toUpperCase(Locale.ROOT));
    }
}
