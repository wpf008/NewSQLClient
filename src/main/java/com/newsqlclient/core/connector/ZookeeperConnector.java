package com.newsqlclient.core.connector;

import com.newsqlclient.core.ConnectionInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ZookeeperConnector implements Connector {

    private CuratorFramework client;
    private ConnectionInfo connectionInfo;
    private boolean connected;

    @Override
    public void connect(ConnectionInfo info) throws Exception {
        this.connectionInfo = info;
        String host = info.getHost() != null ? info.getHost() : "127.0.0.1";
        int port = info.getPort() > 0 ? info.getPort() : 2181;
        String connectString = host + ":" + port;

        client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(10000)
                .connectionTimeoutMs(10000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        client.blockUntilConnected(10, java.util.concurrent.TimeUnit.SECONDS);
        connected = true;
    }

    @Override
    public void disconnect() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
        connected = false;
    }

    @Override
    public boolean testConnection(ConnectionInfo info) {
        String host = info.getHost() != null ? info.getHost() : "127.0.0.1";
        int port = info.getPort() > 0 ? info.getPort() : 2181;
        try (CuratorFramework c = CuratorFrameworkFactory.builder()
                .connectString(host + ":" + port)
                .sessionTimeoutMs(5000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 1))
                .build()) {
            c.start();
            return c.blockUntilConnected(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> browse(String path) throws Exception {
        if (path == null || path.isEmpty()) path = "/";
        List<String> children = client.getChildren().forPath(path);
        List<String> result = new ArrayList<>();
        for (String child : children) {
            String fullPath = path.endsWith("/") ? path + child : path + "/" + child;
            Stat stat = client.checkExists().forPath(fullPath);
            if (stat != null && stat.getNumChildren() > 0) {
                result.add(child + "/");
            } else {
                result.add(child);
            }
        }
        java.util.Collections.sort(result);
        return result;
    }

    @Override
    public Object execute(String command) throws Exception {
        // command format: "get /path" or "stat /path" or "ls /path" or "delete /path"
        String[] parts = command.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String path = parts.length > 1 ? parts[1] : "/";

        return switch (cmd) {
            case "ls" -> {
                List<String> children = client.getChildren().forPath(path);
                yield String.join("\n", children);
            }
            case "get" -> {
                byte[] data = client.getData().forPath(path);
                yield data != null ? new String(data, StandardCharsets.UTF_8) : "(null)";
            }
            case "stat" -> {
                Stat stat = client.checkExists().forPath(path);
                yield stat != null ? stat.toString() : "(node not exists)";
            }
            case "delete" -> {
                client.delete().deletingChildrenIfNeeded().forPath(path);
                yield "Deleted: " + path;
            }
            case "create" -> {
                String[] createParts = path.split("\\s+", 2);
                String newPath = createParts[0];
                String value = createParts.length > 1 ? createParts[1] : "";
                String created = client.create().creatingParentsIfNeeded()
                        .forPath(newPath, value.getBytes(StandardCharsets.UTF_8));
                yield "Created: " + created;
            }
            default -> "Unknown command: " + cmd + "\nAvailable: ls, get, stat, delete, create";
        };
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public String getTypeName() { return "ZooKeeper"; }
}
