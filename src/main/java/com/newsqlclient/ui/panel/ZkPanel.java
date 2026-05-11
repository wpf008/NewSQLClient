package com.newsqlclient.ui.panel;

import com.newsqlclient.core.ConnectionManager;
import com.newsqlclient.core.connector.ZookeeperConnector;
import com.newsqlclient.ui.StatusBar;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

public class ZkPanel extends JPanel {

    private final ConnectionManager connectionManager;
    private final String connectionId;
    private final StatusBar statusBar;
    private final JTree nodeTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode treeRoot;
    private final JTextArea dataArea;
    private final JLabel statLabel;

    public ZkPanel(ConnectionManager connectionManager, String connectionId, StatusBar statusBar) {
        super(new BorderLayout());
        this.connectionManager = connectionManager;
        this.connectionId = connectionId;
        this.statusBar = statusBar;

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // Left: ZK node tree
        treeRoot = new DefaultMutableTreeNode("/");
        treeModel = new DefaultTreeModel(treeRoot);
        nodeTree = new JTree(treeModel);
        nodeTree.setRootVisible(true);
        nodeTree.addTreeSelectionListener(e -> {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                onNodeSelected(path);
            }
        });

        splitPane.setLeftComponent(new JScrollPane(nodeTree));

        // Right: node data + stat
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel infoPanel = new JPanel(new BorderLayout());
        statLabel = new JLabel(" ");
        statLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        infoPanel.add(statLabel, BorderLayout.NORTH);

        dataArea = new JTextArea();
        dataArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        dataArea.setEditable(false);
        infoPanel.add(new JScrollPane(dataArea), BorderLayout.CENTER);

        // Command buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("刷新子节点");
        refreshBtn.addActionListener(e -> refreshCurrent());
        btnPanel.add(refreshBtn);
        JButton deleteBtn = new JButton("删除节点");
        deleteBtn.addActionListener(e -> deleteCurrent());
        btnPanel.add(deleteBtn);
        infoPanel.add(btnPanel, BorderLayout.SOUTH);

        rightPanel.add(infoPanel, BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(300);

        add(splitPane, BorderLayout.CENTER);

        // Initial load
        loadZkChildren(treeRoot);
    }

    private String buildPath(DefaultMutableTreeNode node) {
        if (node == treeRoot) return "/";
        StringBuilder sb = new StringBuilder();
        for (Object o : node.getPath()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) o;
            if (n == treeRoot) continue;
            sb.append("/").append(n.getUserObject());
        }
        return sb.toString();
    }

    private void loadZkChildren(DefaultMutableTreeNode parentNode) {
        parentNode.removeAllChildren();
        String path = buildPath(parentNode);

        ZookeeperConnector conn = (ZookeeperConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        statusBar.setStatus("Loading ZK path: " + path);
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return conn.browse(path);
            }
            @Override
            protected void done() {
                try {
                    List<String> children = get();
                    for (String child : children) {
                        // Strip trailing "/" from display name, path building handles it
                        String displayName = child.endsWith("/") ? child.substring(0, child.length() - 1) : child;
                        parentNode.add(new DefaultMutableTreeNode(displayName));
                    }
                    if (parentNode.getChildCount() == 0) {
//                        parentNode.add(new DefaultMutableTreeNode("(empty)"));
                    }
                    treeModel.nodeStructureChanged(parentNode);
                    statusBar.setStatus("Ready");
                } catch (Exception e) {
                    parentNode.add(new DefaultMutableTreeNode("Error: " + e.getMessage()));
                    treeModel.nodeStructureChanged(parentNode);
                    statusBar.setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void onNodeSelected(TreePath treePath) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        String path = buildPath(node);

        ZookeeperConnector conn = (ZookeeperConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        statusBar.setStatus("Loading: " + path);
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                // Always fetch data, stat, and children
                String data = (String) conn.execute("get " + path);
                String stat = (String) conn.execute("stat " + path);
                List<String> children = conn.browse(path);
                return new Object[]{data, stat, children};
            }
            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] results = get();
                    String data = (String) results[0];
                    String stat = (String) results[1];
                    List<String> children = (List<String>) results[2];

                    dataArea.setText(data != null ? data : "(null)");
                    statLabel.setText(stat != null ? stat : " ");

                    if (!children.isEmpty()) {
                        node.removeAllChildren();
                        for (String child : children) {
                            String name = child.endsWith("/") ? child.substring(0, child.length() - 1) : child;
                            node.add(new DefaultMutableTreeNode(name));
                        }
                        treeModel.nodeStructureChanged(node);
                    }
                    statusBar.setStatus(path + (children.isEmpty() ? "" : " (" + children.size() + " children)"));
                } catch (Exception e) {
                    dataArea.setText("Error: " + e.getMessage());
                    statusBar.setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void refreshCurrent() {
        TreePath path = nodeTree.getSelectionPath();
        if (path != null) {
            loadZkChildren((DefaultMutableTreeNode) path.getLastPathComponent());
        }
    }

    private void deleteCurrent() {
        TreePath path = nodeTree.getSelectionPath();
        if (path == null || path.getPathCount() <= 1) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String zkPath = buildPath(node);

        if (!SwingUtils.confirm(this, "确认删除", "确定要删除节点 '" + zkPath + "' 及其所有子节点吗?")) return;

        ZookeeperConnector conn = (ZookeeperConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                conn.execute("delete " + zkPath);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                    node.removeFromParent();
                    treeModel.nodeStructureChanged(parent);
                    dataArea.setText("");
                    statusBar.setStatus("Deleted: " + zkPath);
                } catch (Exception e) {
                    SwingUtils.showError(ZkPanel.this, "错误", e.getMessage());
                }
            }
        }.execute();
    }
}
