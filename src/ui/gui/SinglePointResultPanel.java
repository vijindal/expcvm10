package ui.gui;

import system.ports.EquilibriumResult;

import javax.swing.*;
import java.awt.*;

/** Structured label/value display for a single-point equilibrium result, replacing a raw text dump. */
public class SinglePointResultPanel extends JPanel {

    private final JLabel statusValue = valueLabel();
    private final JLabel iterationsValue = valueLabel();
    private final JLabel temperatureValue = valueLabel();
    private final JLabel pressureValue = valueLabel();
    private final JLabel phasesValue = valueLabel();
    private final JLabel durationValue = valueLabel();
    private final EmptyStatePanel emptyState = new EmptyStatePanel("No run executed yet.");

    public SinglePointResultPanel() {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.BG);
        add(emptyState, BorderLayout.CENTER);
    }

    private static JLabel valueLabel() {
        JLabel lbl = new JLabel("—");
        lbl.setFont(DarkTheme.FONT_MONO);
        lbl.setForeground(DarkTheme.FG_PRIMARY);
        return lbl;
    }

    private JPanel buildRows() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(
                DarkTheme.SPACE_LG, DarkTheme.SPACE_LG, DarkTheme.SPACE_LG, DarkTheme.SPACE_LG));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(DarkTheme.SPACE_SM, DarkTheme.SPACE_MD, DarkTheme.SPACE_SM, DarkTheme.SPACE_MD);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addRow(panel, g, row, "Status", statusValue);
        row = addRow(panel, g, row, "Iterations", iterationsValue);
        row = addRow(panel, g, row, "Temperature", temperatureValue);
        row = addRow(panel, g, row, "Pressure", pressureValue);
        row = addRow(panel, g, row, "Stable phases", phasesValue);
        addRow(panel, g, row, "Duration", durationValue);

        g.gridx = 0; g.gridy = row + 1; g.gridwidth = 2; g.weighty = 1;
        panel.add(Box.createVerticalGlue(), g);
        return panel;
    }

    private int addRow(JPanel panel, GridBagConstraints g, int row, String label, JLabel value) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(DarkTheme.FONT_LABEL_BOLD);
        lbl.setForeground(DarkTheme.SECTION_FG);
        g.gridx = 0; g.gridy = row; g.weightx = 0;
        panel.add(lbl, g);

        g.gridx = 1; g.gridy = row; g.weightx = 1;
        panel.add(value, g);
        return row + 1;
    }

    public void showResult(EquilibriumResult result, long elapsedMillis) {
        removeAll();
        add(buildRows(), BorderLayout.CENTER);

        boolean converged = result.isConverged();
        statusValue.setText(converged ? "CONVERGED" : "NOT CONVERGED");
        statusValue.setForeground(converged ? DarkTheme.SUCCESS : DarkTheme.ERROR_COLOR);
        iterationsValue.setText(String.valueOf(result.getIterations()));
        temperatureValue.setText(String.format("%.2f K", result.getT()));
        pressureValue.setText(String.format("%.2f Pa", result.getP()));
        phasesValue.setText(String.valueOf(result.getStablePhases().size()));
        durationValue.setText(elapsedMillis >= 0 ? elapsedMillis + " ms" : "—");

        revalidate();
        repaint();
    }

    public void showEmpty(String message) {
        removeAll();
        emptyState.setMessage(message);
        add(emptyState, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
