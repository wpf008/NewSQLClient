package com.newsqlclient.ui;

import com.newsqlclient.core.ConnectionInfo;
import com.newsqlclient.core.ConnectionManager;
import com.newsqlclient.ui.dialog.*;
import com.newsqlclient.util.ConfigStore;
import com.newsqlclient.util.SwingUtils;
import com.newsqlclient.util.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.UUID;

public class MainFrame extends JFrame {

    private final ConnectionManager connectionManager;
    private final ConnectionTree connectionTree;
    private final TabbedWorkArea workArea;
    private final StatusBar statusBar;

    public MainFrame() {
        super("NewSQLClient - 统一中间件客户端");
        connectionManager = new ConnectionManager();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu connMenu = new JMenu("连接");

        JMenuItem addDbItem = new JMenuItem("添加数据库连接...");
        addDbItem.addActionListener(e -> addDatabaseConnection());
        JMenuItem addRedisItem = new JMenuItem("添加Redis连接...");
        addRedisItem.addActionListener(e -> addRedisConnection());
        JMenuItem addZkItem = new JMenuItem("添加ZooKeeper连接...");
        addZkItem.addActionListener(e -> addZkConnection());

        connMenu.add(addDbItem);
        connMenu.add(addRedisItem);
        connMenu.add(addZkItem);
        connMenu.addSeparator();

        JMenuItem removeItem = new JMenuItem("移除选中连接");
        removeItem.addActionListener(e -> removeSelectedConnection());
        connMenu.add(removeItem);

        menuBar.add(connMenu);

        JMenu viewMenu = new JMenu("视图");
        JMenuItem toggleThemeItem = new JMenuItem("切换浅色/深色模式");
        toggleThemeItem.addActionListener(e -> ThemeManager.toggleAndUpdate(this));
        viewMenu.add(toggleThemeItem);
        menuBar.add(viewMenu);

        setJMenuBar(menuBar);

        // Layout
        statusBar = new StatusBar();
        workArea = new TabbedWorkArea();
        connectionTree = new ConnectionTree(connectionManager, workArea, statusBar);
        connectionTree.setOnChangeCallback(this::saveConnections);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(connectionTree), workArea);
        splitPane.setDividerLocation(260);

        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        // Window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });

        // Load saved connections in background after window is shown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                loadSavedConnections();
            }
        });
    }

    private void addDatabaseConnection() {
        DatabaseConnDialog dialog = new DatabaseConnDialog(this);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            ConnectionInfo info = dialog.getConnectionInfo();
            info.setId(UUID.randomUUID().toString());
            info.setConnType(ConnectionInfo.ConnType.DATABASE);
            tryConnect(info);
        }
    }

    private void addRedisConnection() {
        RedisConnDialog dialog = new RedisConnDialog(this);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            ConnectionInfo info = dialog.getConnectionInfo();
            info.setId(UUID.randomUUID().toString());
            info.setConnType(ConnectionInfo.ConnType.REDIS);
            tryConnect(info);
        }
    }

    private void addZkConnection() {
        ZkConnDialog dialog = new ZkConnDialog(this);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            ConnectionInfo info = dialog.getConnectionInfo();
            info.setId(UUID.randomUUID().toString());
            info.setConnType(ConnectionInfo.ConnType.ZOOKEEPER);
            tryConnect(info);
        }
    }

    private void tryConnect(ConnectionInfo info) {
        statusBar.setStatus("Connecting to " + info.getName() + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                connectionManager.addConnection(info);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    connectionTree.addConnectionNode(info);
                    saveConnections();
                    statusBar.setStatus("Connected: " + info.getName());
                } catch (Exception e) {
                    SwingUtils.showError(MainFrame.this, "连接失败",
                            "无法连接到 " + info.getName() + ":\n" + e.getMessage());
                    statusBar.setStatus("Connection failed");
                }
            }
        }.execute();
    }

    private void removeSelectedConnection() {
        var path = connectionTree.getSelectionPath();
        if (path != null) {
            var node = (com.newsqlclient.model.TreeNode) path.getLastPathComponent();
            if (node.getConnectionId() != null && node.getNodeType() == com.newsqlclient.model.TreeNode.NodeType.CONNECTION) {
                boolean ok = SwingUtils.confirm(this, "确认", "确定要移除连接 '" + node + "' 吗?");
                if (ok) {
                    try {
                        String title = connectionManager.getInfo(node.getConnectionId()).getName();
                        connectionManager.removeConnection(node.getConnectionId());
                        connectionTree.removeConnectionNode(node.getConnectionId());
                        workArea.closePanel(title);
                        saveConnections();
                        statusBar.setStatus("Ready");
                    } catch (Exception ex) {
                        SwingUtils.showError(this, "错误", ex.getMessage());
                    }
                }
            }
        }
    }

    private void saveConnections() {
        ConfigStore.save(connectionManager.getAllConnections());
    }

    private void loadSavedConnections() {
        List<ConnectionInfo> saved = ConfigStore.load();
        if (saved.isEmpty()) return;

        for (ConnectionInfo info : saved) {
            connectionManager.rememberConnection(info);
            connectionTree.addConnectionNode(info);
            connectionTree.markDisconnected(info.getId());
        }
        statusBar.setStatus("已加载 " + saved.size() + " 个已保存的连接（点击展开以连接）");
    }

    private void onExit() {
        saveConnections();
        connectionManager.disconnectAll();
        dispose();
        System.exit(0);
    }
}
