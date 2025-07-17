package com.norconex.grid.calcite.conn;

import static java.util.Optional.ofNullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;

import com.norconex.grid.calcite.CalciteGridConnectorConfig;
import com.norconex.grid.calcite.MultiTenancyMode;
import com.norconex.grid.core.GridConnectionContext;
import com.norconex.grid.core.GridException;

/**
 * SQL connection provider for non-JDBC adapters, supporting schema-level
 * multi-tenancy by setting the Calcite schema name to the tenant identifier
 * (gridName). Schema-level multi-tenancy depends on the adapter's ability to
 * use the schema name or adapter-specific properties (e.g., directory,
 * keyspace) for tenant isolation.
 * If the adapter does not support schema-level isolation, it behaves as a
 * single schema.
 */
public class FallbackConnectionProvider implements SqlConnectionProvider {

    private final SchemaPlus rootSchema;
    private final Properties info;
    private final String calciteSchemaName;

    public FallbackConnectionProvider(
            CalciteGridConnectorConfig cfg, GridConnectionContext ctx) {
        // Validate required properties
        var props = cfg.getAdapterProperties();
        String factoryClass = Objects.toString(props.get("factory"), null);
        if (factoryClass == null) {
            throw new IllegalArgumentException(
                    "Adapter property 'factory' is required for "
                            + "non-JDBC adapter");
        }

        // Determine tenancy mode and schema name
        var tenancyMode = ofNullable(cfg.getMultiTenancyMode())
                .orElse(MultiTenancyMode.SCHEMA);
        this.calciteSchemaName = tenancyMode == MultiTenancyMode.DATABASE
                ? ofNullable(cfg.getGridName()).orElseGet(ctx::getGridName)
                : "calciteSchema";

        // Initialize schema
        this.rootSchema = Frameworks.createRootSchema(true);
        try {
            SchemaFactory schemaFactory =
                    (SchemaFactory) Class.forName(factoryClass)
                            .getDeclaredConstructor()
                            .newInstance();
            // Pass adapter properties directly, relying on adapter-specific
            // tenant isolation
            Schema schema =
                    schemaFactory.create(rootSchema, calciteSchemaName, props);
            rootSchema.add(calciteSchemaName, schema);
        } catch (Exception e) {
            throw new GridException(
                    "Failed to create schema for factory: " + factoryClass, e);
        }

        // Initialize Properties
        this.info = new Properties();
        props.forEach((k, v) -> info.setProperty(k, Objects.toString(v, null)));
        info.setProperty("caseSensitive", "false");
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
        // No resources to close for most non-JDBC adapters
        // Custom adapters may override if cleanup is needed
    }
}
