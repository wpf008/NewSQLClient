package com.newsqlclient.ui;

import com.newsqlclient.core.ConnectionInfo;
import com.newsqlclient.core.ConnectionManager;
import com.newsqlclient.core.connector.Connector;
import com.newsqlclient.model.TreeNode;
import com.newsqlclient.ui.dialog.CreateTableDialog;
import com.newsqlclient.ui.panel.DatabasePanel;
import com.newsqlclient.ui.panel.RedisPanel;
import com.newsqlclient.ui.panel.ZkPanel;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ConnectionTree extends JTree {

    private final DefaultTreeModel treeModel;
    private final TreeNode rootNode;
    private final ConnectionManager connectionManager;
    private final TabbedWorkArea workArea;
    private final StatusBar statusBar;
    private final JPopupMenu popupMenu;
    private Runnable onChangeCallback;

    public ConnectionTree(ConnectionManager connectionManager, TabbedWorkArea workArea, StatusBar statusBar) {
        super(new DefaultTreeModel(null));
        this.connectionManager = connectionManager;
        this.workArea = workArea;
        this.statusBar = statusBar;
        this.rootNode = new TreeNode("Connections", TreeNode.NodeType.ROOT, null, null, null, false);
        this.treeModel = new DefaultTreeModel(rootNode);
        this.popupMenu = new JPopupMenu();
        setModel(treeModel);
        setRootVisible(false);
        setShowsRootHandles(true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new ConnTreeCellRenderer());

        addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                TreeNode node = (TreeNode) event.getPath().getLastPathComponent();
                if (node.isLoaded() || node.getNodeType() == TreeNode.NodeType.ROOT) return;

                // If connection node is disconnected, auto-reconnect first
                if (node.getNodeType() == TreeNode.NodeType.CONNECTION
                        && !connectionManager.isConnected(node.getConnectionId())) {
                    autoReconnectThenLoad(node);
                    return;
                }
                loadChildren(node);
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        // Single-click on DATABASE / REDIS_DB node auto-sets context (only if panel already open)
        addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                TreeNode node = (TreeNode) path.getLastPathComponent();
                ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
                if (info != null) {
                    Component c = workArea.findPanel(info.getName());
                    if (node.getNodeType() == TreeNode.NodeType.DATABASE && c instanceof DatabasePanel dp) {
                        dp.setActiveDatabase(node.toString());
                        statusBar.setStatus("当前数据库: " + node);
                    } else if (node.getNodeType() == TreeNode.NodeType.REDIS_DB && c instanceof RedisPanel rp) {
                        rp.switchToDb(node.toString());
                        statusBar.setStatus("当前DB: " + node);
                    }
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                setSelectionPath(path);

                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    onDoubleClick((TreeNode) path.getLastPathComponent());
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showPopup(e, (TreeNode) path.getLastPathComponent());
                }
            }
        });
    }

    public void addConnectionNode(ConnectionInfo info) {
        boolean isConn = connectionManager.isConnected(info.getId());
        String rootPath = info.getConnType() == ConnectionInfo.ConnType.ZOOKEEPER ? "/" : "";
        TreeNode connNode = new TreeNode(info.getName(), TreeNode.NodeType.CONNECTION,
                info.getId(), rootPath, info.getConnType(), isConn);
        treeModel.insertNodeInto(connNode, rootNode, rootNode.getChildCount());
        expandPath(new TreePath(rootNode));
        if (isConn) {
            expandPath(new TreePath(connNode.getPath()));
        }
    }

    public void markDisconnected(String id) {
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            TreeNode child = (TreeNode) rootNode.getChildAt(i);
            if (id.equals(child.getConnectionId())) {
                child.removeAllChildren();
                child.add(new DefaultMutableTreeNode(""));
                child.setLoaded(false);
                child.setConnected(false);
                treeModel.nodeStructureChanged(child);
                return;
            }
        }
    }

    public void removeConnectionNode(String id) {
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            TreeNode child = (TreeNode) rootNode.getChildAt(i);
            if (id.equals(child.getConnectionId())) {
                treeModel.removeNodeFromParent(child);
                return;
            }
        }
    }

    public void setOnChangeCallback(Runnable callback) { this.onChangeCallback = callback; }

    private void notifyChange() {
        if (onChangeCallback != null) onChangeCallback.run();
    }

    public TreeNode getRootNode() { return rootNode; }

    // ---- Popup Menu ----

    private void showPopup(MouseEvent e, TreeNode node) {
        popupMenu.removeAll();
        ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
        if (info == null) return;

        switch (node.getNodeType()) {
            case CONNECTION -> {
                if (connectionManager.isConnected(node.getConnectionId())) {
                    JMenuItem refreshItem = new JMenuItem("刷新");
                    refreshItem.addActionListener(ev -> refreshNode(node));
                    popupMenu.add(refreshItem);

                    if (info.getConnType() == ConnectionInfo.ConnType.DATABASE) {
                        JMenuItem createDbItem = new JMenuItem("新建数据库");
                        createDbItem.addActionListener(ev -> createDatabase(node));
                        popupMenu.add(createDbItem);
                    }

                    popupMenu.addSeparator();
                    JMenuItem disconnectItem = new JMenuItem("断开连接");
                    disconnectItem.addActionListener(ev -> disconnectConnection(node));
                    popupMenu.add(disconnectItem);
                } else {
                    JMenuItem reconnectItem = new JMenuItem("重新连接");
                    reconnectItem.addActionListener(ev -> reconnectConnection(node));
                    popupMenu.add(reconnectItem);

                    JMenuItem removeItem = new JMenuItem("移除");
                    removeItem.addActionListener(ev -> removeConnectionPermanently(node));
                    popupMenu.add(removeItem);
                }
            }
            case DATABASE -> {
                JMenuItem refreshItem = new JMenuItem("刷新");
                refreshItem.addActionListener(ev -> refreshNode(node));
                popupMenu.add(refreshItem);

                JMenuItem useDbItem = new JMenuItem("设为当前数据库");
                useDbItem.addActionListener(ev -> setActiveDatabase(node));
                popupMenu.add(useDbItem);

                popupMenu.addSeparator();
                JMenuItem createTableItem = new JMenuItem("新建表");
                createTableItem.addActionListener(ev -> createTable(node));
                popupMenu.add(createTableItem);
            }
            case REDIS_DB -> {
                JMenuItem refreshItem = new JMenuItem("刷新");
                refreshItem.addActionListener(ev -> refreshNode(node));
                popupMenu.add(refreshItem);

                JMenuItem switchItem = new JMenuItem("切换到此数据库");
                switchItem.addActionListener(ev -> switchRedisDb(node));
                popupMenu.add(switchItem);
            }
            case TABLE -> {
                JMenuItem refreshItem = new JMenuItem("刷新");
                refreshItem.addActionListener(ev -> refreshNode(node));
                popupMenu.add(refreshItem);
                popupMenu.addSeparator();

                JMenuItem viewDataItem = new JMenuItem("查看数据 (前100行)");
                viewDataItem.addActionListener(ev -> viewTableData(node));
                popupMenu.add(viewDataItem);

                JMenuItem viewStructItem = new JMenuItem("查看表结构");
                viewStructItem.addActionListener(ev -> viewTableStructure(node));
                popupMenu.add(viewStructItem);

                JMenuItem countItem = new JMenuItem("查看记录数");
                countItem.addActionListener(ev -> countTableRows(node));
                popupMenu.add(countItem);

                popupMenu.addSeparator();
                JMenuItem dropItem = new JMenuItem("删除表");
                dropItem.addActionListener(ev -> dropTable(node));
                popupMenu.add(dropItem);
            }
        }
        if (popupMenu.getComponentCount() > 0) {
            popupMenu.show(this, e.getX(), e.getY());
        }
    }

    // ---- Node Actions ----

    private void autoReconnectThenLoad(TreeNode node) {
        String connId = node.getConnectionId();
        statusBar.setStatus("正在重新连接 " + node + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                connectionManager.reconnect(connId);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    node.setConnected(true);
                    node.setLoaded(false);
                    loadChildren(node);
                    treeModel.nodeStructureChanged(node);
                    statusBar.setStatus("已重新连接: " + node);
                    notifyChange();
                } catch (Exception e) {
                    node.removeAllChildren();
                    node.add(new DefaultMutableTreeNode("(连接失败)"));
                    node.setLoaded(true);
                    treeModel.nodeStructureChanged(node);
                    statusBar.setStatus("重新连接失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void refreshNode(TreeNode node) {
        node.setLoaded(false);
        loadChildren(node);
    }

    private void disconnectConnection(TreeNode node) {
        String connId = node.getConnectionId();
        if (!SwingUtils.confirm(getParentWindow(), "断开连接", "确定要断开 '" + node + "' 吗？")) return;
        try {
            String title = connectionManager.getInfo(connId).getName();
            connectionManager.disconnect(connId);
            // Collapse node and mark as disconnected
            collapsePath(new TreePath(node.getPath()));
            node.removeAllChildren();
            node.add(new DefaultMutableTreeNode(""));
            node.setLoaded(false);
            node.setConnected(false);
            treeModel.nodeStructureChanged(node);
            workArea.closePanel(title);
            statusBar.setStatus("已断开: " + node);
            notifyChange();
        } catch (Exception ex) {
            SwingUtils.showError(getParentWindow(), "错误", ex.getMessage());
        }
    }

    private void reconnectConnection(TreeNode node) {
        String connId = node.getConnectionId();
        statusBar.setStatus("正在重新连接 " + node + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                connectionManager.reconnect(connId);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    node.setConnected(true);
                    node.setLoaded(false);
                    loadChildren(node);
                    expandPath(new TreePath(node.getPath()));
                    statusBar.setStatus("已重新连接: " + node);
                    notifyChange();
                } catch (Exception e) {
                    SwingUtils.showError(getParentWindow(), "重新连接失败", e.getMessage());
                    statusBar.setStatus("重新连接失败");
                }
            }
        }.execute();
    }

    private void removeConnectionPermanently(TreeNode node) {
        String connId = node.getConnectionId();
        if (!SwingUtils.confirm(getParentWindow(), "移除连接", "确定要永久移除 '" + node + "' 吗？")) return;
        try {
            String title = connectionManager.getInfo(connId) != null
                    ? connectionManager.getInfo(connId).getName() : null;
            connectionManager.removeConnection(connId);
            removeConnectionNode(connId);
            if (title != null) workArea.closePanel(title);
            statusBar.setStatus("已移除: " + node);
            notifyChange();
        } catch (Exception ex) {
            SwingUtils.showError(getParentWindow(), "错误", ex.getMessage());
        }
    }

    private void switchRedisDb(TreeNode node) {
        ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
        if (info == null) return;
        Component c = workArea.findPanel(info.getName());
        if (c instanceof RedisPanel rp) {
            rp.switchToDb(node.toString());
            statusBar.setStatus("已切换到 " + node);
        }
    }

    private void setActiveDatabase(TreeNode node) {
        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            panel.setActiveDatabase(node.toString());
            statusBar.setStatus("当前数据库: " + node);
        }
    }

    private String qualifiedTable(TreeNode node) {
        String dbName = getParentDbName(node);
        String tableName = extractTableName(node.toString());
        if (dbName != null && !dbName.isEmpty()) {
            return dbName + "." + tableName;
        }
        return tableName;
    }

    private void viewTableData(TreeNode node) {
        String qt = qualifiedTable(node);
        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            String dbName = getParentDbName(node);
            if (dbName != null) panel.setActiveDatabase(dbName);
            panel.runQuery("SELECT * FROM " + qt + " LIMIT 100");
        }
    }

    private void viewTableStructure(TreeNode node)  {
        String dbName = getParentDbName(node);
        String qt = qualifiedTable(node);
        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            if (dbName != null) panel.setActiveDatabase(dbName);
            ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
            String sql;
            if (info != null && "postgresql".equals(info.getDbType())) {
                String schema = dbName != null ? dbName : "public";
                String tableName = qt.contains(".") ? qt.substring(qt.indexOf(".") + 1) : qt;
                sql = "SELECT column_name AS \"Field\", data_type AS \"Type\", "
                        + "COALESCE(character_maximum_length, 0) AS \"Length\", "
                        + "CASE WHEN is_nullable = 'YES' THEN 'YES' ELSE 'NO' END AS \"Null\", "
                        + "CASE WHEN column_default LIKE 'nextval%' THEN 'AUTO' ELSE '' END AS \"Extra\" "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = '" + schema + "' AND table_name = '" + tableName + "' "
                        + "ORDER BY ordinal_position";
                panel.runQueryWithNoSetText(sql);
            } else {
                sql = "DESCRIBE " + qt;
                panel.runQuery(sql);
            }

        }
    }

    private void countTableRows(TreeNode node) {
        String dbName = getParentDbName(node);
        String qt = qualifiedTable(node);
        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            if (dbName != null) panel.setActiveDatabase(dbName);
            panel.runQuery("SELECT COUNT(*) FROM " + qt);
        }
    }

    private void createDatabase(TreeNode node) {
        ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
        boolean isPG = info != null && "postgresql".equals(info.getDbType());
        String label = isPG ? "请输入模式名称:" : "请输入数据库名称:";
        String title = isPG ? "新建模式" : "新建数据库";
        String name = JOptionPane.showInputDialog(getParentWindow(), label, title,
                JOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        final String dbName = name.trim();
        String sql = isPG ? "CREATE SCHEMA " + dbName : "CREATE DATABASE " + dbName;

        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            panel.runQuery(sql, () -> {
                refreshNode(node);
                statusBar.setStatus((isPG ? "模式 " : "数据库 ") + dbName + " 创建成功");
            });
        }
    }

    private void createTable(TreeNode node) {
        String dbName = node.toString();
        ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
        String dbType = info != null ? info.getDbType() : "mysql";
        CreateTableDialog dialog = new CreateTableDialog(getParentWindow(), dbName, dbType);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            String sql = dialog.getCreateSQL(dbName);
            DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
            if (panel != null) {
                panel.setActiveDatabase(dbName);
                panel.runQuery(sql, () -> {
                    refreshNode(node);
                    statusBar.setStatus("表创建成功");
                });
            }
        }
    }

    private void dropTable(TreeNode node) {
        String tableName = extractTableName(node.toString());
        String qt = qualifiedTable(node);
        if (!SwingUtils.confirm(getParentWindow(), "删除表", "确定要删除表 '" + qt + "' 吗？\n此操作不可撤销！")) return;

        String dbName = getParentDbName(node);
        DatabasePanel panel = getOrOpenDbPanel(node.getConnectionId());
        if (panel != null) {
            if (dbName != null) panel.setActiveDatabase(dbName);
            panel.runQuery("DROP TABLE " + qt);
        }
    }

    private DatabasePanel getOrOpenDbPanel(String connId) {
        ConnectionInfo info = connectionManager.getInfo(connId);
        if (info == null) return null;

        String title = info.getName();
        Component c = workArea.findPanel(title);
        if (c instanceof DatabasePanel dp) {
            workArea.selectPanel(title);
            return dp;
        }
        DatabasePanel dp = new DatabasePanel(connectionManager, connId, statusBar);
        workArea.openPanel(title, dp, null);
        return dp;
    }

    private String getParentDbName(TreeNode node) {
        // Walk up the tree to find the DATABASE node
        TreeNode parent = (TreeNode) node.getParent();
        if (parent != null && parent.getNodeType() == TreeNode.NodeType.DATABASE) {
            return parent.toString();
        }
        return null;
    }

    private Window getParentWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private String extractTableName(String displayName) {
        return displayName.trim();
    }

    // ---- Path & Loading ----

    private String buildChildPath(TreeNode parent, String childName) {
        ConnectionInfo info = connectionManager.getInfo(parent.getConnectionId());
        if (info == null) return childName;

        return switch (info.getConnType()) {
            case DATABASE -> {
                String parentPath = parent.getNodePath();
                if (parentPath == null || parentPath.isEmpty()) yield childName;
                if (!parentPath.contains(".")) yield parentPath + "." + childName;
                yield parentPath + "." + childName;
            }
            case REDIS -> childName;
            case ZOOKEEPER -> {
                String clean = childName.endsWith("/") ? childName.substring(0, childName.length() - 1) : childName;
                String parentPath = parent.getNodePath();
                if (parentPath.endsWith("/")) yield parentPath + clean;
                yield parentPath + "/" + clean;
            }
        };
    }

    private void loadChildren(TreeNode node) {
        // Prevent duplicate loads from rapid clicks
        if (node.isLoading()) return;
        node.setLoading(true);
        node.removeAllChildren();
        node.setLoaded(false);

        // REDIS_DB nodes don't auto-load keys (avoid KEYS * on large DBs)
        // ZK CONNECTION nodes don't show children in tree (use panel instead)
        if (node.getNodeType() == TreeNode.NodeType.REDIS_DB
                || (node.getNodeType() == TreeNode.NodeType.CONNECTION && node.getConnType() == ConnectionInfo.ConnType.ZOOKEEPER)) {
            node.add(new DefaultMutableTreeNode(""));
            node.setLoaded(true);
            node.setLoading(false);
            treeModel.nodeStructureChanged(node);
            return;
        }

        statusBar.setStatus("Loading " + node + "...");

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Connector conn = connectionManager.getConnector(node.getConnectionId());
                if (conn == null) throw new Exception("Not connected");
                return conn.browse(node.getNodePath());
            }
            @Override
            protected void done() {
                try {
                    List<String> children = get();
                    ConnectionInfo info = connectionManager.getInfo(node.getConnectionId());
                    for (String child : children) {
                        TreeNode.NodeType childType = switch (node.getNodeType()) {
                            case CONNECTION -> switch (info.getConnType()) {
                                case DATABASE -> TreeNode.NodeType.DATABASE;
                                case REDIS -> TreeNode.NodeType.REDIS_DB;
                                case ZOOKEEPER -> TreeNode.NodeType.ZK_NODE;
                            };
                            case DATABASE -> TreeNode.NodeType.TABLE;
                            case ZK_NODE -> TreeNode.NodeType.ZK_NODE;
                            default -> null;
                        };
                        if (childType == null) continue;
                        String childPath = buildChildPath(node, child);
                        node.add(new TreeNode(child, childType, node.getConnectionId(), childPath,
                                info.getConnType(), true));
                    }
                    if (node.getChildCount() == 0) {
                        node.add(new DefaultMutableTreeNode("(empty)"));
                    }
                    node.setLoaded(true);
                    node.setLoading(false);
                    treeModel.nodeStructureChanged(node);
                    expandPath(new TreePath(node.getPath()));
                    statusBar.setStatus("Ready");
                } catch (Exception e) {
                    node.add(new DefaultMutableTreeNode("Error: " + e.getMessage()));
                    node.setLoaded(true);
                    node.setLoading(false);
                    treeModel.nodeStructureChanged(node);
                    statusBar.setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void onDoubleClick(TreeNode node) {
        String connId = node.getConnectionId();
        if (connId == null) return;

        ConnectionInfo info = connectionManager.getInfo(connId);
        if (info == null) return;

        // If disconnected, reconnect first
        if (node.getNodeType() == TreeNode.NodeType.CONNECTION && !connectionManager.isConnected(connId)) {
            statusBar.setStatus("正在重新连接 " + node + "...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    connectionManager.reconnect(connId);
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        node.setConnected(true);
                        node.setLoaded(false);
                        loadChildren(node);
                        expandPath(new TreePath(node.getPath()));
                        statusBar.setStatus("已重新连接: " + node);
                        notifyChange();
                        openPanelForNode(node, info);
                    } catch (Exception e) {
                        SwingUtils.showError(getParentWindow(), "重新连接失败", e.getMessage());
                        statusBar.setStatus("重新连接失败");
                    }
                }
            }.execute();
            return;
        }

        openPanelForNode(node, info);
    }

    private void openPanelForNode(TreeNode node, ConnectionInfo info) {
        String title = info.getName();
        JPanel panel = switch (info.getConnType()) {
            case DATABASE -> {
                DatabasePanel dp = new DatabasePanel(connectionManager, node.getConnectionId(), statusBar);
                if (node.getNodeType() == TreeNode.NodeType.DATABASE) {
                    dp.setActiveDatabase(node.toString());
                }
                yield dp;
            }
            case REDIS -> {
                RedisPanel rp = new RedisPanel(connectionManager, node.getConnectionId(), statusBar);
                if (node.getNodeType() == TreeNode.NodeType.REDIS_DB) {
                    String dbName = node.toString(); // e.g., "db3"
                    rp.switchToDb(dbName);
                }
                yield rp;
            }
            case ZOOKEEPER -> new ZkPanel(connectionManager, node.getConnectionId(), statusBar);
        };
        workArea.openPanel(title, panel, null);
    }

    private static class ConnTreeCellRenderer extends DefaultTreeCellRenderer {

        private static final java.util.Map<String, Icon> iconCache = new java.util.HashMap<>();

        private static Icon loadIcon(String name) {
            return iconCache.computeIfAbsent(name, k -> {
                try {
                    var stream = ConnTreeCellRenderer.class.getResourceAsStream("/icons/" + k);
                    if (stream != null) {
                        return new ImageIcon(javax.imageio.ImageIO.read(stream));
                    }
                } catch (Exception ignored) {}
                return null;
            });
        }

        private static Icon connIcon(ConnectionInfo.ConnType type, boolean connected) {
            String prefix = switch (type) {
                case DATABASE -> "mysql";
                case REDIS -> "redis";
                case ZOOKEEPER -> "zookeeper";
            };
            return loadIcon(prefix + (connected ? "-on.png" : "-close.png"));
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                       boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof TreeNode node) {
                if (node.getNodeType() == TreeNode.NodeType.CONNECTION) {
                    Icon icon = connIcon(node.getConnType(), node.isConnected());
                    if (icon != null) setIcon(icon);
                    if (!node.isConnected()) {
                        setForeground(UIManager.getColor("Label.disabledForeground"));
                    }
                } else {
                    String iconKey = switch (node.getNodeType()) {
                        case DATABASE -> "FileView.hardDriveIcon";
                        case TABLE -> "FileChooser.listViewIcon";
                        case REDIS_DB -> "FileView.hardDriveIcon";
                        case REDIS_KEY -> "FileChooser.listViewIcon";
                        case ZK_NODE -> "Tree.closedIcon";
                        default -> null;
                    };
                    if (iconKey != null) {
                        Icon icon = UIManager.getIcon(iconKey);
                        if (icon != null) setIcon(icon);
                    }
                }
            }
            return this;
        }
    }
}
