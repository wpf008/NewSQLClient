package com.newsqlclient.ui.dialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CreateTableDialog extends JDialog {

    private boolean confirmed;
    private JTextField tableNameField;
    private JTable columnTable;
    private DefaultTableModel columnModel;
    private final String dbType;

    private static final String[] MYSQL_TYPES = {
        "INT", "BIGINT", "VARCHAR", "TEXT", "DATE", "DATETIME",
        "FLOAT", "DOUBLE", "DECIMAL", "BOOLEAN", "BLOB", "LONGTEXT"
    };
    private static final String[] PG_TYPES = {
        "INTEGER", "BIGINT", "SERIAL", "BIGSERIAL", "VARCHAR", "TEXT",
        "DATE", "TIMESTAMP", "FLOAT", "DOUBLE PRECISION", "DECIMAL", "BOOLEAN", "BYTEA"
    };
    private static final String[] SQLITE_TYPES = {
        "INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC"
    };

    public CreateTableDialog(Window parent, String databaseName, String dbType) {
        super(parent, "新建表 - " + databaseName, Dialog.ModalityType.APPLICATION_MODAL);
        this.dbType = dbType != null ? dbType : "mysql";
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private String[] typesForDb() {
        return switch (dbType) {
            case "postgresql" -> PG_TYPES;
            case "sqlite" -> SQLITE_TYPES;
            default -> MYSQL_TYPES;
        };
    }

    private Object[] defaultRow1() {
        return switch (dbType) {
            case "postgresql" -> new Object[]{"id", "SERIAL", "", true, true, false};
            case "sqlite" -> new Object[]{"id", "INTEGER", "", true, true, true};
            default -> new Object[]{"id", "INT", "11", true, true, true};
        };
    }

    private void buildUI() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        namePanel.add(new JLabel("表名:"));
        tableNameField = new JTextField(25);
        namePanel.add(tableNameField);
        panel.add(namePanel, BorderLayout.NORTH);

        // For SQLite, hide the "自增" column (auto-increment is only for INTEGER PRIMARY KEY)
        boolean showAutoInc = !"sqlite".equals(dbType);
        String[] colNames = showAutoInc
                ? new String[]{"列名", "类型", "长度", "非空", "主键", "自增"}
                : new String[]{"列名", "类型", "长度", "非空", "主键"};

        columnModel = new DefaultTableModel(colNames, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col >= 3 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int col) {
                if (col == 5) return Boolean.TRUE.equals(getValueAt(row, 4)); // auto-inc only when PK checked
                return true;
            }
        };
        columnTable = new JTable(columnModel);
        columnTable.setRowHeight(22);
        columnTable.putClientProperty("terminateEditOnFocusLost", true);

        JComboBox<String> typeCombo = new JComboBox<>(typesForDb());
        typeCombo.setEditable(true);
        columnTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));
        columnTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        columnTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        columnTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        columnTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        columnTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        if (showAutoInc) columnTable.getColumnModel().getColumn(5).setPreferredWidth(50);

        columnModel.addRow(defaultRow1());
        columnModel.addRow(new Object[]{"name", "VARCHAR", "255", false, false, false});

        JScrollPane tableScroll = new JScrollPane(columnTable);
        tableScroll.setPreferredSize(new Dimension(600, 200));
        panel.add(tableScroll, BorderLayout.CENTER);

        JPanel colBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addColBtn = new JButton("+ 添加列");
        addColBtn.addActionListener(e -> columnModel.addRow(
                new Object[]{"", "VARCHAR", "255", false, false, false}));
        JButton delColBtn = new JButton("- 删除选中列");
        delColBtn.addActionListener(e -> {
            int row = columnTable.getSelectedRow();
            if (row >= 0) columnModel.removeRow(row);
        });
        JButton moveUpBtn = new JButton("上移");
        moveUpBtn.addActionListener(e -> {
            int row = columnTable.getSelectedRow();
            if (row > 0) columnModel.moveRow(row, row, row - 1);
        });
        JButton moveDownBtn = new JButton("下移");
        moveDownBtn.addActionListener(e -> {
            int row = columnTable.getSelectedRow();
            if (row >= 0 && row < columnModel.getRowCount() - 1) columnModel.moveRow(row, row, row + 1);
        });
        colBtnPanel.add(addColBtn);
        colBtnPanel.add(delColBtn);
        colBtnPanel.add(moveUpBtn);
        colBtnPanel.add(moveDownBtn);
        panel.add(colBtnPanel, BorderLayout.SOUTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("创建表");
        okBtn.addActionListener(e -> {
            if (tableNameField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入表名", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (columnModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "请至少添加一列", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            confirmed = true;
            dispose();
        });
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(colBtnPanel, BorderLayout.WEST);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(bottomPanel, BorderLayout.NORTH);
        panel.add(southPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    public boolean isConfirmed() { return confirmed; }

    public String getCreateSQL(String dbName) {
        String tableName = tableNameField.getText().trim();
        // Prefix with schema/database name for both MySQL and PG
        String fullName = (dbName != null && !dbName.isEmpty())
                ? dbName + "." + tableName : tableName;
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(fullName).append(" (\n");

        List<String> pkCols = new ArrayList<>();
        int colCount = columnModel.getRowCount();
        for (int i = 0; i < colCount; i++) {
            String name = (String) columnModel.getValueAt(i, 0);
            String type = (String) columnModel.getValueAt(i, 1);
            String length = (String) columnModel.getValueAt(i, 2);
            boolean notNull = Boolean.TRUE.equals(columnModel.getValueAt(i, 3));
            boolean pk = Boolean.TRUE.equals(columnModel.getValueAt(i, 4));
            boolean autoInc = columnModel.getColumnCount() > 5
                    && Boolean.TRUE.equals(columnModel.getValueAt(i, 5));

            if (name == null || name.isBlank()) continue;

            sb.append("    ").append(name).append(" ").append(type);

            // SERIAL/BIGSERIAL in PG don't need length
            if (length != null && !length.isBlank() && !"0".equals(length)
                    && !"SERIAL".equalsIgnoreCase(type) && !"BIGSERIAL".equalsIgnoreCase(type)) {
                sb.append("(").append(length).append(")");
            }
            if (notNull && !"SERIAL".equalsIgnoreCase(type) && !"BIGSERIAL".equalsIgnoreCase(type)) {
                sb.append(" NOT NULL");
            }
            if (autoInc) {
                if ("postgresql".equals(dbType)) {
                    // PG SERIAL already handles auto-increment; for other types, use GENERATED
                    sb.append(" GENERATED BY DEFAULT AS IDENTITY");
                } else {
                    sb.append(" AUTO_INCREMENT");
                }
            }
            if (pk) pkCols.add(name);

            if (i < colCount - 1) sb.append(",");
            sb.append("\n");
        }

        if (!pkCols.isEmpty()) {
            sb.append("    ,PRIMARY KEY (").append(String.join(", ", pkCols)).append(")\n");
        }

        sb.append(")");
        return sb.toString();
    }
}
