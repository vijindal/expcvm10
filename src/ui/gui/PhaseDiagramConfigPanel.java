package ui.gui;

import ui.request.PhaseDiagramRequest;
import ui.request.AxisConfig;
import ui.request.AxisConfig.Type;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration panel for phase diagram calculations (MAP, STEP, or
 * COARSE -- a scatter/dot diagram sampled independently at each grid
 * point, see {@code CoarseDiagramTracer}).
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Axis configs, phase selection, fixed conditions + Calculate button.
 */
public class PhaseDiagramConfigPanel extends JPanel {

    /** Which calculation this panel is configured for. */
    public enum Mode { STEP, MAP, COARSE }

    private final Mode mode;
    private final boolean isStep;
    private final DatabaseExtractionPanel dbPanel;

    private JComboBox<String> axis0TypeCombo;
    private RangeField axis0Range;

    // MAP/COARSE only
    private JComboBox<String> axis1TypeCombo;
    private RangeField axis1Range;
    private JPanel axis1Section;

    // COARSE only: toggles axis 1 between TEMPERATURE (binary) and a
    // second COMPOSITION axis picked by element (ternary); one field per
    // element for the fixed/start composition instead of a raw CSV field.
    private JCheckBox ternaryModeCheckBox;
    private JComboBox<String> axis1ElementCombo;
    private JLabel axis1ElementLabel;
    private JLabel axis0SectionLabel;
    private JPanel compositionFieldsPanel;
    private final List<JTextField> compositionFields = new ArrayList<>();
    private List<String> compositionFieldsElements = new ArrayList<>();

    // STEP: start composition (label shows the actual 2nd element symbol,
    // e.g. "x(Zr)" for V-Zr, once a database/elements are selected)
    private JTextField startCompositionField;
    private JLabel startCompositionLabel;

    // MAP: starting point for Algorithm B's stable-set search, decoupled
    // from axis0's own scan range (its min/max) and axis1's scan range --
    // see PhaseDiagramRequest#setStartAxisValue.
    private JTextField startTemperatureField;
    private JTextField startCompositionMapField;
    private JLabel startCompositionMapLabel;

    private JTextField pressureField;
    private JTextField temperatureField;

    // Phase selection
    private TagInputField phasesField;

    private JButton calculateButton;
    private BusyStatusBar busyBar;

    private Runnable onCalculate;

    private GuiCalculationContext context;

