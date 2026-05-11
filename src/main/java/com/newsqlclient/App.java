package com.newsqlclient;

import com.newsqlclient.ui.MainFrame;
import com.newsqlclient.util.ThemeManager;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.applyTheme(ThemeManager.isDark());
            new MainFrame().setVisible(true);
        });
    }
}
