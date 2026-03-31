package ui.gui;

import ui.result.PropertyScanResult;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Renders a STEP scan result as a line chart (property vs one variable).
 */
public class StepResultPanel extends JPanel {

    private PropertyScanResult result;
    private static final int PAD_L = 72, PAD_R = 20, PAD_T = 36, PAD_B = 48;
    private static final Color LINE_COLOR  = new Color(0x4FC3F7); // light blue
    private static final Color GRID_COLOR  = new Color(0x3A3A3A);
    private static final Color AXIS_COLOR  = new Color(0x888888);
    private static final Color LABEL_COLOR = new Color(0xD4D4D4);

    public StepResultPanel() {
        setBackground(DarkTheme.BG);
    }

    public void setResult(PropertyScanResult r) {
        this.result = r;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (result == null || result.getAxis0Values() == null || result.getStepValues() == null) {
            drawMessage(g, "No STEP result to display.");
            return;
        }

        double[] xs = result.getAxis0Values();
        double[] ys = result.getStepValues();
        int n = Math.min(xs.length, ys.length);
        if (n < 2) { drawMessage(g, "Insufficient data points."); return; }

        int W = getWidth(), H = getHeight();
        double plotW = W - PAD_L - PAD_R;
        double plotH = H - PAD_T - PAD_B;

        double xMin = xs[0], xMax = xs[n-1];
        double yMin = result.getValueMin(), yMax = result.getValueMax();
        if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin == yMax) {
            yMin -= 1; yMax += 1;
        }
        double yRange = yMax - yMin;
        yMin -= yRange * 0.05; yMax += yRange * 0.05;

        // ── Chart box ─────────────────────────────────────────────────
        g.setColor(new Color(0x1A1A1A));
        g.fillRect(PAD_L, PAD_T, (int) plotW, (int) plotH);
        g.setColor(DarkTheme.BORDER);
        g.drawRect(PAD_L, PAD_T, (int) plotW, (int) plotH);

        // ── Grid lines ────────────────────────────────────────────────
        Font small = new Font("Segoe UI", Font.PLAIN, 10);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        int yTicks = 5;
        g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{3f, 3f}, 0f));
        for (int i = 0; i <= yTicks; i++) {
            double val = yMin + (yMax - yMin) * i / yTicks;
            int py = PAD_T + (int) (plotH * (1.0 - (double) i / yTicks));
            g.setColor(GRID_COLOR);
            g.drawLine(PAD_L, py, PAD_L + (int) plotW, py);
            String lbl = fmt(val);
            g.setColor(LABEL_COLOR);
            g.drawString(lbl, PAD_L - fm.stringWidth(lbl) - 4, py + fm.getAscent() / 2);
        }
        g.setStroke(new BasicStroke(1f));

        // ── Line ──────────────────────────────────────────────────────
        g.setStroke(new BasicStroke(1.8f));
        g.setColor(LINE_COLOR);
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(ys[i]) || Double.isInfinite(ys[i])) { first = true; continue; }
            int px = PAD_L + (int) (plotW * (xs[i] - xMin) / (xMax == xMin ? 1 : xMax - xMin));
            int py = PAD_T + (int) (plotH * (1.0 - (ys[i] - yMin) / (yMax - yMin)));
            if (first) { path.moveTo(px, py); first = false; }
            else         path.lineTo(px, py);
        }
        g.draw(path);
        g.setStroke(new BasicStroke(1f));

        // ── X axis tick labels ────────────────────────────────────────
        g.setColor(LABEL_COLOR);
        int xTicks = Math.min(8, n - 1);
        for (int i = 0; i <= xTicks; i++) {
            double val = xMin + (xMax - xMin) * i / xTicks;
            int px = PAD_L + (int) (plotW * i / xTicks);
            g.setColor(AXIS_COLOR);
            g.drawLine(px, PAD_T + (int) plotH, px, PAD_T + (int) plotH + 4);
            String lbl = fmt(val);
            g.setColor(LABEL_COLOR);
            g.drawString(lbl, px - fm.stringWidth(lbl) / 2, PAD_T + (int) plotH + 16);
        }

        // ── Axis labels ───────────────────────────────────────────────
        Font axisFont = new Font("Segoe UI", Font.PLAIN, 11);
        g.setFont(axisFont);
        fm = g.getFontMetrics();
        g.setColor(DarkTheme.SECTION_FG);

        // X label
        String xLabel = result.getAxis0Label();
        g.drawString(xLabel, PAD_L + (int)(plotW/2) - fm.stringWidth(xLabel)/2,
                     PAD_T + (int) plotH + PAD_B - 6);

        // Y label (rotated)
        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2);
        String yLabel = result.getPropertyLabel();
        g.drawString(yLabel, -(PAD_T + (int)(plotH/2)) - fm.stringWidth(yLabel)/2, 14);
        g.setTransform(old);

        // ── Title ─────────────────────────────────────────────────────
        Font titleFont = new Font("Segoe UI", Font.BOLD, 11);
        g.setFont(titleFont);
        g.setColor(DarkTheme.FG_PRIMARY);
        String title = result.getPropertyLabel() + "  vs  " + result.getAxis0Label();
        g.drawString(title, PAD_L, PAD_T - 10);
    }

    private void drawMessage(Graphics2D g, String msg) {
        g.setColor(DarkTheme.FG_SECOND);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }

    private static String fmt(double v) {
        if (Math.abs(v) > 9999 || (Math.abs(v) < 0.001 && v != 0))
            return String.format("%.2e", v);
        return String.format("%.2f", v);
    }
}
