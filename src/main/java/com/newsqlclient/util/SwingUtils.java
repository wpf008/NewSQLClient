package com.newsqlclient.util;

import javax.swing.*;
import java.awt.*;

public class SwingUtils {

    public static void showError(Component parent, String title, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, createScrollableMessage(message),
                        title, JOptionPane.ERROR_MESSAGE));
    }

    public static void showInfo(Component parent, String title, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(parent, createScrollableMessage(message),
                        title, JOptionPane.INFORMATION_MESSAGE));
    }

    private static JScrollPane createScrollableMessage(String message) {
        JTextArea area = new JTextArea(message, 6, 50);
        area.setEditable(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setBackground(UIManager.getColor("Label.background"));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        return scroll;
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public static GridBagConstraints gbc(int x, int y, int w, int h, double wx, double wy) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = w;
        c.gridheight = h;
        c.weightx = wx;
        c.weighty = wy;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(3, 3, 3, 3);
        return c;
    }

    public static Icon icon(String name) {
        return UIManager.getIcon(name);
    }
}
