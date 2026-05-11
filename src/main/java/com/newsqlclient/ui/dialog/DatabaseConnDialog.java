package com.newsqlclient.ui.dialog;

import com.newsqlclient.core.ConnectionInfo;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import java.awt.*;

public class DatabaseConnDialog extends JDialog {

    private boolean confirmed;
    private JTextField nameField;
    private JComboBox<String> dbTypeCombo;
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passwordField;
    private JTextField dbNameField;
    private JTextField urlField;
    private JButton testBtn;

    private static final String[] DB_TYPES = {"mysql", "postgresql"};

    public DatabaseConnDialog(JFrame parent) {
        super(parent, "添加数据库连接", true);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());

        nameField = new JTextField(20);
        dbTypeCombo = new JComboBox<>(DB_TYPES);
        hostField = new JTextField("127.0.0.1");
        portField = new JTextField("3306");
        userField = new JTextField("root");
        passwordField = new JPasswordField();
        dbNameField = new JTextField();
        urlField = new JTextField();

        dbTypeCombo.addActionListener(e -> {
            String type = (String) dbTypeCombo.getSelectedItem();
            switch (type) {
                case "mysql" -> { hostField.setText("127.0.0.1"); portField.setText("3306"); userField.setText("root"); }
                case "postgresql" -> { hostField.setText("127.0.0.1"); portField.setText("5432"); userField.setText("postgres"); }
                case "sqlite" -> { hostField.setText(""); portField.setText(""); userField.setText(""); hostField.setEnabled(false); portField.setEnabled(false); userField.setEnabled(false); passwordField.setEnabled(false); }
            }
            if (!"sqlite".equals(type)) {
                hostField.setEnabled(true); portField.setEnabled(true); userField.setEnabled(true); passwordField.setEnabled(true);
            }
        });

        int y = 0;
        panel.add(new JLabel("连接名称:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(nameField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("数据库类型:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(dbTypeCombo, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("主机:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(hostField, SwingUtils.gbc(1, y, 1, 1, 0.7, 0));
        JPanel portPanel = new JPanel(new BorderLayout(4, 0));
        portPanel.add(new JLabel("端口:"), BorderLayout.WEST);
        portPanel.add(portField, BorderLayout.CENTER);
        panel.add(portPanel, SwingUtils.gbc(2, y++, 1, 1, 0.3, 0));
        panel.add(new JLabel("用户名:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(userField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("密码:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(passwordField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("数据库名:"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(dbNameField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));
        panel.add(new JLabel("JDBC URL (覆盖默认):"), SwingUtils.gbc(0, y, 1, 1, 0, 0));
        panel.add(urlField, SwingUtils.gbc(1, y++, 2, 1, 1, 0));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        testBtn = new JButton("测试连接");
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
        boolean ok = new com.newsqlclient.core.connector.DatabaseConnector().testConnection(info);
        if (ok) {
            SwingUtils.showInfo(this, "测试结果", "连接成功!");
        } else {
            SwingUtils.showError(this, "测试结果", "连接失败，请检查配置");
        }
    }

    private ConnectionInfo buildInfo() {
        ConnectionInfo info = new ConnectionInfo();
        info.setName(nameField.getText());
        info.setDbType((String) dbTypeCombo.getSelectedItem());
        info.setHost(hostField.getText());
        try { info.setPort(Integer.parseInt(portField.getText())); } catch (NumberFormatException e) { info.setPort(0); }
        info.setUser(userField.getText());
        info.setPassword(new String(passwordField.getPassword()));
        info.setDatabase(dbNameField.getText());
        info.setExtra(urlField.getText());
        return info;
    }

    public boolean isConfirmed() { return confirmed; }
    public ConnectionInfo getConnectionInfo() { return buildInfo(); }
}
