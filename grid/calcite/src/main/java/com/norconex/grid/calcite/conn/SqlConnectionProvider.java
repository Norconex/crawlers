package com.norconex.grid.calcite.conn;

import java.sql.Connection;
import java.sql.SQLException;

public interface SqlConnectionProvider {

    Connection getConnection() throws SQLException;

    void close();
}
