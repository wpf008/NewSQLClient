package com.newsqlclient.core.connector;

import java.util.List;
import java.util.Map;

public interface Connector {

    void connect(com.newsqlclient.core.ConnectionInfo info) throws Exception;

    void disconnect() throws Exception;

    boolean testConnection(com.newsqlclient.core.ConnectionInfo info);

    List<String> browse(String path) throws Exception;

    Object execute(String command) throws Exception;

    boolean isConnected();

    String getTypeName();
}
