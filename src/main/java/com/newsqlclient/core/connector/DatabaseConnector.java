package com.newsqlclient.core.connector;

import com.newsqlclient.core.ConnectionInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class DatabaseConnector implements Connector {

    private HikariDataSource dataSource;
    private ConnectionInfo connectionInfo;
    private boolean connected;
    private String activeCatalog;

    @Override
    public void connect(ConnectionInfo info) throws Exception {
        this.connectionInfo = info;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(info.buildJdbcUrl());
        config.setUsername(info.getUser());
        config.setPassword(info.getPassword());
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        dataSource = new HikariDataSource(config);
        try (Connection conn = getConnection()) {
            connected = true;
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        connected = false;
    }

    @Override
    public boolean testConnection(ConnectionInfo info) {
        try {
            Class.forName(switch (info.getDbType()) {
                case "mysql" -> "com.mysql.cj.jdbc.Driver";
                case "postgresql" -> "org.postgresql.Driver";
                case "sqlite" -> "org.sqlite.JDBC";
                default -> "com.mysql.cj.jdbc.Driver";
            });
            try (Connection conn = DriverManager.getConnection(info.buildJdbcUrl(), info.getUser(), info.getPassword())) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> browse(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            return listDatabases();
        }
        if (!path.contains(".")) {
            return listTables(path);
        }
        String[] parts = path.split("\\.");
        return listColumns(parts[0], parts[1]);
    }

    private List<String> listDatabases() throws Exception {
        List<String> dbs = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if ("postgresql".equals(connectionInfo.getDbType())) {
                try (ResultSet rs = meta.getSchemas()) {
                    while (rs.next()) {
                        String schema = rs.getString("TABLE_SCHEM");
                        // Skip system schemas
                        if (!schema.startsWith("pg_") && !"information_schema".equals(schema)) {
                            dbs.add(schema);
                        }
                    }
                }
            } else {
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) dbs.add(rs.getString("TABLE_CAT"));
                }
            }
        }
        if (dbs.isEmpty()) {
            dbs.add(connectionInfo.getDatabase());
        }
        return dbs;
    }

    private List<String> listTables(String schema) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String name = (schema == null || schema.isEmpty()) ? null : schema;
            try (ResultSet rs = meta.getTables(
                    "postgresql".equals(connectionInfo.getDbType()) ? null : name,
                    "postgresql".equals(connectionInfo.getDbType()) ? name : null,
                    "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private List<String> listColumns(String schema, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String name = (schema == null || schema.isEmpty()) ? null : schema;
            try (ResultSet rs = meta.getColumns(
                    "postgresql".equals(connectionInfo.getDbType()) ? null : name,
                    "postgresql".equals(connectionInfo.getDbType()) ? name : null,
                    table, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    cols.add(colName + "  [" + type + "(" + size + ")]");
                }
            }
        }
        return cols;
    }

    @Override
    public Object execute(String command) throws Exception {
        if (command.trim().toUpperCase().startsWith("SELECT") || command.trim().toUpperCase().startsWith("SHOW")
                || command.trim().toUpperCase().startsWith("DESCRIBE") || command.trim().toUpperCase().startsWith("DESC")
                || command.trim().toUpperCase().startsWith("EXPLAIN") || command.trim().toUpperCase().startsWith("PRAGMA")) {
            return executeQuery(command);
        }
        return executeUpdate(command);
    }

    public java.util.List<java.util.Map<String, Object>> executeQuery(String sql) throws Exception {
        return executeQueryInternal(sql);
    }

    public java.util.List<java.util.Map<String, Object>> executePagedQuery(String sql, int offset, int limit) throws Exception {
        String pagedSql = sql.trim();
        // Remove trailing semicolon
        if (pagedSql.endsWith(";")) pagedSql = pagedSql.substring(0, pagedSql.length() - 1);
        pagedSql = pagedSql + " LIMIT " + limit + " OFFSET " + offset;
        return executeQueryInternal(pagedSql);
    }

    public int executeCountQuery(String sql) throws Exception {
        String countSql = sql.trim();
        if (countSql.endsWith(";")) countSql = countSql.substring(0, countSql.length() - 1);
        countSql = "SELECT COUNT(*) FROM (" + countSql + ") AS _cnt";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    private java.util.List<java.util.Map<String, Object>> executeQueryInternal(String sql) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public String executeUpdate(String sql) throws Exception {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            int affected = stmt.executeUpdate(sql);
            return "Affected rows: " + affected;
        }
    }

    public void setActiveCatalog(String catalog) {
        this.activeCatalog = catalog;
    }

    public String getActiveCatalog() {
        return activeCatalog;
    }

    private Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        if (activeCatalog != null && !activeCatalog.isEmpty()) {
            if ("postgresql".equals(connectionInfo.getDbType())) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET search_path TO " + activeCatalog);
                } catch (SQLException ignored) {}
            } else {
                try {
                    conn.setCatalog(activeCatalog);
                } catch (SQLException ignored) {}
            }
        }
        return conn;
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public String getTypeName() { return "Database"; }
}
