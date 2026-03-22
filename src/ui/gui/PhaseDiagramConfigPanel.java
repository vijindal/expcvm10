package ui.gui;

import service.PhaseDiagramRequest;
import thermocalc.diagram.AxisConfig;
import thermocalc.diagram.AxisConfig.Type;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration panel for phase diagram calculations (MAP or STEP).
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Axis configs, phase selection, fixed conditions + Calculate button.
 *
 * @param isStep  true → STEP (1-axis) mode; false → MAP (2-axis) mode
 */
public class PhaseDiagramConfigPanel extends JPanel {

    private final boolean isStep;
    private final DatabaseExtractionPanel dbPanel;

    private JComboBox<String> axis0TypeCombo;
    private RangeField axis0Range;

    // MAP only
    private JComboBox<String> axis1TypeCombo;
    private RangeField axis1Range;
    private JPanel axis1Section;

    // STEP: start composition
    private JTextField startCompositionField;

    private JTextField pressureField;
    private JTextField temperatureField;

    // Phase selection
    private JPanel phaseCheckBoxPanel;
    private JScrollPane phaseScrollPane;
    private final List<JCheckBox> phaseCheckBoxes = new ArrayList<>();

    private JButton calculateButton;
    private JLabel statusLabel;

    private Runnable onCalculate;
    private Runnable onAbort;

