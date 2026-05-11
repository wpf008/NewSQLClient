package com.newsqlclient.core.connector;

import com.newsqlclient.core.ConnectionInfo;
import redis.clients.jedis.*;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.Tuple;

import java.util.*;

public class RedisConnector implements Connector {

    private JedisPool jedisPool;
    private ConnectionInfo connectionInfo;
    private boolean connected;
    private int currentDb = 0;

    @Override
    public void connect(ConnectionInfo info) throws Exception {
        this.connectionInfo = info;
        String host = info.getHost() != null ? info.getHost() : "127.0.0.1";
        int port = info.getPort() > 0 ? info.getPort() : 6379;
        String password = info.getPassword();
        int timeout = 10000;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(5);
        poolConfig.setMaxIdle(2);

        if (password != null && !password.isBlank()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeout);
        }

        try (Jedis j = jedisPool.getResource()) {
            j.ping();
        }
        connected = true;
    }

    @Override
    public void disconnect() throws Exception {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
        connected = false;
    }

    @Override
    public boolean testConnection(ConnectionInfo info) {
        String host = info.getHost() != null ? info.getHost() : "127.0.0.1";
        int port = info.getPort() > 0 ? info.getPort() : 6379;
        String password = info.getPassword();

        try (Jedis j = new Jedis(host, port, 5000)) {
            if (password != null && !password.isBlank()) {
                j.auth(password);
            }
            return "PONG".equals(j.ping());
        } catch (JedisException e) {
            return false;
        }
    }

    @Override
    public List<String> browse(String path) throws Exception {
        List<String> result = new ArrayList<>();
        try (Jedis j = jedisPool.getResource()) {
            if (path == null || path.isEmpty()) {
                // Return database list: db0 - db15
                for (int i = 0; i < 16; i++) {
                    result.add("db" + i);
                }
            } else if (path.startsWith("db") && path.length() <= 4 && !path.contains(":")) {
                // Path like "db0" → switch to that DB and list keys
                int dbIndex = Integer.parseInt(path.substring(2));
                j.select(dbIndex);
                currentDb = dbIndex;
                Set<String> keys = j.keys("*");
                if (keys.size() > 500) {
                    result.add("... too many keys (" + keys.size() + "), use search");
                    result.addAll(new ArrayList<>(keys).subList(0, 100));
                } else {
                    result.addAll(keys);
                }
                Collections.sort(result);
            } else {
                // Browse a specific key
                String type = j.type(path);
                result.add("type:" + type);
                switch (type) {
                    case "string" -> result.add("value:" + j.get(path));
                    case "hash" -> {
                        Map<String, String> hash = j.hgetAll(path);
                        hash.forEach((k, v) -> result.add(k + " = " + v));
                    }
                    case "list" -> {
                        List<String> list = j.lrange(path, 0, 99);
                        for (int i = 0; i < list.size(); i++) result.add("[" + i + "] " + list.get(i));
                        result.add("length: " + j.llen(path));
                    }
                    case "set" -> {
                        Set<String> set = j.smembers(path);
                        result.addAll(set);
                        result.add("size: " + j.scard(path));
                    }
                    case "zset" -> {
                        List<Tuple> zset = j.zrangeWithScores(path, 0, 99);
                        for (Tuple t : zset) result.add(t.getElement() + " -> " + t.getScore());
                        result.add("size: " + j.zcard(path));
                    }
                    default -> result.add("(unknown or empty)");
                }
                // TTL
                long ttl = j.ttl(path);
                result.add("ttl:" + (ttl == -1 ? "no expiry" : ttl == -2 ? "expired" : ttl + "s"));
            }
        }
        return result;
    }

    public int getCurrentDb() { return currentDb; }

    @Override
    public Object execute(String command) throws Exception {
        try (Jedis j = jedisPool.getResource()) {
            String[] parts = command.trim().split("\\s+");
            String cmdName = parts[0].toUpperCase();
            String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

            ProtocolCommand cmd = () -> parts[0].getBytes();
            Object resp = j.sendCommand(cmd, args);

            // Track DB changes (response may be String or byte[])
            if ("SELECT".equals(cmdName) && args.length > 0) {
                String respStr = resp instanceof String s ? s
                        : resp instanceof byte[] b ? new String(b, java.nio.charset.StandardCharsets.UTF_8) : null;
                if ("OK".equalsIgnoreCase(respStr)) {
                    try { currentDb = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
                }
            }

            return formatResponse(resp);
        }
    }

    private String formatResponse(Object resp) {
        if (resp == null) return "(nil)";
        if (resp instanceof String s) return s;
        if (resp instanceof Long || resp instanceof Integer || resp instanceof Double) return resp.toString();
        if (resp instanceof byte[] bytes) return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (resp instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatResponseItem(i, list.get(i))).append("\n");
            }
            if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.toString();
        }
        if (resp instanceof Map<?,?> map) {
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> sb.append(formatResponse(k)).append(" = ").append(formatResponse(v)).append("\n"));
            if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.toString();
        }
        if (resp instanceof Set<?> set) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Object item : set) sb.append("[").append(i++).append("] ").append(formatResponse(item)).append("\n");
            if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
            return sb.toString();
        }
        return resp.toString();
    }

    private String formatResponseItem(int index, Object item) {
        if (item instanceof byte[] bytes) {
            return "[" + index + "] " + new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (item instanceof List<?> nested) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(index).append("]");
            for (Object sub : nested) {
                sb.append("\n    ").append(formatResponse(sub));
            }
            return sb.toString();
        }
        if (item instanceof Map<?,?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(index).append("]");
            map.forEach((k, v) -> sb.append("\n    ").append(formatResponse(k)).append(" = ").append(formatResponse(v)));
            return sb.toString();
        }
        return "[" + index + "] " + formatResponse(item);
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public String getTypeName() { return "Redis"; }
}
