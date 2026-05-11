package com.newsqlclient.ui;

import javax.swing.*;
import java.awt.*;

public class TabbedWorkArea extends JTabbedPane {

    public TabbedWorkArea() {
        setTabPlacement(JTabbedPane.TOP);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    public void openPanel(String title, JPanel panel, Icon icon) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTitleAt(i).equals(title)) {
                setSelectedIndex(i);
                return;
            }
        }
        addTab(title, icon, panel);
        setSelectedIndex(getTabCount() - 1);
    }

    public Component findPanel(String title) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTitleAt(i).equals(title)) {
                return getComponentAt(i);
            }
        }
        return null;
    }

    public void selectPanel(String title) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTitleAt(i).equals(title)) {
                setSelectedIndex(i);
                return;
            }
        }
    }

    public void closePanel(String title) {
        for (int i = 0; i < getTabCount(); i++) {
            if (getTitleAt(i).equals(title)) {
                removeTabAt(i);
                return;
            }
        }
    }

    public void closeCurrentTab() {
        int idx = getSelectedIndex();
        if (idx >= 0) {
            removeTabAt(idx);
        }
    }
}
