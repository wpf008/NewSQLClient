package com.newsqlclient.util;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

public class ThemeManager {

    private static final String KEY = "theme";
    private static final String DARK = "dark";
    private static final String LIGHT = "light";
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    public static boolean isDark() {
        return DARK.equals(PREFS.get(KEY, LIGHT));
    }

    public static void applyTheme(boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("Failed to set theme: " + e.getMessage());
        }
        PREFS.put(KEY, dark ? DARK : LIGHT);
    }

    public static void toggleAndUpdate(Window root) {
        boolean dark = !isDark();
        applyTheme(dark);
        SwingUtilities.updateComponentTreeUI(root);
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
