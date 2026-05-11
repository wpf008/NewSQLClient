package com.newsqlclient.ui.panel;

import com.newsqlclient.core.ConnectionManager;
import com.newsqlclient.core.connector.RedisConnector;
import com.newsqlclient.ui.StatusBar;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RedisPanel extends JPanel {

    private final ConnectionManager connectionManager;
    private final String connectionId;
    private final StatusBar statusBar;
    private final JTextField inputField;
    private final JTextArea valueArea;
    private final JLabel dbLabel;

    public RedisPanel(ConnectionManager connectionManager, String connectionId, StatusBar statusBar) {
        super(new BorderLayout());
        this.connectionManager = connectionManager;
        this.connectionId = connectionId;
        this.statusBar = statusBar;

        // Top bar: DB index + input
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        dbLabel = new JLabel("DB: 0");
        dbLabel.setPreferredSize(new Dimension(55, 28));
        dbLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dbLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        inputField = new JTextField();
        inputField.addActionListener(e -> executeInput());
        JButton sendBtn = new JButton("发送");
        sendBtn.addActionListener(e -> executeInput());

        topPanel.add(dbLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1,
                GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new Insets(0, 0, 0, 4), 0, 0));
        topPanel.add(inputField, new GridBagConstraints(1, 0, 1, 1, 1, 1,
                GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        topPanel.add(sendBtn, new GridBagConstraints(2, 0, 1, 1, 0, 1,
                GridBagConstraints.EAST, GridBagConstraints.VERTICAL, new Insets(0, 4, 0, 0), 0, 0));

        add(topPanel, BorderLayout.NORTH);

        // Center: value display
        valueArea = new JTextArea();
        valueArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        valueArea.setEditable(false);
        add(new JScrollPane(valueArea), BorderLayout.CENTER);

        // Load current DB index
        refreshDbInfo();
    }

    private void executeInput() {
        String input = inputField.getText().trim();
        if (input.isEmpty()) return;

        if (input.contains(" ")) {
            executeCmd(input);
        } else {
            loadKey(input);
        }
    }

    private void loadKey(String key) {
        RedisConnector conn = (RedisConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        statusBar.setStatus("Loading key: " + key);
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return conn.browse(key);
            }
            @Override
            protected void done() {
                try {
                    List<String> data = get();
                    StringBuilder sb = new StringBuilder();
                    for (String line : data) {
                        if (line.startsWith("type:")) sb.append("[").append(line).append("]\n");
                        else if (line.startsWith("ttl:")) sb.append("[").append(line).append("]\n");
                        else sb.append(line).append("\n");
                    }
                    valueArea.setText(sb.toString());
                    statusBar.setStatus("Ready");
                } catch (Exception e) {
                    SwingUtils.showError(RedisPanel.this, "错误", e.getMessage());
                    statusBar.setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void executeCmd(String cmd) {
        if (cmd.isBlank()) return;
        RedisConnector conn = (RedisConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        statusBar.setStatus("Executing: " + cmd);
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return conn.execute(cmd);
            }
            @Override
            protected void done() {
                try {
                    Object result = get();
                    valueArea.setText(result != null ? result.toString() : "(nil)");

                    String cmdUpper = cmd.trim().toUpperCase();
                    String[] parts = cmdUpper.split("\\s+");

                    // SELECT → refresh DB index
                    if (parts[0].equals("SELECT")) {
                        refreshDbInfo();
                    }

                    statusBar.setStatus("Ready");
                } catch (Exception e) {
                    valueArea.setText("Error: " + e.getMessage());
                    statusBar.setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void refreshDbInfo() {
        RedisConnector conn = (RedisConnector) connectionManager.getConnector(connectionId);
        if (conn != null) {
            dbLabel.setText("DB: " + conn.getCurrentDb());
        }
    }

    public void switchToDb(String dbName) {
        // dbName like "db3" → execute SELECT 3
        if (dbName == null || !dbName.startsWith("db")) return;
        try {
            int idx = Integer.parseInt(dbName.substring(2));
            inputField.setText("SELECT " + idx);
            executeInput();
            dbLabel.setText("DB: " + idx);
        } catch (NumberFormatException ignored) {}
    }
}
