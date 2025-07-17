package com.norconex.grid.calcite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class CalciteSchemaUtil {

    public static CalciteConnection createCalciteConnectionWithSchema(
            CalciteGridConnectorConfig config,
            String schemaName // logical schema name in Calcite
    ) throws Exception {
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection("jdbc:calcite:", info);
        CalciteConnection calciteConn = conn.unwrap(CalciteConnection.class);
        SchemaPlus rootSchema = calciteConn.getRootSchema();

        Map<String, Object> props = config.getAdapterProperties();
        String adapterType = config.getAdapterType();

        if ("jdbc".equalsIgnoreCase(adapterType)) {
            // --- JDBC: Build Hikari DataSource ---
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig
                    .setJdbcUrl(Objects.toString(props.get("jdbcUrl"), null));
            hikariConfig.setDriverClassName(
                    Objects.toString(props.get("jdbcDriver"), null));
            if (props.get("jdbcUser") != null) {
                hikariConfig.setUsername(
                        Objects.toString(props.get("jdbcUser"), null));
            }
            if (props.get("jdbcPassword") != null) {
                hikariConfig.setPassword(
                        Objects.toString(props.get("jdbcPassword"), null));
            }
            // Optionally add more Hikari properties here

            DataSource dataSource = new HikariDataSource(hikariConfig);

            // Register JDBC schema (null for DB schema uses default)
            rootSchema.add(
                    schemaName,
                    JdbcSchema.create(rootSchema, null, dataSource, null,
                            null));
        } else {
            // --- Non-JDBC: Use factory and operand ---
            String factoryClass = Objects.toString(props.get("factory"), null);
            if (factoryClass == null) {
                throw new IllegalArgumentException(
                        "Missing 'factory' property for non-JDBC adapter.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> operand =
                    (Map<String, Object>) props.get("operand");
            if (operand == null) {
                throw new IllegalArgumentException(
                        "Missing 'operand' property for non-JDBC adapter.");
            }
            SchemaFactory factory = (SchemaFactory) Class.forName(factoryClass)
                    .getDeclaredConstructor().newInstance();
            rootSchema.add(
                    schemaName,
                    factory.create(rootSchema, schemaName, operand));
        }

        // Set as default schema
        calciteConn.setSchema(schemaName);
        return calciteConn;
    }
}
