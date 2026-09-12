package ui.gui;

import ui.result.CoarseDiagramResult;
import ui.result.CoarseDiagramResult.GridPoint;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom Swing component rendering a {@link CoarseDiagramResult} as
 * colored dots, one per sampled grid point -- a coarse/scatter phase
 * diagram, distinct from {@link PhaseDiagramPanel}'s ZPF-line rendering.
 *
 * <p>Not a subclass of {@link PhaseDiagramPanel}: that class's {@code
 * paintComponent}/{@code drawDiagram} are hardwired to {@code
 * PhaseDiagramResult}'s line/node model. The small, stable axis-scaling
 * and phase-set-color helpers are duplicated here rather than shared,
 * since they are self-contained (a handful of lines each) and forcing a
 * common base class across two genuinely different rendering models
 * would be a larger, riskier change than this feature warrants.
 */
public class CoarseDiagramPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private CoarseDiagramResult diagram;

    private static final int MARGIN_LEFT   = 60;
    private static final int MARGIN_BOTTOM = 50;
    private static final int MARGIN_RIGHT  = 20;
    private static final int MARGIN_TOP    = 20;
    private static final int TICK_SIZE     = 5;
    private static final int AXIS_WIDTH    = 2;
    private static final int DOT_RADIUS    = 3;

    private static final int LEGEND_WIDTH       = 170;
    private static final int LEGEND_SWATCH_SIZE = 12;
    private static final int LEGEND_ROW_HEIGHT  = 18;
    private static final int LEGEND_PADDING     = 10;

    private static final Font LABEL_FONT  = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font TICK_FONT   = new Font("SansSerif", Font.PLAIN, 9);
    private static final Font TITLE_FONT  = new Font("SansSerif", Font.BOLD, 12);
    private static final Font LEGEND_FONT = new Font("SansSerif", Font.PLAIN, 11);

    private static final Color NON_CONVERGED_COLOR = Color.LIGHT_GRAY;
    private static final String NON_CONVERGED_LABEL = "not converged";

    // Insertion order preserved so the legend lists phase regions in the
    // order they were first encountered while scanning the grid, rather
    // than an arbitrary hash order.
    private final Map<String, Color> phaseSetColours = new LinkedHashMap<>();
    private int colourIndex = 0;
    private boolean anyNonConverged = false;
    private static final Color[] PALETTE = {
            new Color(0,   102, 204),
            new Color(204, 0,   0),
            new Color(0,   153, 0),
            new Color(255, 128, 0),
            new Color(153, 0,   153),
            new Color(0,   153, 153),
            new Color(204, 102, 0),
            new Color(200, 200, 0),
    };

    public CoarseDiagramPanel() {
        setBackground(Color.WHITE);
        setFocusable(true);
    }

    public void setDiagram(CoarseDiagramResult result) {
        this.diagram = result;
        phaseSetColours.clear();
        colourIndex = 0;
        anyNonConverged = false;
        assignColours();
        repaint();
    }

    /**
     * Walks every point once up front (rather than relying on paint
     * order) so the legend's phase-region list and each region's color
     * are stable and complete before the first paint -- e.g. so a
     * region that only appears in the last row sampled still shows up
     * in the legend immediately.
     */
    private void assignColours() {
        if (diagram == null) {
            return;
        }
        for (GridPoint p : diagram.getPoints()) {
            if (p.converged) {
                getColourForPhaseSet(p.stablePhases);
            } else {
                anyNonConverged = true;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (diagram == null) {
            drawPlaceholder(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int xMax = w - MARGIN_RIGHT - LEGEND_WIDTH;
        int yMax = h - MARGIN_BOTTOM;

        g2d.setColor(Color.WHITE);
        g2d.fillRect(MARGIN_LEFT, MARGIN_TOP, xMax - MARGIN_LEFT, yMax - MARGIN_TOP);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(AXIS_WIDTH));
        g2d.drawRect(MARGIN_LEFT, MARGIN_TOP, xMax - MARGIN_LEFT, yMax - MARGIN_TOP);

        drawAxes(g2d, MARGIN_LEFT, MARGIN_TOP, xMax, yMax);
        drawPoints(g2d, MARGIN_LEFT, MARGIN_TOP, xMax, yMax);
        drawTitle(g2d);
        drawLegend(g2d, xMax + MARGIN_RIGHT, MARGIN_TOP);
    }

    private void drawAxes(Graphics2D g2d, int xMin, int yMin, int xMax, int yMax) {

        String[] names = diagram.getAxisNames();
        double[] mins  = diagram.getAxisMin();
        double[] maxs  = diagram.getAxisMax();

        g2d.setColor(Color.BLACK);
        g2d.setFont(TICK_FONT);

        int nTicks = 5;
        for (int i = 0; i <= nTicks; i++) {
            double frac = (double) i / nTicks;
            int x = (int) (xMin + frac * (xMax - xMin));
            g2d.drawLine(x, yMax, x, yMax + TICK_SIZE);
            double val = mins[0] + frac * (maxs[0] - mins[0]);
            String label = String.format("%.2g", val);
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(label);
            g2d.drawString(label, x - tw / 2, yMax + TICK_SIZE + 15);
        }

        g2d.setFont(LABEL_FONT);
        FontMetrics fm = g2d.getFontMetrics();
        int nameW = fm.stringWidth(names[0]);
        g2d.drawString(names[0], xMax - nameW - 10, yMax + 40);

        g2d.setFont(TICK_FONT);
        for (int i = 0; i <= nTicks; i++) {
            double frac = (double) i / nTicks;
            int y = (int) (yMax - frac * (yMax - yMin));
            g2d.drawLine(xMin - TICK_SIZE, y, xMin, y);
            double val = mins[1] + frac * (maxs[1] - mins[1]);
            String label = String.format("%.2g", val);
            fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(label);
            g2d.drawString(label, xMin - tw - 10, y + 4);
        }

        g2d.setFont(LABEL_FONT);
        g2d.rotate(-Math.PI / 2);
        g2d.drawString(names[1], -yMin - 40, 15);
        g2d.rotate(Math.PI / 2);
    }

    private void drawPoints(Graphics2D g2d, int xMin, int yMin, int xMax, int yMax) {
        for (GridPoint p : diagram.getPoints()) {
            int x = dataToPixelX(p.axisXValue, xMin, xMax);
            int y = dataToPixelY(p.axisYValue, yMin, yMax);

            g2d.setColor(p.converged
                    ? getColourForPhaseSet(p.stablePhases)
                    : NON_CONVERGED_COLOR);
            g2d.fillOval(x - DOT_RADIUS, y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS);
        }
    }

    /**
     * Draws a swatch + label per distinct converged phase-set (in the
     * order first seen while scanning the grid, via {@link
     * #assignColours}), plus a final entry for non-converged points if
     * any occurred -- so every color on the plot is identified.
     */
    private void drawLegend(Graphics2D g2d, int xStart, int yStart) {

        g2d.setFont(LEGEND_FONT);
        FontMetrics fm = g2d.getFontMetrics();

        int x = xStart + LEGEND_PADDING;
        int y = yStart + LEGEND_PADDING;

        g2d.setColor(Color.BLACK);
        g2d.drawString("Phase regions", x, y + LEGEND_SWATCH_SIZE - 2);
        y += LEGEND_ROW_HEIGHT + 4;

        List<Map.Entry<String, Color>> entries = new ArrayList<>(phaseSetColours.entrySet());
        for (Map.Entry<String, Color> entry : entries) {
            g2d.setColor(entry.getValue());
            g2d.fillRect(x, y, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);

            String label = entry.getKey().isEmpty() ? "(none)" : entry.getKey();
            int maxTextWidth = LEGEND_WIDTH - LEGEND_SWATCH_SIZE - LEGEND_PADDING * 2 - 6;
            g2d.drawString(truncateToWidth(fm, label, maxTextWidth),
                    x + LEGEND_SWATCH_SIZE + 6, y + LEGEND_SWATCH_SIZE - 2);
            y += LEGEND_ROW_HEIGHT;
        }

        if (anyNonConverged) {
            g2d.setColor(NON_CONVERGED_COLOR);
            g2d.fillRect(x, y, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, y, LEGEND_SWATCH_SIZE, LEGEND_SWATCH_SIZE);
            g2d.drawString(NON_CONVERGED_LABEL, x + LEGEND_SWATCH_SIZE + 6, y + LEGEND_SWATCH_SIZE - 2);
        }
    }

    private String truncateToWidth(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String truncated = text;
        while (truncated.length() > 1 && fm.stringWidth(truncated + ellipsis) > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    private Color getColourForPhaseSet(java.util.List<String> phases) {
        String key = String.join("+", phases);
        return phaseSetColours.computeIfAbsent(key, k -> {
            Color c = PALETTE[colourIndex % PALETTE.length];
            colourIndex++;
            return c;
        });
    }

    private int dataToPixelX(double x, int xMin, int xMax) {
        double[] mins = diagram.getAxisMin();
        double[] maxs = diagram.getAxisMax();
        double frac = (x - mins[0]) / (maxs[0] - mins[0]);
        return (int) (xMin + frac * (xMax - xMin));
    }

    private int dataToPixelY(double y, int yMin, int yMax) {
        double[] mins = diagram.getAxisMin();
        double[] maxs = diagram.getAxisMax();
        double frac = (y - mins[1]) / (maxs[1] - mins[1]);
        return (int) (yMax - frac * (yMax - yMin));
    }

    private void drawPlaceholder(Graphics g) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(TITLE_FONT);
        FontMetrics fm = g.getFontMetrics();
        String msg = "No coarse diagram loaded";
        int x = (getWidth() - fm.stringWidth(msg)) / 2;
        int y = getHeight() / 2;
        g.drawString(msg, x, y);
    }

    private void drawTitle(Graphics2D g2d) {
        if (diagram == null) return;
        g2d.setColor(Color.BLACK);
        g2d.setFont(TITLE_FONT);
        g2d.drawString(diagram.getPoints().size() + " sampled points",
                MARGIN_LEFT + 5, MARGIN_TOP - 5);
    }
}
