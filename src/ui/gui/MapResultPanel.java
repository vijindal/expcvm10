package ui.gui;

import service.PropertyScanResult;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Renders a MAP scan result as a 2-D heatmap with iso-value contour lines.
 *
 * Color scale: blue (low) → cyan → green → yellow → red (high).
 * Contour lines: 10 evenly spaced iso-value levels drawn in white.
 */
public class MapResultPanel extends JPanel {

    private PropertyScanResult result;
    private BufferedImage heatImage;

    private static final int PAD_L = 72, PAD_R = 80, PAD_T = 36, PAD_B = 48;
    private static final int N_CONTOURS = 10;
    private static final Color CONTOUR_COLOR = new Color(0xFFFFFF, true);  // semi-transparent white
    private static final Color LABEL_COLOR   = new Color(0xD4D4D4);

    public MapResultPanel() {
        setBackground(DarkTheme.BG);
    }

    public void setResult(PropertyScanResult r) {
        this.result = r;
        this.heatImage = null; // invalidate cache
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (result == null || result.getMapValues() == null) {
            drawMessage(g, "No MAP result to display."); return;
        }

        double[][] data = result.getMapValues();
        int nRows = data.length;       // axis1 index (Y)
        int nCols = data[0].length;    // axis0 index (X)
        if (nRows < 2 || nCols < 2) { drawMessage(g, "Insufficient grid points."); return; }

        double[] a0 = result.getAxis0Values(); // X
        double[] a1 = result.getAxis1Values(); // Y

        int W = getWidth(), H = getHeight();
        int plotW = W - PAD_L - PAD_R;
        int plotH = H - PAD_T - PAD_B;
        if (plotW < 10 || plotH < 10) return;

        double vMin = result.getValueMin(), vMax = result.getValueMax();
        if (Double.isNaN(vMin) || vMin == vMax) { vMin -= 1; vMax += 1; }

        // ── Heatmap image (rebuild if size changed) ──────────────────
        if (heatImage == null || heatImage.getWidth() != plotW || heatImage.getHeight() != plotH) {
            heatImage = buildHeatmap(data, nRows, nCols, plotW, plotH, vMin, vMax);
        }
        g.drawImage(heatImage, PAD_L, PAD_T, null);

        // ── Chart border ─────────────────────────────────────────────
        g.setColor(DarkTheme.BORDER);
        g.drawRect(PAD_L, PAD_T, plotW, plotH);

        // ── Iso-value contour lines (simplified cell-boundary method) ──
        drawContours(g, data, nRows, nCols, plotW, plotH, vMin, vMax);

        // ── Axis ticks & labels ───────────────────────────────────────
        Font small = new Font("Segoe UI", Font.PLAIN, 10);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        // X axis
        int xTicks = Math.min(8, nCols - 1);
        for (int i = 0; i <= xTicks; i++) {
            double v = a0[0] + (a0[nCols-1] - a0[0]) * i / xTicks;
            int px = PAD_L + plotW * i / xTicks;
            g.setColor(new Color(0x888888));
            g.drawLine(px, PAD_T + plotH, px, PAD_T + plotH + 4);
            String lbl = fmt(v);
            g.setColor(LABEL_COLOR);
            g.drawString(lbl, px - fm.stringWidth(lbl)/2, PAD_T + plotH + 16);
        }
        // Y axis
        int yTicks = 5;
        for (int i = 0; i <= yTicks; i++) {
            double v = a1[0] + (a1[nRows-1] - a1[0]) * i / yTicks;
            int py = PAD_T + plotH - plotH * i / yTicks;
            g.setColor(new Color(0x888888));
            g.drawLine(PAD_L - 4, py, PAD_L, py);
            String lbl = fmt(v);
            g.setColor(LABEL_COLOR);
            g.drawString(lbl, PAD_L - fm.stringWidth(lbl) - 6, py + fm.getAscent()/2);
        }

        // Axis labels
        Font axisFont = new Font("Segoe UI", Font.PLAIN, 11);
        g.setFont(axisFont);
        fm = g.getFontMetrics();
        g.setColor(DarkTheme.SECTION_FG);

        String xLabel = result.getAxis0Label();
        g.drawString(xLabel, PAD_L + plotW/2 - fm.stringWidth(xLabel)/2,
                     PAD_T + plotH + PAD_B - 6);

        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI/2);
        String yLabel = result.getAxis1Label();
        g.drawString(yLabel, -(PAD_T + plotH/2) - fm.stringWidth(yLabel)/2, 14);
        g.setTransform(old);

        // Title
        Font titleFont = new Font("Segoe UI", Font.BOLD, 11);
        g.setFont(titleFont);
        g.setColor(DarkTheme.FG_PRIMARY);
        String title = result.getPropertyLabel() + "  (" + result.getAxis1Label()
                       + "  ×  " + result.getAxis0Label() + ")";
        g.drawString(title, PAD_L, PAD_T - 10);

