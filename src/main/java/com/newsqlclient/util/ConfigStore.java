package com.newsqlclient.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.newsqlclient.core.ConnectionInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigStore {

    private static final File CONFIG_DIR = new File(System.getProperty("user.home"), ".newsqlclient");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "connections.json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void save(List<ConnectionInfo> connections) {
        try {
            if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
            List<Map<String, Object>> list = new ArrayList<>();
            for (ConnectionInfo info : connections) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", info.getId());
                map.put("name", info.getName());
                map.put("connType", info.getConnType().name());
                map.put("host", info.getHost());
                map.put("port", info.getPort());
                map.put("user", info.getUser());
                map.put("password", encode(info.getPassword()));
                map.put("database", info.getDatabase());
                map.put("dbType", info.getDbType());
                map.put("extra", info.getExtra());
                list.add(map);
            }
            MAPPER.writeValue(CONFIG_FILE, list);
        } catch (IOException e) {
            System.err.println("Failed to save connections: " + e.getMessage());
        }
    }

    public static List<ConnectionInfo> load() {
        if (!CONFIG_FILE.exists()) return new ArrayList<>();
        try {
            List<Map<String, Object>> list = MAPPER.readValue(CONFIG_FILE,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<ConnectionInfo> result = new ArrayList<>();
            for (Map<String, Object> map : list) {
                ConnectionInfo info = new ConnectionInfo();
                info.setId((String) map.get("id"));
                info.setName((String) map.get("name"));
                info.setConnType(ConnectionInfo.ConnType.valueOf((String) map.get("connType")));
                info.setHost((String) map.get("host"));
                info.setPort(map.get("port") != null ? ((Number) map.get("port")).intValue() : 0);
                info.setUser((String) map.get("user"));
                info.setPassword(decode((String) map.get("password")));
                info.setDatabase((String) map.get("database"));
                info.setDbType((String) map.get("dbType"));
                info.setExtra((String) map.get("extra"));
                result.add(info);
            }
            return result;
        } catch (Exception e) {
            System.err.println("Failed to load connections: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String encode(String s) {
        if (s == null || s.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    private static String decode(String s) {
        if (s == null || s.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(s));
    }
}
