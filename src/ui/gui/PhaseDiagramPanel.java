package ui.gui;

import ui.result.PhaseDiagramResult;
import ui.result.PhaseDiagramResult.LineSegment;
import ui.result.PhaseDiagramResult.NodePoint;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Swing component for rendering phase diagrams.
 */
public class PhaseDiagramPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private PhaseDiagramResult diagram;

    private static final int    MARGIN_LEFT   = 60;
    private static final int    MARGIN_BOTTOM = 50;
    private static final int    MARGIN_RIGHT  = 20;
    private static final int    MARGIN_TOP    = 20;
    private static final int    TICK_SIZE    = 5;
    private static final int    AXIS_WIDTH   = 2;
    private static final int    LINE_WIDTH   = 2;
    private static final int    NODE_RADIUS  = 5;

    private static final Font   LABEL_FONT   = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font   TICK_FONT    = new Font("SansSerif", Font.PLAIN, 9);
    private static final Font   TITLE_FONT   = new Font("SansSerif", Font.BOLD, 12);

    private final Map<String, Color> phaseSetColours = new HashMap<>();
    private int colourIndex = 0;
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

    public PhaseDiagramPanel() {
        setBackground(Color.WHITE);
        setFocusable(true);
    }

    public void setDiagram(PhaseDiagramResult result) {
        this.diagram = result;
        phaseSetColours.clear();
        colourIndex = 0;
        repaint();
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
        int xMax = w - MARGIN_RIGHT;
        int yMax = h - MARGIN_BOTTOM;

        g2d.setColor(Color.WHITE);
        g2d.fillRect(MARGIN_LEFT, MARGIN_TOP, xMax - MARGIN_LEFT, yMax - MARGIN_TOP);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(AXIS_WIDTH));
        g2d.drawRect(MARGIN_LEFT, MARGIN_TOP, xMax - MARGIN_LEFT, yMax - MARGIN_TOP);

        drawAxes(g2d, w, h, MARGIN_LEFT, MARGIN_TOP, xMax, yMax);

        if (diagram.getLines().size() > 0 || diagram.getNodes().size() > 0) {
            drawDiagram(g2d, MARGIN_LEFT, MARGIN_TOP, xMax, yMax);
        }

        drawTitle(g2d);
    }

    private void drawAxes(Graphics2D g2d, int w, int h,
                          int xMin, int yMin, int xMax, int yMax) {
        if (diagram.numAxes() < 1) return;

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

        if (diagram.numAxes() >= 2) {
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
    }

    private void drawDiagram(Graphics2D g2d, int xMin, int yMin, int xMax, int yMax) {
        g2d.setStroke(new BasicStroke(LINE_WIDTH));
        for (LineSegment line : diagram.getLines()) {
            g2d.setColor(getColourForPhaseSet(line.stablePhases));
            drawLine(g2d, line.coords, xMin, yMin, xMax, yMax);
        }
        for (NodePoint node : diagram.getNodes()) {
            drawNode(g2d, node, xMin, yMin, xMax, yMax);
        }
    }

    private void drawLine(Graphics2D g2d, java.util.List<double[]> coords,
                          int xMin, int yMin, int xMax, int yMax) {
        if (coords.size() < 2) return;
        Path2D path = new Path2D.Double();
        for (int i = 0; i < coords.size(); i++) {
            double[] c = coords.get(i);
            int x = dataToPixelX(c[0], xMin, xMax);
            int y = dataToPixelY(c.length > 1 ? c[1] : 0, yMin, yMax);
            if (i == 0) path.moveTo(x, y);
            else        path.lineTo(x, y);
        }
        g2d.draw(path);
    }

    private void drawNode(Graphics2D g2d, NodePoint node,
                          int xMin, int yMin, int xMax, int yMax) {
        int x = dataToPixelX(node.axisValues[0], xMin, xMax);
        int y = dataToPixelY(node.axisValues.length > 1 ? node.axisValues[1] : 0, yMin, yMax);

        g2d.setColor(Color.BLACK);
        switch (node.type) {
            case CROSSING:
                g2d.drawOval(x - NODE_RADIUS, y - NODE_RADIUS, 2 * NODE_RADIUS, 2 * NODE_RADIUS);
                break;
            case INVARIANT:
                g2d.fillRect(x - NODE_RADIUS, y - NODE_RADIUS, 2 * NODE_RADIUS, 2 * NODE_RADIUS);
                g2d.setColor(Color.WHITE);
                g2d.drawRect(x - NODE_RADIUS, y - NODE_RADIUS, 2 * NODE_RADIUS, 2 * NODE_RADIUS);
                break;
            case BOUNDARY:
                g2d.drawPolygon(
                    new int[]{x, x - NODE_RADIUS, x + NODE_RADIUS},
                    new int[]{y - NODE_RADIUS, y + NODE_RADIUS, y + NODE_RADIUS},
                    3);
                break;
        }
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
        if (diagram.numAxes() < 1) return xMin;
        double[] mins = diagram.getAxisMin();
        double[] maxs = diagram.getAxisMax();
        double frac = (x - mins[0]) / (maxs[0] - mins[0]);
        return (int) (xMin + frac * (xMax - xMin));
    }

    private int dataToPixelY(double y, int yMin, int yMax) {
        if (diagram.numAxes() < 2) return yMax;
        double[] mins = diagram.getAxisMin();
        double[] maxs = diagram.getAxisMax();
        double frac = (y - mins[1]) / (maxs[1] - mins[1]);
        return (int) (yMax - frac * (yMax - yMin));
    }

    private void drawPlaceholder(Graphics g) {
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(TITLE_FONT);
        FontMetrics fm = g.getFontMetrics();
        String msg = "No phase diagram loaded";
        int x = (getWidth() - fm.stringWidth(msg)) / 2;
        int y = getHeight() / 2;
        g.drawString(msg, x, y);
    }

    private void drawTitle(Graphics2D g2d) {
        if (diagram == null) return;
        g2d.setColor(Color.BLACK);
        g2d.setFont(TITLE_FONT);
        g2d.drawString(diagram.numAxes() + "-axis diagram", MARGIN_LEFT + 5, MARGIN_TOP - 5);
    }
}