        // ── Color bar ─────────────────────────────────────────────────
        drawColorBar(g, PAD_L + plotW + 12, PAD_T, 16, plotH, vMin, vMax);
    }

    // ── Heatmap image ─────────────────────────────────────────────────

    private BufferedImage buildHeatmap(double[][] data, int nRows, int nCols,
                                       int W, int H, double vMin, double vMax) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < H; py++) {
            int row = (int) ((double)(H - 1 - py) / H * nRows);
            row = Math.max(0, Math.min(nRows - 1, row));
            for (int px = 0; px < W; px++) {
                int col = (int) ((double) px / W * nCols);
                col = Math.max(0, Math.min(nCols - 1, col));
                double v = data[row][col];
                img.setRGB(px, py, Double.isNaN(v) ? 0x1E1E1E : heatColor(v, vMin, vMax).getRGB());
            }
        }
        return img;
    }

    // ── Contour lines (simplified marching-squares) ───────────────────

    private void drawContours(Graphics2D g, double[][] data, int nRows, int nCols,
                               int plotW, int plotH, double vMin, double vMax) {
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(255, 255, 255, 160));
        Font cf = new Font("Segoe UI", Font.PLAIN, 9);
        g.setFont(cf);
        FontMetrics fm = g.getFontMetrics();

        for (int k = 1; k <= N_CONTOURS; k++) {
            double iso = vMin + (vMax - vMin) * k / (N_CONTOURS + 1);
            boolean labelDrawn = false;

            for (int row = 0; row < nRows - 1; row++) {
                for (int col = 0; col < nCols - 1; col++) {
                    double v00 = data[row][col];
                    double v10 = data[row+1][col];
                    double v01 = data[row][col+1];
                    double v11 = data[row+1][col+1];
                    if (anyNaN(v00, v10, v01, v11)) continue;

                    // cell corners in plot coords
                    float x0 = PAD_L + (float) col / (nCols-1) * plotW;
                    float x1 = PAD_L + (float)(col+1) / (nCols-1) * plotW;
                    float y0 = PAD_T + plotH - (float) row / (nRows-1) * plotH;
                    float y1 = PAD_T + plotH - (float)(row+1) / (nRows-1) * plotH;

                    // Collect crossing points on each cell edge
                    java.util.List<float[]> pts = new java.util.ArrayList<>();
                    addCrossing(pts, x0, y0, x0, y1, v00, v10, iso); // left edge
                    addCrossing(pts, x1, y0, x1, y1, v01, v11, iso); // right edge
                    addCrossing(pts, x0, y0, x1, y0, v00, v01, iso); // bottom edge
                    addCrossing(pts, x0, y1, x1, y1, v10, v11, iso); // top edge

                    if (pts.size() >= 2) {
                        g.drawLine((int)pts.get(0)[0], (int)pts.get(0)[1],
                                   (int)pts.get(1)[0], (int)pts.get(1)[1]);
                        // Draw label once per iso level near center
                        if (!labelDrawn && col == nCols / 3) {
                            float lx = (pts.get(0)[0] + pts.get(1)[0]) / 2;
                            float ly = (pts.get(0)[1] + pts.get(1)[1]) / 2;
                            String lbl = fmt(iso);
                            g.setColor(new Color(255, 255, 200, 220));
                            g.drawString(lbl, lx - fm.stringWidth(lbl)/2, ly - 2);
                            g.setColor(new Color(255, 255, 255, 160));
                            labelDrawn = true;
                        }
                    }
                }
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void addCrossing(java.util.List<float[]> pts,
                             float x0, float y0, float x1, float y1,
                             double v0, double v1, double iso) {
        if ((v0 < iso && v1 >= iso) || (v1 < iso && v0 >= iso)) {
            float t = (float)((iso - v0) / (v1 - v0));
            pts.add(new float[]{x0 + t*(x1-x0), y0 + t*(y1-y0)});
        }
    }

    private boolean anyNaN(double... vs) {
        for (double v : vs) if (Double.isNaN(v) || Double.isInfinite(v)) return true;
        return false;
    }

    // ── Color bar ─────────────────────────────────────────────────────

    private void drawColorBar(Graphics2D g, int x, int y, int w, int h, double vMin, double vMax) {
        for (int py = 0; py < h; py++) {
            double t = 1.0 - (double) py / h;
            double v = vMin + (vMax - vMin) * t;
            g.setColor(heatColor(v, vMin, vMax));
            g.fillRect(x, y + py, w, 1);
        }
        g.setColor(DarkTheme.BORDER);
        g.drawRect(x, y, w, h);

        // Labels
        Font f = new Font("Segoe UI", Font.PLAIN, 9);
        g.setFont(f);
        g.setColor(LABEL_COLOR);
        FontMetrics fm = g.getFontMetrics();
        int lx = x + w + 3;
        g.drawString(fmt(vMax), lx, y + fm.getAscent());
        g.drawString(fmt(vMin), lx, y + h);
        double mid = (vMin + vMax) / 2;
        g.drawString(fmt(mid), lx, y + h/2 + fm.getAscent()/2);
    }

    // ── Color mapping (blue→cyan→green→yellow→red) ───────────────────

    private static Color heatColor(double v, double vMin, double vMax) {
        double t = (vMax > vMin) ? Math.max(0, Math.min(1, (v - vMin) / (vMax - vMin))) : 0.5;
        // 4-segment gradient
        float[] r = {0.0f, 0.0f, 0.0f, 1.0f, 1.0f};
        float[] gr = {0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
        float[] b = {1.0f, 1.0f, 0.0f, 0.0f, 0.0f};
        double seg = t * 4;
        int i = Math.min(3, (int) seg);
        float ft = (float)(seg - i);
        return new Color(lerp(r[i],r[i+1],ft), lerp(gr[i],gr[i+1],ft), lerp(b[i],b[i+1],ft));
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }

    private void drawMessage(Graphics2D g, String msg) {
        g.setColor(DarkTheme.FG_SECOND);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (getWidth()-fm.stringWidth(msg))/2, getHeight()/2);
    }

    private static String fmt(double v) {
        if (Math.abs(v) > 9999 || (Math.abs(v) < 0.001 && v != 0))
            return String.format("%.2e", v);
        return String.format("%.1f", v);
    }
}
