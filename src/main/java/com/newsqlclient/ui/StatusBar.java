package com.newsqlclient.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class StatusBar extends JPanel {

    private final JLabel leftLabel;
    private final JLabel rightLabel;

    public StatusBar() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                new EmptyBorder(2, 8, 2, 8)));

        leftLabel = new JLabel("Ready");
        rightLabel = new JLabel("");

        add(leftLabel, BorderLayout.WEST);
        add(rightLabel, BorderLayout.EAST);
    }

    public void setStatus(String text) { leftLabel.setText(text); }
    public void setRightStatus(String text) { rightLabel.setText(text); }
}
