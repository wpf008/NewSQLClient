package com.newsqlclient.model;

import com.newsqlclient.core.ConnectionInfo;
import javax.swing.tree.DefaultMutableTreeNode;

public class TreeNode extends DefaultMutableTreeNode {

    public enum NodeType { ROOT, CONNECTION, DATABASE, TABLE, COLUMN, REDIS_DB, REDIS_KEY, ZK_NODE }

    private final NodeType nodeType;
    private final String connectionId;
    private final String path;
    private final ConnectionInfo.ConnType connType;
    private boolean loaded;
    private boolean connected;
    private boolean loading;

    public TreeNode(String name, NodeType nodeType, String connectionId, String path,
                    ConnectionInfo.ConnType connType, boolean connected) {
        super(name);
        this.nodeType = nodeType;
        this.connectionId = connectionId;
        this.path = path;
        this.connType = connType;
        this.connected = connected;
        if (nodeType != NodeType.ROOT) {
            add(new DefaultMutableTreeNode("Loading..."));
        }
    }

    public NodeType getNodeType() { return nodeType; }
    public String getConnectionId() { return connectionId; }
    public String getNodePath() { return path; }
    public ConnectionInfo.ConnType getConnType() { return connType; }
    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public boolean isLoading() { return loading; }
    public void setLoading(boolean loading) { this.loading = loading; }
}
