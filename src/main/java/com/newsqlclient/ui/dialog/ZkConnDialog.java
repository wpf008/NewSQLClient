package com.newsqlclient.ui.dialog;

import com.newsqlclient.core.ConnectionInfo;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import java.awt.*;

public class ZkConnDialog extends JDialog {

    private boolean confirmed;
    private JTextField nameField;
    private JTextField hostField;
    private JTextField portField;
    private JTextField rootPathField;

    public ZkConnDialog(JFrame parent) {
        super(parent, "添加ZooKeeper连接", true);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());

        nameField = new JTextField(20);
        hostField = new JTextField("127.0.0.1");
        portField = new JTextField("2181");
        rootPathField = new JTextField("/");

        int y = 0;
        panel.add(new JLabel("连接名称:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(nameField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("主机:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(hostField, SwingUtils.gbc(1, y, 1, 1, 0.7, 0));
        JPanel portPanel = new JPanel(new BorderLayout(4, 0));
        portPanel.add(new JLabel("端口:"), BorderLayout.WEST);
        portPanel.add(portField, BorderLayout.CENTER);
        panel.add(portPanel, SwingUtils.gbc(2, y++, 1, 1, 0.3, 0));
        panel.add(new JLabel("根路径:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(rootPathField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton testBtn = new JButton("测试连接");
        testBtn.addActionListener(e -> testConnection());
        JButton okBtn = new JButton("确定");
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(testBtn);
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        panel.add(btnPanel, SwingUtils.gbc(0, y, 3, 1, 1, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(panel);
    }

    private void testConnection() {
        ConnectionInfo info = buildInfo();
        boolean ok = new com.newsqlclient.core.connector.ZookeeperConnector().testConnection(info);
        if (ok) {
            SwingUtils.showInfo(this, "测试结果", "连接成功!");
        } else {
            SwingUtils.showError(this, "测试结果", "连接失败，请检查配置");
        }
    }

    private ConnectionInfo buildInfo() {
        ConnectionInfo info = new ConnectionInfo();
        info.setName(nameField.getText());
        info.setHost(hostField.getText());
        try { info.setPort(Integer.parseInt(portField.getText())); } catch (NumberFormatException e) { info.setPort(2181); }
        info.setExtra(rootPathField.getText());
        return info;
    }

    public boolean isConfirmed() { return confirmed; }
    public ConnectionInfo getConnectionInfo() { return buildInfo(); }
}
