package com.newsqlclient.ui.panel;

import com.newsqlclient.core.ConnectionManager;
import com.newsqlclient.core.connector.DatabaseConnector;
import com.newsqlclient.ui.StatusBar;
import com.newsqlclient.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class DatabasePanel extends JPanel {

    private final ConnectionManager connectionManager;
    private final String connectionId;
    private final StatusBar statusBar;
    private final JTextArea sqlEditor;
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JButton runBtn;
    private final JComboBox<String> dbSelector;
    private final JLabel infoLabel;
    private String activeDatabase;

    // Pagination state
    private String lastSelectSql;
    private int currentPage = 1;
    private int pageSize = 100;
    private int totalRows;
    private JPanel pagePanel;
    private JLabel pageLabel;
    private JButton prevBtn, nextBtn, firstBtn, lastBtn;
    private JComboBox<String> pageSizeCombo;

    public DatabasePanel(ConnectionManager connectionManager, String connectionId, StatusBar statusBar) {
        super(new BorderLayout());
        this.connectionManager = connectionManager;
        this.connectionId = connectionId;
        this.statusBar = statusBar;

        var info = connectionManager.getInfo(connectionId);
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        infoLabel = new JLabel(info.getDbType() + " | " + info.getHost() + ":" + info.getPort());
        topPanel.add(infoLabel, BorderLayout.WEST);

        JPanel dbSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dbSelectPanel.add(new JLabel("数据库:"));
        dbSelector = new JComboBox<>();
        dbSelector.setPreferredSize(new Dimension(150, dbSelector.getPreferredSize().height));
        dbSelector.addActionListener(e -> {
            String selected = (String) dbSelector.getSelectedItem();
            if (selected != null && !selected.equals(activeDatabase) && !selected.equals("(刷新列表...)")) {
                switchDatabase(selected);
            }
            if ("(刷新列表...)".equals(selected)) {
                loadDatabaseList();
            }
        });
        dbSelectPanel.add(dbSelector);
        JButton refreshDbBtn = new JButton("↻");
        refreshDbBtn.setToolTipText("刷新数据库列表");
        refreshDbBtn.addActionListener(e -> loadDatabaseList());
        dbSelectPanel.add(refreshDbBtn);

        topPanel.add(dbSelectPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center: SQL editor + results
        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        centerSplit.setResizeWeight(0.35);

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createTitledBorder("SQL Query"));
        sqlEditor = new JTextArea(8, 40);
        sqlEditor.setFont(new Font("Monospaced", Font.PLAIN, 14));
        sqlEditor.setTabSize(2);
        JScrollPane editorScroll = new JScrollPane(sqlEditor);
        editorScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        runBtn = new JButton("执行 (Ctrl+Enter)");
        runBtn.addActionListener(e -> executeQuery());

        sqlEditor.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "execute");
        sqlEditor.getActionMap().put("execute", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { executeQuery(); }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(runBtn);
        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> sqlEditor.setText(""));
        btnPanel.add(clearBtn);

        editorPanel.add(editorScroll, BorderLayout.CENTER);
        editorPanel.add(btnPanel, BorderLayout.SOUTH);

        // Results with pagination
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder("Results"));
        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane resultScroll = new JScrollPane(resultTable);
        resultPanel.add(resultScroll, BorderLayout.CENTER);

        // Pagination bar
        pagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pagePanel.setVisible(false);

        firstBtn = new JButton("|<");
        firstBtn.addActionListener(e -> goToPage(1));
        prevBtn = new JButton("<");
        prevBtn.addActionListener(e -> goToPage(currentPage - 1));

        pageSizeCombo = new JComboBox<>(new String[]{"20", "50", "100", "200", "500", "1000"});
        pageSizeCombo.setSelectedItem("100");
        pageSizeCombo.addActionListener(e -> {
            String sel = (String) pageSizeCombo.getSelectedItem();
            if (sel != null) {
                pageSize = Integer.parseInt(sel);
                currentPage = 1;
                reloadPage();
            }
        });

        nextBtn = new JButton(">");
        nextBtn.addActionListener(e -> goToPage(currentPage + 1));
        lastBtn = new JButton(">|");
        lastBtn.addActionListener(e -> {
            int last = (int) Math.ceil((double) totalRows / pageSize);
            goToPage(last);
        });

        pageLabel = new JLabel("");

        pagePanel.add(new JLabel("每页:"));
        pagePanel.add(pageSizeCombo);
        pagePanel.add(firstBtn);
        pagePanel.add(prevBtn);
        pagePanel.add(nextBtn);
        pagePanel.add(lastBtn);
        pagePanel.add(pageLabel);

        resultPanel.add(pagePanel, BorderLayout.SOUTH);
        centerSplit.setTopComponent(editorPanel);
        centerSplit.setBottomComponent(resultPanel);
        add(centerSplit, BorderLayout.CENTER);

        // Set initial database
        if (info.getDatabase() != null && !info.getDatabase().isEmpty()) {
            DatabaseConnector conn = (DatabaseConnector) connectionManager.getConnector(connectionId);
            if (conn != null) {
                conn.setActiveCatalog(info.getDatabase());
                activeDatabase = info.getDatabase();
            }
        }
        loadDatabaseList();
    }

    private boolean isSelectQuery(String sql) {
        String upper = sql.trim().toUpperCase();
        return upper.startsWith("SELECT") && !upper.contains("LIMIT");
    }

    private void goToPage(int page) {
        if (page < 1 || totalRows == 0) return;
        int maxPage = (int) Math.ceil((double) totalRows / pageSize);
        if (page > maxPage) page = maxPage;
        currentPage = page;
        reloadPage();
    }

    private void reloadPage() {
        if (lastSelectSql == null) return;
        DatabaseConnector conn = (DatabaseConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        int offset = (currentPage - 1) * pageSize;
        statusBar.setStatus("加载第 " + currentPage + " 页...");

        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return conn.executePagedQuery(lastSelectSql, offset, pageSize);
            }
            @Override
            protected void done() {
                try {
                    List<Map<String, Object>> rows = get();
                    displayPagedResult(rows);
                    updatePageControls();
                    statusBar.setStatus("第 " + currentPage + " 页, 共 " + totalRows + " 行");
                } catch (Exception e) {
                    SwingUtils.showError(DatabasePanel.this, "查询失败", e.getMessage());
                    statusBar.setStatus("Query failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updatePageControls() {
        int maxPage = (int) Math.ceil((double) totalRows / pageSize);
        if (maxPage == 0) maxPage = 1;
        pageLabel.setText("第 " + currentPage + "/" + maxPage + " 页 (共 " + totalRows + " 行)");
        prevBtn.setEnabled(currentPage > 1);
        nextBtn.setEnabled(currentPage < maxPage);
        firstBtn.setEnabled(currentPage > 1);
        lastBtn.setEnabled(currentPage < maxPage);
    }

    @SuppressWarnings("unchecked")
    private void displayPagedResult(List<Map<String, Object>> rows) {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        if (rows.isEmpty()) {
            tableModel.addColumn("Message");
            tableModel.addRow(new Object[]{"(empty)"});
            return;
        }
        String[] columns = rows.get(0).keySet().toArray(new String[0]);
        tableModel.setColumnIdentifiers(columns);
        for (Map<String, Object> row : rows) {
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                values[i] = row.get(columns[i]);
            }
            tableModel.addRow(values);
        }
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
    }

    private void loadDatabaseList() {
        DatabaseConnector conn = (DatabaseConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return conn.browse("");
            }
            @Override
            protected void done() {
                try {
                    List<String> dbs = get();
                    dbSelector.removeAllItems();
                    for (String db : dbs) dbSelector.addItem(db);
                    dbSelector.addItem("(刷新列表...)");
                    if (activeDatabase != null) dbSelector.setSelectedItem(activeDatabase);
                    else {
                        var info = connectionManager.getInfo(connectionId);
                        if (info.getDatabase() != null && !info.getDatabase().isEmpty()) {
                            dbSelector.setSelectedItem(info.getDatabase());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    public void setActiveDatabase(String dbName) {
        if (dbName == null || dbName.equals(activeDatabase)) return;
        activeDatabase = dbName;
        dbSelector.setSelectedItem(dbName);
        switchDatabase(dbName);
    }

    private void switchDatabase(String dbName) {
        DatabaseConnector conn = (DatabaseConnector) connectionManager.getConnector(connectionId);
        if (conn == null) return;
        conn.setActiveCatalog(dbName);
        activeDatabase = dbName;
        infoLabel.setText("当前库: " + dbName);
        statusBar.setStatus("已切换到数据库: " + dbName);
    }

    public void runQuery(String sql) {
        runQuery(sql, null);
    }
    public void runQueryWithNoSetText(String sql) {
        sqlEditor.setText(sql);
        executeQuery(null);
        sqlEditor.setText("");
    }
    public void runQuery(String sql, Runnable onSuccess) {
        sqlEditor.setText(sql);
        executeQuery(onSuccess);
    }

    private void executeQuery() {
        executeQuery(null);
    }

    private void executeQuery(Runnable onSuccess) {
        String sql = sqlEditor.getSelectedText();
        String query = sql;
        if (query == null || query.isBlank()) query = sqlEditor.getText().trim();
        if (query.isBlank()) return;
        final String finalSql = query;

        DatabaseConnector conn = (DatabaseConnector) connectionManager.getConnector(connectionId);
        if (conn == null) {
            SwingUtils.showError(this, "错误", "未连接到数据库");
            return;
        }

        // For SELECT without LIMIT, use pagination
        if (isSelectQuery(finalSql)) {
            lastSelectSql = finalSql;
            currentPage = 1;
            executePagedQuery(conn, finalSql, onSuccess);
            return;
        }

        // Non-SELECT or already has LIMIT — execute directly
        lastSelectSql = null;
        pagePanel.setVisible(false);
        runBtn.setEnabled(false);
        statusBar.setStatus("Executing query...");

        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return conn.execute(finalSql);
            }
            @Override
            protected void done() {
                try {
                    Object result = get();
                    displayFullResult(result);
                    statusBar.setStatus("Query executed successfully");
                    if (onSuccess != null) onSuccess.run();
                } catch (Exception e) {
                    SwingUtils.showError(DatabasePanel.this, "查询失败", e.getMessage());
                    statusBar.setStatus("Query failed: " + e.getMessage());
                }
                runBtn.setEnabled(true);
            }
        }.execute();
    }

    private void executePagedQuery(DatabaseConnector conn, String sql, Runnable onSuccess) {
        runBtn.setEnabled(false);
        statusBar.setStatus("Counting rows...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return conn.executeCountQuery(sql);
            }
            @Override
            protected void done() {
                try {
                    totalRows = get();
                } catch (Exception e) {
                    totalRows = 0;
                }
                // Then load first page
                int offset = (currentPage - 1) * pageSize;
                statusBar.setStatus("Loading page 1...");
                new SwingWorker<List<Map<String, Object>>, Void>() {
                    @Override
                    protected List<Map<String, Object>> doInBackground() throws Exception {
                        return conn.executePagedQuery(sql, offset, pageSize);
                    }
                    @Override
                    protected void done() {
                        try {
                            List<Map<String, Object>> rows = get();
                            displayPagedResult(rows);
                            pagePanel.setVisible(totalRows > pageSize || totalRows > 0);
                            updatePageControls();
                            statusBar.setStatus("共 " + totalRows + " 行, 当前显示第 1 页");
                            if (onSuccess != null) onSuccess.run();
                        } catch (Exception e) {
                            SwingUtils.showError(DatabasePanel.this, "查询失败", e.getMessage());
                            statusBar.setStatus("Query failed: " + e.getMessage());
                        }
                        runBtn.setEnabled(true);
                    }
                }.execute();
            }
        }.execute();
    }

    @SuppressWarnings("unchecked")
    private void displayFullResult(Object result) {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        if (result instanceof List<?> rows) {
            if (rows.isEmpty()) {
                tableModel.addColumn("Message");
                tableModel.addRow(new Object[]{"(empty result set)"});
                return;
            }
            Object first = rows.get(0);
            if (first instanceof Map<?, ?> map) {
                String[] columns = map.keySet().toArray(new String[0]);
                tableModel.setColumnIdentifiers(columns);
                for (Object row : rows) {
                    Map<String, Object> rowMap = (Map<String, Object>) row;
                    Object[] values = new Object[columns.length];
                    for (int i = 0; i < columns.length; i++) {
                        values[i] = rowMap.get(columns[i]);
                    }
                    tableModel.addRow(values);
                }
            }
        } else if (result instanceof String str) {
            tableModel.addColumn("Result");
            for (String line : str.split("\n")) {
                tableModel.addRow(new Object[]{line});
            }
        }
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
    }
}
