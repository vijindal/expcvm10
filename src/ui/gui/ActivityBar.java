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
    private boolean utilitySeparatorAdded = false;

    public ActivityBar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DarkTheme.BG);
        setPreferredSize(new Dimension(DarkTheme.ACTIVITY_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DarkTheme.BORDER));
    }

    /** Adds a calculation-mode activity to the main button group. */
    public void addActivity(String label, ActivityIcon icon, Runnable callback) {
        ActivityButton btn = new ActivityButton(label, icon, callback);
        buttons.add(btn);
        add(btn);
        add(Box.createVerticalStrut(4));

        if (activeButton == null) {
            setActive(btn);
        }
    }

    /** Adds a utility activity (e.g. Inspect), visually separated below the calculation modes. */
    public void addUtility(String label, ActivityIcon icon, Runnable callback) {
        if (!utilitySeparatorAdded) {
            add(Box.createVerticalGlue());
            JPanel line = new JPanel();
            line.setBackground(DarkTheme.BORDER);
            line.setPreferredSize(new Dimension(DarkTheme.ACTIVITY_WIDTH - 16, 1));
            line.setMaximumSize(new Dimension(DarkTheme.ACTIVITY_WIDTH - 16, 1));
            JPanel wrap = new JPanel();
            wrap.setOpaque(false);
            wrap.add(line);
            add(wrap);
            add(Box.createVerticalStrut(4));
            utilitySeparatorAdded = true;
        }
        addActivity(label, icon, callback);
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
        private ActivityIcon icon;
        private Color currentColor = DarkTheme.FG_SECOND;

        public ActivityButton(String label, ActivityIcon icon, Runnable callback) {
            this.icon = icon;
            setToolTipText(label);
            setPreferredSize(new Dimension(48, 48));
            setMaximumSize(new Dimension(48, 48));
            setMinimumSize(new Dimension(48, 48));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    if (!active) {
                        currentColor = DarkTheme.FG_PRIMARY;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (!active) {
                        currentColor = DarkTheme.FG_SECOND;
                        repaint();
                    }
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

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(currentColor);
            icon.paint(g2, 12, 12, 24, 24);
        }

        public void setActive(boolean active) {
            this.active = active;
            if (active) {
                currentColor = DarkTheme.ACCENT;
            } else {
                currentColor = DarkTheme.FG_SECOND;
            }
            repaint();
        }

        public boolean isActive() { return active; }
    }

    // ─── Activity Icon Interface ──────────────────────────────────
    public interface ActivityIcon {
        void paint(Graphics2D g, int x, int y, int width, int height);
    }

    // ─── Built-in Icons ──────────────────────────────────────────
    public static class CircleIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(x + 2, y + 2, w - 4, h - 4);
        }
    }

    public static class LineIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine(x + 2, y + h / 2, x + w - 2, y + h / 2);
        }
    }

    /** 2x2 grid — used for MAP (a two-axis scan). */
    public static class GridIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            g.setStroke(new BasicStroke(1.6f));
            g.drawRect(x + 2, y + 2, w - 4, h - 4);
            g.drawLine(x + w / 2, y + 2, x + w / 2, y + h - 2);
            g.drawLine(x + 2, y + h / 2, x + w - 2, y + h / 2);
        }
    }

    /** Scattered dots — used for the coarse/scatter diagram. */
    public static class ScatterIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            int r = 2;
            int[][] pts = {{x + 4, y + 5}, {x + w - 6, y + 4}, {x + 6, y + h - 6},
                            {x + w - 5, y + h - 5}, {x + w / 2, y + h / 2}};
            for (int[] p : pts) g.fillOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
        }
    }

    public static class DiamondIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            int[] xpts = {x + w / 2, x + w - 2, x + w / 2, x + 2};
            int[] ypts = {y + 2, y + h / 2, y + h - 2, y + h / 2};
            g.setStroke(new BasicStroke(2.0f));
            g.drawPolygon(xpts, ypts, 4);
        }
    }

    public static class InfoIcon implements ActivityIcon {
        @Override
        public void paint(Graphics2D g, int x, int y, int w, int h) {
            g.setStroke(new BasicStroke(2.0f));
            // Draw circle
            g.drawOval(x + 2, y + 2, w - 4, h - 4);
            // Draw 'i'
            int cx = x + w / 2;
            int cy = y + h / 2;
            g.fillRect(cx - 1, cy - 7, 2, 2);
            g.drawLine(cx, cy - 4, cx, cy + 4);
        }
    }
}
