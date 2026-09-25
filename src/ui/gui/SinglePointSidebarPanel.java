package ui.gui;

import ui.request.DatabaseSelection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar panel for single-point thermodynamic calculations.
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Phase selector, Method, T, P, Composition + Run/Reset.
 */
public class SinglePointSidebarPanel extends JPanel {

    private final DatabaseExtractionPanel dbPanel;

    private TagInputField phasesField;

    private JComboBox<String> methodCombo;
    private JTextField        temperatureField;
    private JTextField        pressureField;
    private JTextField        compositionField;
    private JLabel            inputStatusLabel;
    private BusyStatusBar     busyBar;
    private JButton           runBtn;

    private Runnable runCallback;
    private Runnable resetCallback;

    private GuiCalculationContext context;

    public SinglePointSidebarPanel(MainController controller) {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.SIDEBAR_BG);

        dbPanel = new DatabaseExtractionPanel(controller);
        dbPanel.setDefaults("data/tizr_kum_cvm.tdb", List.of("TI", "ZR"));
        dbPanel.setOnSelectionChanged(this::onSelectionChanged);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(dbPanel, BorderLayout.CENTER);
        top.add(DarkTheme.separator(), BorderLayout.SOUTH);

        add(top,               BorderLayout.NORTH);
        add(buildLowerContent(), BorderLayout.CENTER);
    }

    /** Binds this panel's shared database/element/phase state to {@code context}. */
    public void bindContext(GuiCalculationContext context) {
        this.context = context;
        dbPanel.bindContext(context);
        phasesField.setKnownValues(context.getSelection().getAvailablePhases());
        phasesField.clear();
        for (String p : context.getSelectedPhases()) phasesField.addKnownSelectedValue(p);
    }

    /** Refreshes fields from the bound context; call when this activity becomes visible. */
    public void onActivityShown() {
        if (context == null) return;
        dbPanel.syncFromContext();
        phasesField.setKnownValues(context.getSelection().getAvailablePhases());
        phasesField.clear();
        for (String p : context.getSelectedPhases()) phasesField.addKnownSelectedValue(p);
    }

    private JPanel buildLowerContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(DarkTheme.SPACE_SM + 2, DarkTheme.SPACE_LG - 2,
                DarkTheme.SPACE_LG - 2, DarkTheme.SPACE_LG - 2));

        GridBagConstraints gbc = DarkTheme.formGbc();
        int row = 0;

        // ── PHASES ────────────────────────────────────────────────────
        DarkTheme.addSectionRow(panel, gbc, row++, "PHASES");

        phasesField = new TagInputField("Select one or more phases", true);
        phasesField.setOnChanged(() -> { if (context != null) context.setSelectedPhases(phasesField.getValues()); });
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(phasesField, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── CONDITIONS ────────────────────────────────────────────────
        DarkTheme.addSectionRow(panel, gbc, row++, "CONDITIONS");
        temperatureField = DarkTheme.addLabeledRow(panel, gbc, row++, "T (K)",  "500.0");
        pressureField    = DarkTheme.addLabeledRow(panel, gbc, row++, "P (Pa)", "10000.0");
        compositionField = DarkTheme.addLabeledRow(panel, gbc, row++, "Composition", "0.333,0.333,0.334");

        methodCombo = DarkTheme.comboBox(new String[]{"Gm"});

        // ── STATUS ────────────────────────────────────────────────────
        inputStatusLabel = new JLabel(" ");
        inputStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        inputStatusLabel.setForeground(DarkTheme.FG_SECOND);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(inputStatusLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── ACTIONS ───────────────────────────────────────────────────
        runBtn = DarkTheme.primaryButton("Run Calculation");
        runBtn.addActionListener(e -> { if (runCallback != null) runCallback.run(); });

        JButton resetBtn = DarkTheme.smallButton("Reset Calculation Inputs");
        resetBtn.addActionListener(e -> {
            resetCalculationInputs();
            if (resetCallback != null) resetCallback.run();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, DarkTheme.SPACE_MD, 0));
        actions.setOpaque(false);
        actions.add(runBtn);
        actions.add(resetBtn);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        panel.add(actions, gbc);
        row++;

        busyBar = new BusyStatusBar();
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(busyBar, gbc);
        gbc.gridwidth = 1;
        row++;

        // Filler
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    // ── Respond to database/element selection changes ─────────────────

    private void onSelectionChanged(DatabaseSelection sel) {
        List<String> phases = sel.hasPhases() ? sel.getAvailablePhases() : new ArrayList<>();
        phasesField.setKnownValues(phases);
        phasesField.pruneInvalid();
    }

    // ── Public API ─────────────────────────────────────────────────────

    public void setRunCallback(Runnable r)    { this.runCallback = r; }
    public void setResetCallback(Runnable r)  { this.resetCallback = r; }

    public String getTdbPath() {
        String p = dbPanel.getSelection().getTdbPath();
        return p != null ? p : "";
    }

    public String[] getElements() {
        List<String> elems = dbPanel.getSelection().getElements();
        return elems.toArray(new String[0]);
    }

    public List<String> getSelectedPhases() { return phasesField.getValues(); }
    public String[] getPhases() { return getSelectedPhases().toArray(new String[0]); }
    public String   getMethod() { return (String) methodCombo.getSelectedItem(); }

    public double getTemperature() {
        try { return Double.parseDouble(temperatureField.getText().trim()); }
        catch (NumberFormatException e) { return 500.0; }
    }

    public double getPressure() {
        try { return Double.parseDouble(pressureField.getText().trim()); }
        catch (NumberFormatException e) { return 10000.0; }
    }

    public ArrayList<ArrayList<Double>> getCompositions() {
        String[] parts = compositionField.getText().trim().split("\\s*,\\s*");
        ArrayList<Double> row = new ArrayList<>();
        for (String p : parts) {
            try { row.add(Double.parseDouble(p)); } catch (NumberFormatException e) { row.add(0.0); }
        }
        ArrayList<ArrayList<Double>> out = new ArrayList<>();
        out.add(row);
        return out;
    }

    public void setStatus(String msg, Color color) {
        inputStatusLabel.setText(msg);
        inputStatusLabel.setForeground(color);
    }

    /** Disables the Run button and shows the busy indicator while a calculation runs. */
    public void setRunning(boolean running) {
        runBtn.setEnabled(!running);
        busyBar.setRunning(running, "Running…");
    }

    /** Resets method/T/P/composition to their defaults; leaves the shared database/elements/phases context untouched. */
    public void resetCalculationInputs() {
        methodCombo.setSelectedItem("Gm");
        temperatureField.setText("500.0");
        pressureField.setText("10000.0");
        compositionField.setText("0.333,0.333,0.334");
        inputStatusLabel.setText(" ");
    }

    /** Pre-fills the database/element text fields; used by the initial default and "Open TDB...", not by Reset. */
    public void resetDefaults(String defaultTdb) {
        dbPanel.setDefaults(defaultTdb, List.of("TI", "ZR"));
    }

    public List<String> validateAll() {
        List<String> errors = new ArrayList<>();
        if (!dbPanel.getSelection().hasTdb()) errors.add("No database loaded");
        if (dbPanel.getSelection().getElements().isEmpty()) errors.add("No elements selected");
        if (getSelectedPhases().isEmpty()) errors.add("Select at least 1 phase");
        try { if (getTemperature() <= 0) errors.add("Temperature must be > 0 K"); }
        catch (Exception e) { errors.add("Invalid temperature"); }
        try { if (getPressure() < 0) errors.add("Pressure must be >= 0"); }
        catch (Exception e) { errors.add("Invalid pressure"); }
        if (!isValidComposition(compositionField.getText().trim()))
            errors.add("Composition must sum to ~1.0");
        return errors;
    }

    private boolean isValidComposition(String text) {
        try {
            double sum = 0;
            for (String p : text.trim().split("\\s*,\\s*")) {
                double v = Double.parseDouble(p);
                if (v < 0 || v > 1) return false;
                sum += v;
            }
            return Math.abs(sum - 1.0) <= 0.05;
        } catch (Exception e) { return false; }
    }
}