    public PhaseDiagramConfigPanel(MainController controller, Mode mode) {
        this.mode = mode;
        this.isStep = mode == Mode.STEP;
        setLayout(new BorderLayout());
        setBackground(DarkTheme.SIDEBAR_BG);

        dbPanel = new DatabaseExtractionPanel(controller);
        dbPanel.setDefaults("data/tizr_kum_cvm.tdb", List.of("TI", "ZR"));
        dbPanel.setOnSelectionChanged(sel -> populatePhases(sel.getAvailablePhases()));

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

    /** Backwards-compatible constructor: {@code isStep=true} -> STEP mode, else MAP mode. */
    public PhaseDiagramConfigPanel(MainController controller, boolean isStep) {
        this(controller, isStep ? Mode.STEP : Mode.MAP);
    }

    /** Backwards-compatible constructor: defaults to MAP mode. */
    public PhaseDiagramConfigPanel(MainController controller) {
        this(controller, Mode.MAP);
    }

    // ── Phase selection ────────────────────────────────────────────────

    private void populatePhases(List<String> availablePhases) {
        List<String> preSelected = context != null ? context.getSelectedPhases() : List.of();

        phasesField.setKnownValues(availablePhases);
        phasesField.clear();
        for (String p : preSelected) {
            if (availablePhases != null && availablePhases.contains(p)) phasesField.addKnownSelectedValue(p);
        }
        pushSelectedPhasesToContext();
    }

    private List<String> getSelectedPhases() {
        return phasesField.getValues();
    }

    private void pushSelectedPhasesToContext() {
        if (context != null) context.setSelectedPhases(getSelectedPhases());
    }

    // ── Layout ─────────────────────────────────────────────────────────

    private JPanel buildLowerContent() {
        return mode == Mode.COARSE ? buildCoarseLowerContent() : buildStepOrMapLowerContent();
    }

    private JPanel buildStepOrMapLowerContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(6, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // ── Axis 0 ─────────────────────────────────────────────────
        // MAP's axis 1 is fixed to COMPOSITION below, so axis 0 defaults
        // to TEMPERATURE for the classic T-x map; STEP has no axis 1 and
        // keeps its own COMPOSITION default.
        addSectionLabel(panel, gbc, row++, isStep ? "AXIS (Scan Variable)" : "AXIS 0 (X-Axis)");
        axis0TypeCombo = addAxisTypeCombo(panel, gbc, row++, "Type", isStep ? "COMPOSITION" : "TEMPERATURE");
        axis0Range = addRangeField(panel, gbc, row++, "Range", "300, 2500, 100");
        axis0TypeCombo.addItemListener(e -> axis0Range.setText(
                "COMPOSITION".equals(axis0TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "300, 2500, 100"));

        // ── Axis 1 (MAP only) ───────────────────────────────────────
        // PhaseDiagramEngine.calculatePhaseDiagram requires axes[1] to be
        // COMPOSITION for a 2-axis MAP (Sundman 2021 S3.3's binary case);
        // offering TEMPERATURE/PRESSURE here would only fail later in the
        // background worker, so the combo is fixed to the one legal value.
        if (!isStep) {
            addSectionLabel(panel, gbc, row++, "AXIS 1 (Y-Axis)");
            axis1TypeCombo = addAxisTypeCombo(panel, gbc, row++, "Type", "COMPOSITION",
                    new String[]{"COMPOSITION"});
            axis1Range = addRangeField(panel, gbc, row++, "Range", "0.0, 1.0, 0.1");
        }

        row = addPhaseSelectionSection(panel, gbc, row);

        // ── Fixed conditions ────────────────────────────────────────
        addSectionLabel(panel, gbc, row++, "FIXED CONDITIONS");
        pressureField = addTextField(panel, gbc, row++, "Pressure (Pa)", "101325.0");
        if (isStep) {
            startCompositionLabel = new JLabel("x(comp 2)");
            startCompositionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
            panel.add(startCompositionLabel, gbc);

            startCompositionField = new JTextField("0.5");
            startCompositionField.setBackground(DarkTheme.BG_INPUT);
            startCompositionField.setForeground(DarkTheme.FG_PRIMARY);
            startCompositionField.setCaretColor(DarkTheme.FG_PRIMARY);
            startCompositionField.setFont(new Font("Consolas", Font.PLAIN, 10));
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
            panel.add(startCompositionField, gbc);
            gbc.gridwidth = 1;
            row++;

            dbPanel.setOnSelectionChanged(sel -> {
                populatePhases(sel.getAvailablePhases());
                List<String> els = sel.getElements();
                startCompositionLabel.setText(
                        els != null && els.size() >= 2 ? "x(" + els.get(1) + ")" : "x(comp 2)");
            });
        } else {
            // MAP: axis0 is T by default and axis1 is COMPOSITION (see
            // above), so fixedT is unused here (PhaseDiagramRequest#fixedT
            // is only read when T isn't a diagram axis) -- what MAP needs
            // instead is where in the diagram Algorithm B starts its
            // stable-set search, which used to silently default to
            // axis0.min/axis1.min (composition = 0, a pure-component edge
            // that often has no transition in range). Both are now
            // user-set fields.
            startTemperatureField = addTextField(panel, gbc, row++, "Start T (K)", "300");
            startCompositionMapLabel = new JLabel("Start x(comp 2)");
            startCompositionMapLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
            panel.add(startCompositionMapLabel, gbc);

            startCompositionMapField = new JTextField("0.5");
            startCompositionMapField.setBackground(DarkTheme.BG_INPUT);
            startCompositionMapField.setForeground(DarkTheme.FG_PRIMARY);
            startCompositionMapField.setCaretColor(DarkTheme.FG_PRIMARY);
            startCompositionMapField.setFont(new Font("Consolas", Font.PLAIN, 10));
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
            panel.add(startCompositionMapField, gbc);
            gbc.gridwidth = 1;
            row++;

            dbPanel.setOnSelectionChanged(sel -> {
                populatePhases(sel.getAvailablePhases());
                List<String> els = sel.getElements();
                startCompositionMapLabel.setText(
                        els != null && els.size() >= 2 ? "Start x(" + els.get(1) + ")" : "Start x(comp 2)");
            });
        }

        // Filler
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    /**
     * COARSE mode's own, simplified layout: axis 0 is always the first
     * component's composition (no type picker to disable), a single
     * "Ternary diagram" toggle swaps axis 1 between a temperature range
     * and a second component's composition range picked from a dropdown
     * (no raw index spinner), and the fixed composition is one labeled
     * field per element (no comma-separated string to get wrong) that
     * only needs a value for whichever elements are NOT swept by an
     * axis. Default ranges use a denser 0.02 step (vs. 0.1 elsewhere)
     * since a coarse scatter diagram's whole value is in the density of
     * its sampled grid.
     */
    private JPanel buildCoarseLowerContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(6, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        String denseDefault = "0.0, 1.0, 0.02";

        axis0SectionLabel = addSectionLabel(panel, gbc, row++, "AXIS 0: composition x(comp 1)");
        axis0Range = addRangeField(panel, gbc, row++, "Range", denseDefault);

        addSectionLabel(panel, gbc, row++, "AXIS 1");
        ternaryModeCheckBox = new JCheckBox("Ternary diagram (2nd composition axis)");
        ternaryModeCheckBox.setOpaque(false);
        ternaryModeCheckBox.setForeground(DarkTheme.FG_PRIMARY);
        ternaryModeCheckBox.setFont(new Font("Consolas", Font.PLAIN, 10));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(ternaryModeCheckBox, gbc);
        gbc.gridwidth = 1;
        row++;

        axis1ElementLabel = new JLabel("Axis 1 = Temperature (K)");
        axis1ElementLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(axis1ElementLabel, gbc);

        axis1ElementCombo = new JComboBox<>();
        axis1ElementCombo.setBackground(DarkTheme.BG_INPUT);
        axis1ElementCombo.setForeground(DarkTheme.FG_PRIMARY);
        axis1ElementCombo.setRenderer(new DarkTheme.ComboRenderer());
        axis1ElementCombo.setEnabled(false);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(axis1ElementCombo, gbc);
        gbc.gridwidth = 1;
        row++;

        axis1Range = addRangeField(panel, gbc, row++, "Range", "300, 2500, 25");

        ternaryModeCheckBox.addItemListener(e -> {
            boolean ternary = ternaryModeCheckBox.isSelected();
            axis1ElementCombo.setEnabled(ternary);
            axis1ElementLabel.setText(ternary ? "Axis 1 = composition of" : "Axis 1 = Temperature (K)");
            axis1Range.setText(ternary ? denseDefault : "300, 2500, 25");
        });

        row = addPhaseSelectionSection(panel, gbc, row);

        addSectionLabel(panel, gbc, row++, "FIXED CONDITIONS");
        pressureField = addTextField(panel, gbc, row++, "Pressure (Pa)", "101325.0");
        temperatureField = addTextField(panel, gbc, row++, "Temperature (K)", "1500.0");

        addSectionLabel(panel, gbc, row++, "COMPOSITION (non-swept elements)");
        compositionFieldsPanel = new JPanel();
        compositionFieldsPanel.setLayout(new BoxLayout(compositionFieldsPanel, BoxLayout.Y_AXIS));
        compositionFieldsPanel.setOpaque(false);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(compositionFieldsPanel, gbc);
        gbc.gridwidth = 1;
        row++;

        dbPanel.setOnSelectionChanged(sel -> {
            populatePhases(sel.getAvailablePhases());
            populateCoarseElementUI(sel.getElements());
        });

        // Filler
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private int addPhaseSelectionSection(JPanel panel, GridBagConstraints gbc, int row) {
        addSectionLabel(panel, gbc, row++, "PHASES");

        phasesField = new TagInputField("Type phase name, Enter or Add", true);
        phasesField.setEmptyStateText("Empty = all available phases");
        phasesField.setOnChanged(this::pushSelectedPhasesToContext);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(phasesField, gbc);
        gbc.gridwidth = 1;
        row++;

        return row;
    }

    /** Repopulates the axis-1 element dropdown and the per-element composition fields. */
    private void populateCoarseElementUI(List<String> elements) {
        compositionFieldsElements = elements != null ? new ArrayList<>(elements) : new ArrayList<>();

        axis0SectionLabel.setText(compositionFieldsElements.isEmpty()
                ? "AXIS 0: composition x(comp 1)"
                : "AXIS 0: composition x(" + compositionFieldsElements.get(0) + ")");

        axis1ElementCombo.removeAllItems();
        for (int i = 1; i < compositionFieldsElements.size(); i++) {
            axis1ElementCombo.addItem(compositionFieldsElements.get(i));
        }

        compositionFieldsPanel.removeAll();
        compositionFields.clear();
        for (String el : compositionFieldsElements) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            JLabel label = new JLabel("x(" + el + ")");
            label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            label.setForeground(DarkTheme.FG_PRIMARY);
            label.setPreferredSize(new Dimension(60, 20));
            JTextField field = new JTextField(String.format("%.3f", 1.0 / compositionFieldsElements.size()));
            field.setBackground(DarkTheme.BG_INPUT);
            field.setForeground(DarkTheme.FG_PRIMARY);
            field.setCaretColor(DarkTheme.FG_PRIMARY);
            field.setFont(new Font("Consolas", Font.PLAIN, 10));
            row.add(label, BorderLayout.WEST);
            row.add(field, BorderLayout.CENTER);
            compositionFieldsPanel.add(row);
            compositionFields.add(field);
        }
        compositionFieldsPanel.revalidate();
        compositionFieldsPanel.repaint();
    }

    private JComponent buildButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, DarkTheme.SPACE_SM));
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(DarkTheme.SPACE_MD, DarkTheme.SPACE_LG - 2,
                DarkTheme.SPACE_LG - 2, DarkTheme.SPACE_LG - 2));

