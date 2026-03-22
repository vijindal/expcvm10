package ui.gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin activity/navigation bar inspired by VS Code.
 *
 * Provides icon-based selection between different calculation modes:
 * - Single Point: Direct equilibrium calculation at fixed T, P, composition
 * - Phase Diagram: Binary/ternary phase boundary mapping
 * - Data Inspection: Inspect TDB metadata and available phases/elements
 */
public class ActivityBar extends JPanel {

    private List<ActivityButton> buttons = new ArrayList<>();
    private ActivityButton activeButton;

    public ActivityBar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DarkTheme.BG);
        setPreferredSize(new Dimension(48, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DarkTheme.BORDER));
    }

    public void addActivity(String label, String symbol, Runnable callback) {
        ActivityButton btn = new ActivityButton(label, symbol, callback);
        buttons.add(btn);
        add(btn);
        add(Box.createVerticalStrut(4));

        if (activeButton == null) {
            setActive(btn);
        }
    }

    public void setActive(ActivityButton button) {
        if (activeButton != null) {
            activeButton.setActive(false);
        }
        activeButton = button;
        button.setActive(true);
        button.doClick();
    }

    // ─── Activity Button ──────────────────────────────────────────
    public class ActivityButton extends JButton {
        private boolean active = false;

        public ActivityButton(String label, String symbol, Runnable callback) {
            setText(symbol);
            setToolTipText(label);
            setFont(new Font("Segoe UI", Font.BOLD, 18));
            setPreferredSize(new Dimension(48, 48));
            setMaximumSize(new Dimension(48, 48));
            setMinimumSize(new Dimension(48, 48));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setForeground(DarkTheme.FG_SECOND);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    if (!active) setForeground(DarkTheme.FG_PRIMARY);
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (!active) setForeground(DarkTheme.FG_SECOND);
                }
            });

            addActionListener(e -> {
                // Deactivate all other buttons
                for (ActivityButton b : buttons) {
                    if (b != this) b.setActive(false);
                }
                setActive(true);
                if (callback != null) callback.run();
            });
        }

        public void setActive(boolean active) {
            this.active = active;
            if (active) {
                setForeground(DarkTheme.ACCENT);
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setForeground(DarkTheme.FG_SECOND);
            }
        }

        public boolean isActive() { return active; }
    }
}
