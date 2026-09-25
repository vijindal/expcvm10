package ui.gui;

import javax.swing.*;
import java.awt.*;

/** Shared centered placeholder for result panels with no data yet ("No run executed yet."). */
public class EmptyStatePanel extends JPanel {

    private String message;

    public EmptyStatePanel(String message) {
        this.message = message;
        setOpaque(true);
        setBackground(DarkTheme.BG);
    }

    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(DarkTheme.FG_SECOND);
        g2.setFont(DarkTheme.FONT_LABEL);
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = getHeight() / 2;
        g2.drawString(message, Math.max(0, x), y);
    }
}
