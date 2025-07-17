package com.norconex.grid.calcite.conn;

import static java.util.Optional.ofNullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;

import com.norconex.grid.calcite.CalciteGridConnectorConfig;
import com.norconex.grid.calcite.MultiTenancyMode;
import com.norconex.grid.core.GridConnectionContext;
import com.norconex.grid.core.GridException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * SQL connection provider for JDBC adapters.
 */
@Slf4j
public class JdbcConnectionProvider implements SqlConnectionProvider {

    private final DataSource dataSource;
    private final SchemaPlus rootSchema;
    private final Properties info;
    private final String calciteSchemaName;

    public JdbcConnectionProvider(
            CalciteGridConnectorConfig cfg, GridConnectionContext ctx) {
        this.dataSource = createDataSource(cfg);
        LOG.debug("✔️ JDBC datasource created.");

        var tenancyMode = ofNullable(cfg.getMultiTenancyMode())
                .orElse(MultiTenancyMode.DATABASE);
        this.calciteSchemaName = tenancyMode == MultiTenancyMode.SCHEMA
                ? ofNullable(cfg.getGridName()).orElseGet(ctx::getGridName)
                : "calciteSchema";

        // Create a Calcite schema with the DataSource
        this.rootSchema = Frameworks.createRootSchema(true);
        var jdbcSchema = JdbcSchema.create(
                rootSchema,
                calciteSchemaName, // mandatory to have one for calcite
                dataSource,
                null,
                tenancyMode == MultiTenancyMode.SCHEMA
                        ? calciteSchemaName
                        : null); // null = default database name from vendor
        rootSchema.add(calciteSchemaName, jdbcSchema);

        // Initialize Properties
        this.info = new Properties();
        cfg.getAdapterProperties().forEach((k, v) -> {
            if (!k.startsWith("datasource.")) {
                info.setProperty(k, Objects.toString(v, null));
            }
        });
        info.setProperty("caseSensitive", "false");

        // Create tenant-specific schema for schema-level multi-tenancy
        if (tenancyMode == MultiTenancyMode.SCHEMA) {
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "CREATE SCHEMA IF NOT EXISTS " + calciteSchemaName);
            } catch (SQLException e) {
                throw new GridException(
                        "Failed to create schema: " + calciteSchemaName, e);
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection( //NOSONAR
                "jdbc:calcite:", info);
        CalciteConnection calciteConnection =
                conn.unwrap(CalciteConnection.class);
        calciteConnection.setSchema(calciteSchemaName); // Set default schema
        return conn;
    }

    @Override
    public void close() {
        ((HikariDataSource) dataSource).close();
        LOG.debug("✖️ JDBC datasource closed.");

    }

    /**
     * Enhance the JDBC adapter with HikariCP connection pooling.
     */
    private static DataSource createDataSource(CalciteGridConnectorConfig cfg) {
        var props = cfg.getAdapterProperties();
        String jdbcUrl = Objects.toString(props.get("jdbcUrl"), null);
        String jdbcDriver = Objects.toString(props.get("jdbcDriver"), null);
        String jdbcUser = Objects.toString(props.get("jdbcUser"), null);
        String jdbcPassword = Objects.toString(props.get("jdbcPassword"), null);
        Objects.requireNonNull(jdbcUrl, "jdbcUrl is required");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        if (jdbcDriver != null) {
            hikariConfig.setDriverClassName(jdbcDriver);
        }
        if (jdbcUser != null) {
            hikariConfig.setUsername(jdbcUser);
        }
        if (jdbcPassword != null) {
            hikariConfig.setPassword(jdbcPassword);
        }

        // Apply any datasource.* properties from the schema
        props.forEach((k, v) -> {
            if (k.startsWith("datasource.")) {
                String hikariProp = k.substring(7);
                hikariConfig.addDataSourceProperty(hikariProp,
                        Objects.toString(v, null));
            }
        });
        return new HikariDataSource(hikariConfig);
    }
}
