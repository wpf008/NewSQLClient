package com.newsqlclient.core;

import com.newsqlclient.core.connector.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> infos = new ConcurrentHashMap<>();

    public void addConnection(ConnectionInfo info) throws Exception {
        Connector connector = switch (info.getConnType()) {
            case DATABASE -> new DatabaseConnector();
            case REDIS -> new RedisConnector();
            case ZOOKEEPER -> new ZookeeperConnector();
        };
        connector.connect(info);
        connectors.put(info.getId(), connector);
        infos.put(info.getId(), info);
    }

    public void disconnect(String id) throws Exception {
        Connector c = connectors.remove(id);
        if (c != null) c.disconnect();
        // Info is kept so the connection can be reconnected later
    }

    public void reconnect(String id) throws Exception {
        ConnectionInfo info = infos.get(id);
        if (info == null) throw new Exception("Connection info not found");
        addConnection(info);
    }

    public void removeConnection(String id) throws Exception {
        Connector c = connectors.remove(id);
        if (c != null) c.disconnect();
        infos.remove(id);
    }

    public Connector getConnector(String id) {
        return connectors.get(id);
    }

    public ConnectionInfo getInfo(String id) {
        return infos.get(id);
    }

    public void rememberConnection(ConnectionInfo info) {
        infos.put(info.getId(), info);
    }

    public List<ConnectionInfo> getAllConnections() {
        return List.copyOf(infos.values());
    }

    public boolean isConnected(String id) {
        Connector c = connectors.get(id);
        return c != null && c.isConnected();
    }

    public void disconnectAll() {
        for (Connector c : connectors.values()) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
        connectors.clear();
        infos.clear();
    }
}