        busyBar = new BusyStatusBar();

        calculateButton = DarkTheme.primaryButton(buttonText());
        calculateButton.addActionListener(e -> onCalculateClicked());

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.add(calculateButton, BorderLayout.EAST);

        panel.add(busyBar,   BorderLayout.NORTH);
        panel.add(bottomRow, BorderLayout.CENTER);
        return panel;
    }

    private String buttonText() {
        switch (mode) {
            case STEP: return "Calculate STEP";
            case COARSE: return "Calculate Coarse Diagram";
            case MAP:
            default: return "Calculate MAP Diagram";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private JLabel addSectionLabel(JPanel panel, GridBagConstraints gbc, int row, String text) {
        return DarkTheme.addSectionRow(panel, gbc, row, text);
    }

    private JTextField addTextField(JPanel panel, GridBagConstraints gbc, int row,
                                    String label, String defaultValue) {
        return DarkTheme.addLabeledRow(panel, gbc, row, label, defaultValue);
    }

    private RangeField addRangeField(JPanel panel, GridBagConstraints gbc, int row,
                                     String label, String defaultValue) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(DarkTheme.fieldLabel(label), gbc);

        RangeField rf = new RangeField(defaultValue);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(rf, gbc);
        gbc.gridwidth = 1;
        return rf;
    }

    private JComboBox<String> addAxisTypeCombo(JPanel panel, GridBagConstraints gbc, int row,
                                               String label, String selected) {
        return addAxisTypeCombo(panel, gbc, row, label, selected,
                new String[]{"COMPOSITION", "TEMPERATURE", "PRESSURE"});
    }

    private JComboBox<String> addAxisTypeCombo(JPanel panel, GridBagConstraints gbc, int row,
                                               String label, String selected, String[] options) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(DarkTheme.fieldLabel(label), gbc);

        JComboBox<String> combo = DarkTheme.comboBox(options);
        combo.setSelectedItem(selected);
        combo.setEnabled(options.length > 1);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(combo, gbc);
        gbc.gridwidth = 1;
        return combo;
    }

    // ── Public API ─────────────────────────────────────────────────────

    public PhaseDiagramRequest buildRequest() {
        ui.request.DatabaseSelection sel = dbPanel.getSelection();

        PhaseDiagramRequest request = new PhaseDiagramRequest();
        request.setTdbFilePath(sel.getTdbPath() != null ? sel.getTdbPath() : "");
        request.setElements(sel.getElements());

        List<String> phases = getSelectedPhases();
        if (phases.isEmpty()) phases = sel.getAvailablePhases();
        request.setPhases(phases);

        request.setDiagramType(isStep ? PhaseDiagramRequest.DiagramType.STEP
                : mode == Mode.COARSE ? PhaseDiagramRequest.DiagramType.COARSE
                : PhaseDiagramRequest.DiagramType.MAP);

        List<String> els = sel.getElements();
        String firstElement = els != null && !els.isEmpty() ? els.get(0) : null;

        List<AxisConfig> axes = new ArrayList<>();
        AxisConfig axis0 = mode == Mode.COARSE
                ? buildCompositionAxisConfig(axis0Range, firstElement, 0)
                : buildAxisConfig(axis0TypeCombo.getSelectedItem().toString(), axis0Range, "Axis0");
        if (axis0 != null) axes.add(axis0);

        if (!isStep && mode != Mode.COARSE) {
            AxisConfig axis1 = buildAxisConfig(axis1TypeCombo.getSelectedItem().toString(), axis1Range, "Axis1");
            if (axis1 != null) axes.add(axis1);
        } else if (mode == Mode.COARSE) {
            boolean ternary = ternaryModeCheckBox.isSelected();
            request.setTernary(ternary);

            if (ternary && axis1ElementCombo.getSelectedItem() != null) {
                String axis1Element = (String) axis1ElementCombo.getSelectedItem();
                int axis1Index = compositionFieldsElements.indexOf(axis1Element);
                AxisConfig axis1 = buildCompositionAxisConfig(axis1Range, axis1Element, Math.max(axis1Index, 1));
                if (axis1 != null) axes.add(axis1);
            } else if (!ternary && axis1Range != null && axis1Range.isValid()) {
                axes.add(new AxisConfig("T / K", Type.TEMPERATURE,
                        axis1Range.getMin(), axis1Range.getMax(), axis1Range.getStep()));
            }
        }
        request.setAxes(axes);

        try { request.setFixedP(Double.parseDouble(pressureField.getText().trim())); }
        catch (NumberFormatException e) { request.setFixedP(101325.0); }

        if (isStep && startCompositionField != null) {
            try {
                double x2 = Double.parseDouble(startCompositionField.getText().trim());
                request.setStartComposition(new double[]{1.0 - x2, x2});
            } catch (NumberFormatException ignored) {}
        } else if (mode == Mode.COARSE) {
            if (temperatureField != null && !temperatureField.getText().trim().isEmpty()) {
                try { request.setFixedT(Double.parseDouble(temperatureField.getText().trim())); }
                catch (NumberFormatException ignored) {}
            }
            request.setStartComposition(readCoarseStartComposition(sel.getElements().size()));
        } else if (mode == Mode.MAP) {
            if (startTemperatureField != null && axis0 != null && axis0.type == Type.TEMPERATURE) {
                try {
                    request.setStartAxisValue(0, Double.parseDouble(startTemperatureField.getText().trim()));
                } catch (NumberFormatException ignored) {}
            }
            if (startCompositionMapField != null) {
                try {
                    double x2 = Double.parseDouble(startCompositionMapField.getText().trim());
                    request.setStartComposition(new double[]{1.0 - x2, x2});
                } catch (NumberFormatException ignored) {}
            }
        }
        return request;
    }

    /**
     * Reads the COARSE-mode per-element composition fields (see
     * {@link #populateCoarseElementUI}), falling back to a uniform
     * composition over {@code numElements} components if the fields
     * haven't been populated yet or don't match -- {@link
     * application.ApplicationLayer#calculateCoarseBinaryDiagram}/
     * {@code calculateCoarseTernaryDiagram} both require a non-null
     * composition vector to renormalize/distribute the non-swept
     * components against; values for axis-swept components are
     * overridden by the tracer anyway, so only the non-swept fields
     * actually matter here.
     */
    private double[] readCoarseStartComposition(int numElements) {
        if (numElements <= 0) {
            numElements = 1;
        }
        if (compositionFields.size() == numElements) {
            try {
                double[] comp = new double[numElements];
                for (int i = 0; i < numElements; i++) {
                    comp[i] = Double.parseDouble(compositionFields.get(i).getText().trim());
                }
                return comp;
            } catch (NumberFormatException ignored) {}
        }
        double[] uniform = new double[numElements];
        java.util.Arrays.fill(uniform, 1.0 / numElements);
        return uniform;
    }

    private AxisConfig buildAxisConfig(String typeStr, RangeField rf, String axisName) {
        if (rf == null || !rf.isValid()) return null;
        double min = rf.getMin(), max = rf.getMax(), step = rf.getStep();
        if ("TEMPERATURE".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (K)", Type.TEMPERATURE, min, max, step);
        if ("PRESSURE".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (Pa)", Type.PRESSURE, min, max, step);
        if ("COMPOSITION".equalsIgnoreCase(typeStr))
            return new AxisConfig(axisName + " (X)", 0, min, max, step);  // index 0 = first component
        return null;
    }

    /**
     * COARSE mode composition axis at a specific component index,
     * labeled by the actual element symbol (e.g. "x(Zr)" for V-Zr's
     * second, alphabetically-sorted element) rather than a generic
     * index -- {@code elementSymbol} is null-safe: falls back to
     * "x(comp N)" if the element list doesn't cover this index yet
     * (e.g. before a database is fully selected).
     */
    private AxisConfig buildCompositionAxisConfig(RangeField rf, String elementSymbol, int componentIndex) {
        if (rf == null || !rf.isValid()) return null;
        String label = elementSymbol != null ? "x(" + elementSymbol + ")" : "x(comp " + componentIndex + ")";
        return new AxisConfig(label, componentIndex, rf.getMin(), rf.getMax(), rf.getStep());
    }

    private void onCalculateClicked() {
        setStatus("Validating inputs...", DarkTheme.FG_SECOND);

        ui.request.DatabaseSelection sel = dbPanel.getSelection();
        if (!sel.hasTdb()) {
            setStatus("Error: TDB file required", DarkTheme.ERROR_COLOR); return;
        }
        if (!sel.hasElements()) {
            setStatus("Error: Elements required", DarkTheme.ERROR_COLOR); return;
        }
        if (!sel.hasPhases()) {
            setStatus("Error: No phases available", DarkTheme.ERROR_COLOR); return;
        }

        if (onCalculate != null) onCalculate.run();
    }

    public void setCalculateCallback(Runnable callback) { this.onCalculate = callback; }

    public void setAbortCallback(Runnable callback) { busyBar.setAbortCallback(callback); }

    /** Binds this panel's shared database/element/phase state to {@code context}. */
    public void bindContext(GuiCalculationContext context) {
        this.context = context;
        dbPanel.bindContext(context);
        populatePhases(context.getSelection().getAvailablePhases());
    }

    /** Refreshes fields from the bound context; call when this activity becomes visible. */
    public void onActivityShown() {
        if (context == null) return;
        dbPanel.syncFromContext();
        populatePhases(context.getSelection().getAvailablePhases());
    }

    /** Disables the Calculate button and shows the busy indicator/Abort while a calculation runs. */
    public void setRunning(boolean running) {
        calculateButton.setEnabled(!running);
        busyBar.setRunning(running, "Calculating…");
    }

    public void setStatus(String message, Color color) {
        busyBar.setStatus(message, color);
    }
}
