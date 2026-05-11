package com.newsqlclient.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConnectionInfo.class, name = "generic")
})
public class ConnectionInfo {
    public enum ConnType { DATABASE, REDIS, ZOOKEEPER }

    private String id;
    private String name;
    private ConnType connType;
    private String host;
    private int port;
    private String user;
    private String password;
    private String database;      // DB: db name, Redis: unused, ZK: unused
    private String dbType;        // mysql, postgresql, sqlite
    private String extra;         // DB: JDBC URL override, Redis: cluster nodes, ZK: root path

    public ConnectionInfo() {}

    public ConnectionInfo(String id, String name, ConnType connType) {
        this.id = id;
        this.name = name;
        this.connType = connType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ConnType getConnType() { return connType; }
    public void setConnType(ConnType connType) { this.connType = connType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }

    public String buildJdbcUrl() {
        if (extra != null && !extra.isBlank()) return extra;
        return switch (dbType) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            case "postgresql" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "sqlite" -> "jdbc:sqlite:" + database;
            default -> "jdbc:mysql://" + host + ":" + port + "/" + database;
        };
    }

    public String toString() { return name; }
}