    public PhaseDiagramConfigPanel(MainController controller, boolean isStep) {
        this.isStep = isStep;
        setLayout(new BorderLayout());
        setBackground(DarkTheme.SIDEBAR_BG);

        dbPanel = new DatabaseExtractionPanel(controller);
        dbPanel.setDefaults("data/tizr_kum_cvm.tdb", List.of("TI", "ZR"));
        dbPanel.setOnSelectionChanged(sel -> populatePhaseCheckBoxes(sel.getAvailablePhases()));

        JPanel lowerContent = buildLowerContent();
        JScrollPane scroll = new JScrollPane(lowerContent,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(DarkTheme.SIDEBAR_BG);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(dbPanel, BorderLayout.CENTER);
        top.add(DarkTheme.separator(), BorderLayout.SOUTH);

        add(top,    BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    /** Backwards-compatible constructor: defaults to MAP mode. */
    public PhaseDiagramConfigPanel(MainController controller) {
        this(controller, false);
    }

    // ── Phase selection ────────────────────────────────────────────────

    private void populatePhaseCheckBoxes(List<String> phases) {
        phaseCheckBoxPanel.removeAll();
        phaseCheckBoxes.clear();
        if (phases != null) {
            for (String phase : phases) {
                JCheckBox cb = new JCheckBox(phase, true);
                cb.setOpaque(false);
                cb.setForeground(DarkTheme.FG_PRIMARY);
                cb.setFont(new Font("Consolas", Font.PLAIN, 10));
                phaseCheckBoxes.add(cb);
                phaseCheckBoxPanel.add(cb);
            }
        }
        phaseCheckBoxPanel.revalidate();
        phaseCheckBoxPanel.repaint();
        // resize scroll pane height based on content
        int rows = Math.max(1, phaseCheckBoxes.size());
        int h = Math.min(rows * 20, 120);
        phaseScrollPane.setPreferredSize(new Dimension(0, h));
        phaseScrollPane.revalidate();
    }

    private List<String> getSelectedPhases() {
        List<String> selected = new ArrayList<>();
        for (JCheckBox cb : phaseCheckBoxes) {
            if (cb.isSelected()) selected.add(cb.getText());
        }
        return selected;
    }

    // ── Layout ─────────────────────────────────────────────────────────

    private JPanel buildLowerContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(6, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // ── Axis 0 ─────────────────────────────────────────────────
        addSectionLabel(panel, gbc, row++, isStep ? "AXIS (Scan Variable)" : "AXIS 0 (X-Axis)");
        String defAxis0Type = isStep ? "TEMPERATURE" : "COMPOSITION";
        axis0TypeCombo = addAxisTypeCombo(panel, gbc, row++, "Type", defAxis0Type);
        axis0Range = addRangeField(panel, gbc, row++, "Range",
                isStep ? "500, 1500, 100" : "0.0, 1.0, 0.1");
        axis0TypeCombo.addItemListener(e -> axis0Range.setText(
                "COMPOSITION".equals(axis0TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "500, 1500, 100"));

        // ── Axis 1 (MAP only) ───────────────────────────────────────
        if (!isStep) {
            addSectionLabel(panel, gbc, row++, "AXIS 1 (Y-Axis)");
            axis1TypeCombo = addAxisTypeCombo(panel, gbc, row++, "Type", "TEMPERATURE");
            axis1Range = addRangeField(panel, gbc, row++, "Range", "500, 1500, 100");
            axis1TypeCombo.addItemListener(e -> axis1Range.setText(
                    "COMPOSITION".equals(axis1TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "500, 1500, 100"));
        }

        // ── Phases ──────────────────────────────────────────────────
        addSectionLabel(panel, gbc, row++, "PHASES");

        phaseCheckBoxPanel = new JPanel();
        phaseCheckBoxPanel.setLayout(new BoxLayout(phaseCheckBoxPanel, BoxLayout.Y_AXIS));
        phaseCheckBoxPanel.setBackground(DarkTheme.SIDEBAR_BG);
        phaseScrollPane = new JScrollPane(phaseCheckBoxPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        phaseScrollPane.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        phaseScrollPane.getViewport().setBackground(DarkTheme.SIDEBAR_BG);
        phaseScrollPane.setPreferredSize(new Dimension(0, 60));

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(phaseScrollPane, gbc);
        gbc.gridwidth = 1;
        row++;

        // Select All / Deselect All buttons
        JButton selAllBtn = smallButton("All");
        selAllBtn.addActionListener(e -> phaseCheckBoxes.forEach(cb -> cb.setSelected(true)));
        JButton deselBtn = smallButton("None");
        deselBtn.addActionListener(e -> phaseCheckBoxes.forEach(cb -> cb.setSelected(false)));
        JPanel selRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selRow.setOpaque(false);
        selRow.add(selAllBtn);
        selRow.add(deselBtn);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(selRow, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── Fixed conditions ────────────────────────────────────────
        addSectionLabel(panel, gbc, row++, "FIXED CONDITIONS");
        pressureField = addTextField(panel, gbc, row++, "Pressure (Pa)", "101325.0");
        if (isStep) {
            startCompositionField = addTextField(panel, gbc, row++, "x(comp 2)", "0.5");
        } else {
            temperatureField = addTextField(panel, gbc, row++, "Temperature (K)", "");
        }

        // Filler
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btn.setForeground(DarkTheme.FG_SECOND);
        btn.setBackground(DarkTheme.BG_INPUT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setMargin(new Insets(1, 6, 1, 6));
        return btn;
    }

    private JComponent buildButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(8, 10, 10, 10));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusLabel.setForeground(DarkTheme.FG_SECOND);

        String btnText = isStep ? "Calculate STEP" : "Calculate MAP Diagram";
        calculateButton = new JButton(btnText);
        calculateButton.setBackground(DarkTheme.ACCENT);
        calculateButton.setForeground(Color.WHITE);
        calculateButton.setFocusPainted(false);
        calculateButton.setBorderPainted(false);
        calculateButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        calculateButton.setMargin(new Insets(8, 16, 8, 16));
        calculateButton.addActionListener(e -> {
            if ("Abort".equals(calculateButton.getText())) { if (onAbort != null) onAbort.run(); }
            else onCalculateClicked();
        });

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.add(statusLabel, BorderLayout.WEST);
        bottomRow.add(calculateButton, BorderLayout.EAST);

        panel.add(bottomRow, BorderLayout.CENTER);
        return panel;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void addSectionLabel(JPanel panel, GridBagConstraints gbc, int row, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setForeground(DarkTheme.SECTION_FG);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(label, gbc);
        gbc.gridwidth = 1;
    }

    private JTextField addTextField(JPanel panel, GridBagConstraints gbc, int row,
                                    String label, String defaultValue) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(labelComp, gbc);

        JTextField field = new JTextField(defaultValue);
        field.setBackground(DarkTheme.BG_INPUT);
        field.setForeground(DarkTheme.FG_PRIMARY);
        field.setCaretColor(DarkTheme.FG_PRIMARY);
        field.setFont(new Font("Consolas", Font.PLAIN, 10));
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(field, gbc);
        gbc.gridwidth = 1;
        return field;
    }

    private RangeField addRangeField(JPanel panel, GridBagConstraints gbc, int row,
                                     String label, String defaultValue) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(labelComp, gbc);

        RangeField rf = new RangeField(defaultValue);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(rf, gbc);
        gbc.gridwidth = 1;
        return rf;
    }

    private JComboBox<String> addAxisTypeCombo(JPanel panel, GridBagConstraints gbc, int row,
                                               String label, String selected) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(labelComp, gbc);

        JComboBox<String> combo = new JComboBox<>(new String[]{"COMPOSITION", "TEMPERATURE", "PRESSURE"});
        combo.setSelectedItem(selected);
        combo.setBackground(DarkTheme.BG_INPUT);
        combo.setForeground(DarkTheme.FG_PRIMARY);
        combo.setRenderer(new DarkTheme.ComboRenderer());
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(combo, gbc);
        gbc.gridwidth = 1;
        return combo;
    }

    // ── Public API ─────────────────────────────────────────────────────

    public PhaseDiagramRequest buildRequest() {
        service.DatabaseSelection sel = dbPanel.getSelection();

        PhaseDiagramRequest request = new PhaseDiagramRequest();
        request.setTdbFilePath(sel.getTdbPath() != null ? sel.getTdbPath() : "");
        request.setElements(sel.getElements());

        List<String> phases = getSelectedPhases();
        if (phases.isEmpty()) phases = sel.getAvailablePhases();
        request.setPhases(phases);

        request.setDiagramType(isStep ? PhaseDiagramRequest.DiagramType.STEP
                                      : PhaseDiagramRequest.DiagramType.MAP);

        List<AxisConfig> axes = new ArrayList<>();
        AxisConfig axis0 = buildAxisConfig(axis0TypeCombo.getSelectedItem().toString(), axis0Range, "Axis0");
        if (axis0 != null) axes.add(axis0);

        if (!isStep) {
            AxisConfig axis1 = buildAxisConfig(axis1TypeCombo.getSelectedItem().toString(), axis1Range, "Axis1");
            if (axis1 != null) axes.add(axis1);
        }
        request.setAxes(axes);

        try { request.setFixedP(Double.parseDouble(pressureField.getText().trim())); }
        catch (NumberFormatException e) { request.setFixedP(101325.0); }

        if (isStep && startCompositionField != null) {
            try {
                double x2 = Double.parseDouble(startCompositionField.getText().trim());
                request.setStartComposition(new double[]{1.0 - x2, x2});
            } catch (NumberFormatException ignored) {}
        } else if (!isStep && temperatureField != null
                   && !temperatureField.getText().trim().isEmpty()) {
            try { request.setFixedT(Double.parseDouble(temperatureField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        return request;
    }

    private AxisConfig buildAxisConfig(String typeStr, RangeField rf, String axisName) {
        if (rf == null || !rf.isValid()) return null;
        double min = rf.getMin(), max = rf.getMax(), step = rf.getStep();
        if ("TEMPERATURE".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (K)", Type.TEMPERATURE, min, max, step);
        if ("PRESSURE".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (Pa)", Type.PRESSURE, min, max, step);
        if ("COMPOSITION".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (X)", 1, min, max, step);
        return null;
    }

    private void onCalculateClicked() {
        statusLabel.setText("Validating inputs...");
        statusLabel.setForeground(DarkTheme.FG_SECOND);

        service.DatabaseSelection sel = dbPanel.getSelection();
        if (!sel.hasTdb()) {
            setStatus("Error: TDB file required", DarkTheme.ERROR_COLOR); return;
        }
        if (!sel.hasElements()) {
            setStatus("Error: Elements required", DarkTheme.ERROR_COLOR); return;
        }
        if (!sel.hasPhases()) {
            setStatus("Error: No phases available", DarkTheme.ERROR_COLOR); return;
        }

        statusLabel.setText("Calculating...");
        statusLabel.setForeground(DarkTheme.ACCENT);
        if (onCalculate != null) onCalculate.run();
    }

    public void setCalculateCallback(Runnable callback) { this.onCalculate = callback; }

    public void setAbortCallback(Runnable callback) { this.onAbort = callback; }

    public void setRunning(boolean running) {
        if (running) {
            calculateButton.setText("Abort");
            calculateButton.setBackground(DarkTheme.ERROR_COLOR);
        } else {
            calculateButton.setText(isStep ? "Calculate STEP" : "Calculate MAP Diagram");
            calculateButton.setBackground(DarkTheme.ACCENT);
        }
    }

    public void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
}
