package ui.gui;

import ui.request.DatabaseSelection;
import ui.request.PropertyScanRequest;
import ui.request.PropertyScanRequest.AxisType;
import ui.request.PropertyScanRequest.ScanType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Sidebar configuration panel for STEP and MAP property scan calculations.
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Method, phase selector, axis config(s), fixed conditions, Calculate button.
 */
public class PropertyCalcConfigPanel extends JPanel {

    private final boolean isMap;
    private final DatabaseExtractionPanel dbPanel;

    private JComboBox<String> methodCombo;
    private TagInputField phasesField;

    private JComboBox<String> axis0TypeCombo;
    private RangeField axis0Range;

    private JComboBox<String> axis1TypeCombo;
    private RangeField axis1Range;

    private JTextField fixedPField, fixedTField, fixedXField;

    private BusyStatusBar busyBar;
    private JButton calcBtn;
    private Runnable onCalculate;

    private GuiCalculationContext context;

    public PropertyCalcConfigPanel(MainController controller, boolean isMap) {
        this.isMap = isMap;
        setLayout(new BorderLayout());
        setBackground(DarkTheme.SIDEBAR_BG);

        dbPanel = new DatabaseExtractionPanel(controller);
        dbPanel.setDefaults("data/tizr_kum_cvm.tdb", List.of("TI", "ZR"));
        dbPanel.setOnSelectionChanged(this::onSelectionChanged);

        JPanel lower = buildLower();
        JScrollPane scroll = new JScrollPane(lower,
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
        add(buildButtonRow(), BorderLayout.SOUTH);
    }

    private void onSelectionChanged(DatabaseSelection sel) {
        List<String> phases = sel.hasPhases() ? sel.getAvailablePhases() : List.of();
        phasesField.setKnownValues(phases);
        phasesField.pruneInvalid();
    }

    /** Binds this panel's shared database/element/phase state to {@code context}. */
    public void bindContext(GuiCalculationContext context) {
        this.context = context;
        dbPanel.bindContext(context);
        syncPhasesFromContext();
    }

    /** Refreshes fields from the bound context; call when this activity becomes visible. */
    public void onActivityShown() {
        if (context == null) return;
        dbPanel.syncFromContext();
        syncPhasesFromContext();
    }

    private void syncPhasesFromContext() {
        phasesField.setKnownValues(context.getSelection().getAvailablePhases());
        phasesField.clear();
        for (String p : context.getSelectedPhases()) phasesField.addKnownSelectedValue(p);
    }

    // ── Layout ────────────────────────────────────────────────────────

    private JPanel buildLower() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(DarkTheme.SIDEBAR_BG);
        p.setBorder(new EmptyBorder(DarkTheme.SPACE_SM + 2, DarkTheme.SPACE_LG - 2,
                DarkTheme.SPACE_LG - 2, DarkTheme.SPACE_LG - 2));

        GridBagConstraints g = DarkTheme.formGbc();
        int row = 0;

        // ── Method ─────────────────────────────────────────────────
        DarkTheme.addSectionRow(p, g, row++, "METHOD");
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(DarkTheme.fieldLabel("Type"), g);
        methodCombo = DarkTheme.comboBox(new String[]{"Gm"});
        methodCombo.setEnabled(false);
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(methodCombo, g);
        g.gridwidth = 1;
        row++;

        // ── Phases ────────────────────────────────────────────────
        DarkTheme.addSectionRow(p, g, row++, "PHASES");
        phasesField = new TagInputField("Type phase name, Enter or Add", true);
        phasesField.setEmptyStateText("Empty = all available phases");
        phasesField.setOnChanged(() -> { if (context != null) context.setSelectedPhases(phasesField.getValues()); });
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        p.add(phasesField, g);
        g.gridwidth = 1;
        row++;

        // ── Axis 0 ──────────────────────────────────────────────────
        DarkTheme.addSectionRow(p, g, row++, isMap ? "AXIS 0  (X)" : "SCAN AXIS");
        axis0TypeCombo = axisTypeCombo(p, g, row++, isMap ? "COMPOSITION" : "TEMPERATURE");
        axis0Range = rangeField(p, g, row++, "Range", isMap ? "0.0, 1.0, 0.1" : "300, 2500, 100");
        axis0TypeCombo.addItemListener(e -> axis0Range.setText(
                "COMPOSITION".equals(axis0TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "300, 2500, 100"));

        // ── Axis 1 (MAP) ─────────────────────────────────────────────
        if (isMap) {
            DarkTheme.addSectionRow(p, g, row++, "AXIS 1  (Y)");
            axis1TypeCombo = axisTypeCombo(p, g, row++, "TEMPERATURE");
            axis1Range = rangeField(p, g, row++, "Range", "300, 2500, 100");
            axis1TypeCombo.addItemListener(e -> axis1Range.setText(
                    "COMPOSITION".equals(axis1TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "300, 2500, 100"));
        }

        // ── Fixed conditions ─────────────────────────────────────────
        DarkTheme.addSectionRow(p, g, row++, "FIXED CONDITIONS");
        fixedPField = DarkTheme.addLabeledRow(p, g, row++, "Pressure (Pa)", "101325.0");
        if (!isMap) {
            fixedTField = DarkTheme.addLabeledRow(p, g, row++, "Fixed T (K)",  "1000.0");
            fixedXField = DarkTheme.addLabeledRow(p, g, row++, "Fixed x(2)",  "0.5");
        } else {
            fixedXField = DarkTheme.addLabeledRow(p, g, row++, "Starting composition (CSV)", "0.5,0.5");
        }

        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weighty = 1;
        p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JComponent buildButtonRow() {
        JPanel p = new JPanel(new BorderLayout(0, DarkTheme.SPACE_SM));
        p.setBackground(DarkTheme.SIDEBAR_BG);
        p.setBorder(new EmptyBorder(DarkTheme.SPACE_MD, DarkTheme.SPACE_LG - 2,
                DarkTheme.SPACE_LG - 2, DarkTheme.SPACE_LG - 2));

        busyBar = new BusyStatusBar();

        calcBtn = DarkTheme.primaryButton(isMap ? "Calculate MAP" : "Calculate STEP");
        calcBtn.addActionListener(e -> fireCalculate());

        JPanel btnRow = new JPanel(new BorderLayout());
        btnRow.setOpaque(false);
        btnRow.add(calcBtn, BorderLayout.EAST);

        p.add(busyBar, BorderLayout.NORTH);
        p.add(btnRow,  BorderLayout.CENTER);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private JComboBox<String> axisTypeCombo(JPanel p, GridBagConstraints g, int row, String sel) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(DarkTheme.fieldLabel("Type"), g);
        JComboBox<String> c = DarkTheme.comboBox(new String[]{"COMPOSITION", "TEMPERATURE"});
        c.setSelectedItem(sel);
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(c, g);
        g.gridwidth = 1;
        return c;
    }

    private RangeField rangeField(JPanel p, GridBagConstraints g, int row, String label, String defaults) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(DarkTheme.fieldLabel(label), g);
        RangeField rf = new RangeField(defaults);
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(rf, g);
        g.gridwidth = 1;
        return rf;
    }

    private double parseDouble(JTextField f, double fallback) {
        try { return Double.parseDouble(f.getText().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private AxisType comboToAxisType(JComboBox<String> c) {
        return "TEMPERATURE".equals(c.getSelectedItem()) ? AxisType.TEMPERATURE : AxisType.COMPOSITION;
    }

    // ── Public API ────────────────────────────────────────────────────

    public PropertyScanRequest buildRequest() {
        ui.request.DatabaseSelection sel = dbPanel.getSelection();

        PropertyScanRequest req = new PropertyScanRequest();
        req.setTdbFilePath(sel.getTdbPath() != null ? sel.getTdbPath() : "");
        req.setElements(sel.getElements());
        List<String> phases = phasesField.getValues();
        req.setPhases(phases.isEmpty() ? sel.getAvailablePhases() : phases);
        req.setMethod(methodCombo.getSelectedItem().toString());
        req.setScanType(isMap ? ScanType.MAP : ScanType.STEP);

        req.setAxis0Type(comboToAxisType(axis0TypeCombo));
        req.setAxis0Min(axis0Range.getMinOrDefault(isMap ? 0.0 : 500.0));
        req.setAxis0Max(axis0Range.getMaxOrDefault(isMap ? 1.0 : 2000.0));
        req.setAxis0Step(axis0Range.getStepOrDefault(isMap ? 0.05 : 50.0));

        if (isMap) {
            req.setAxis1Type(comboToAxisType(axis1TypeCombo));
            req.setAxis1Min(axis1Range.getMinOrDefault(500.0));
            req.setAxis1Max(axis1Range.getMaxOrDefault(2000.0));
            req.setAxis1Step(axis1Range.getStepOrDefault(50.0));
        } else {
            req.setFixedT(parseDouble(fixedTField, 1000.0));
            req.setFixedX(parseDouble(fixedXField, 0.5));
        }
        req.setFixedP(parseDouble(fixedPField, 101325.0));
        return req;
    }

    public void setCalculateCallback(Runnable r)             { this.onCalculate = r; }
    public void setAbortCallback(Runnable r)                 { busyBar.setAbortCallback(r); }
    public void setStatus(String msg, Color color)           { busyBar.setStatus(msg, color); }

    /** Disables the Calculate button and shows the busy indicator/Abort while a calculation runs. */
    public void setRunning(boolean running) {
        calcBtn.setEnabled(!running);
        busyBar.setRunning(running, "Calculating…");
    }

    private void fireCalculate() {
        ui.request.DatabaseSelection sel = dbPanel.getSelection();
        if (!sel.hasTdb())      { setStatus("Error: TDB required",      DarkTheme.ERROR_COLOR); return; }
        if (!sel.hasElements()) { setStatus("Error: Elements required",  DarkTheme.ERROR_COLOR); return; }
        if (!sel.hasPhases())   { setStatus("Error: No phases",          DarkTheme.ERROR_COLOR); return; }
        setStatus("Calculating...", DarkTheme.ACCENT);
        if (onCalculate != null) onCalculate.run();
    }
}
