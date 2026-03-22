package ui.gui;

import service.DatabaseSelection;
import service.PropertyScanRequest;
import service.PropertyScanRequest.AxisType;
import service.PropertyScanRequest.ScanType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar configuration panel for STEP and MAP property scan calculations.
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Method, phase selector (badge-style), axis config(s), fixed conditions, Calculate button.
 */
public class PropertyCalcConfigPanel extends JPanel {

    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 10);
    private static final Font LABEL_FONT   = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FIELD_FONT   = new Font("Consolas", Font.PLAIN, 10);
    private static final Font HINT_FONT    = new Font("Segoe UI", Font.PLAIN,  9);
    private static final Font BADGE_FONT   = new Font("Consolas", Font.BOLD,  10);

    private final boolean isMap;
    private final DatabaseExtractionPanel dbPanel;

    private JComboBox<String> methodCombo;

    // Phase selector (badge-style, same as SinglePointSidebarPanel)
    private JTextField phaseInputField;
    private JPanel     phaseConfirmedPanel;
    private JLabel     phaseHintLabel;
    private List<String> availablePhases = new ArrayList<>();

    // Axis 0
    private JComboBox<String> axis0TypeCombo;
    private RangeField axis0Range;

    // Axis 1 (MAP only)
    private JComboBox<String> axis1TypeCombo;
    private RangeField axis1Range;

    // Fixed conditions
    private JTextField fixedPField, fixedTField, fixedXField;

    private JLabel statusLabel;
    private JButton calcBtn;
    private Runnable onCalculate;
    private Runnable onAbort;

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

    // ── Database/element selection callback ───────────────────────────

    private void onSelectionChanged(DatabaseSelection sel) {
        availablePhases = sel.hasPhases() ? sel.getAvailablePhases() : new ArrayList<>();
        // Remove any now-invalid badges
        List<String> current = getSelectedPhases();
        phaseConfirmedPanel.removeAll();
        if (!availablePhases.isEmpty()) {
            for (String p : current) {
                if (availablePhases.contains(p)) addPhaseBadge(p);
            }
        } else {
            for (String p : current) addPhaseBadge(p);
        }
        phaseConfirmedPanel.revalidate();
        phaseConfirmedPanel.repaint();
    }

    // ── Phase selector logic ──────────────────────────────────────────

    private void onAddPhase() {
        String raw = phaseInputField.getText().trim().toUpperCase();
        if (raw.isEmpty()) return;

        // Validate against available phases
        if (!availablePhases.isEmpty() && !availablePhases.contains(raw)) {
            String hint = suggestPhase(raw);
            phaseHintLabel.setText("Unknown: " + raw + (hint != null ? "  Try: " + hint : ""));
            phaseHintLabel.setForeground(DarkTheme.ERROR_COLOR);
            phaseInputField.setForeground(DarkTheme.ERROR_COLOR);
            return;
        }

        // Check duplicate
        if (getSelectedPhases().contains(raw)) {
            phaseInputField.setText("");
            return;
        }

        addPhaseBadge(raw);
        phaseInputField.setText("");
        phaseInputField.setForeground(DarkTheme.FG_PRIMARY);
        phaseHintLabel.setText("Type phase name, Enter or Add");
        phaseHintLabel.setForeground(DarkTheme.FG_SECOND);
        phaseConfirmedPanel.revalidate();
        phaseConfirmedPanel.repaint();
    }

    private void onTabComplete(KeyEvent e) {
        String prefix = phaseInputField.getText().trim().toUpperCase();
        if (prefix.isEmpty()) return;
        for (String p : availablePhases) {
            if (p.startsWith(prefix) && !p.equals(prefix)) {
                phaseInputField.setText(p);
                phaseInputField.setForeground(DarkTheme.FG_PRIMARY);
                e.consume();
                return;
            }
        }
    }

    private String suggestPhase(String input) {
        for (String p : availablePhases) {
            if (p.startsWith(input) || p.contains(input)) return p;
        }
        return null;
    }

    private void addPhaseBadge(String phase) {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        badge.setBackground(DarkTheme.BG_INPUT);
        badge.setBorder(BorderFactory.createLineBorder(DarkTheme.ACCENT, 1));

        JLabel nameLbl = new JLabel(phase);
        nameLbl.setFont(BADGE_FONT);
        nameLbl.setForeground(DarkTheme.ACCENT);
        badge.add(nameLbl);

        JLabel removeLbl = new JLabel("×");
        removeLbl.setFont(BADGE_FONT);
        removeLbl.setForeground(DarkTheme.FG_SECOND);
        removeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeLbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent ev) {
                phaseConfirmedPanel.remove(badge);
                phaseConfirmedPanel.revalidate();
                phaseConfirmedPanel.repaint();
            }
        });
        badge.add(removeLbl);
        phaseConfirmedPanel.add(badge);
    }

    public List<String> getSelectedPhases() {
        List<String> result = new ArrayList<>();
        for (Component c : phaseConfirmedPanel.getComponents()) {
            if (c instanceof JPanel) {
                for (Component inner : ((JPanel) c).getComponents()) {
                    if (inner instanceof JLabel && !((JLabel) inner).getText().equals("×")) {
                        result.add(((JLabel) inner).getText());
                        break;
                    }
                }
            }
        }
        return result;
    }

    // ── Layout ────────────────────────────────────────────────────────

    private JPanel buildLower() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(DarkTheme.SIDEBAR_BG);
        p.setBorder(new EmptyBorder(6, 10, 10, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill   = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // ── Method ─────────────────────────────────────────────────
        row = addSection(p, g, row, "METHOD");
        addLabel(p, g, row, "Type");
        methodCombo = new JComboBox<>(new String[]{"HM", "Gm", "G"});
        methodCombo.setBackground(DarkTheme.BG_INPUT);
        methodCombo.setForeground(DarkTheme.FG_PRIMARY);
        methodCombo.setRenderer(new DarkTheme.ComboRenderer());
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(methodCombo, g);
        g.gridwidth = 1;
        row++;

        // ── Phases (badge-style) ─────────────────────────────────────
        row = addSection(p, g, row, "PHASES");

        phaseHintLabel = new JLabel("Type phase name, Enter or Add");
        phaseHintLabel.setFont(HINT_FONT);
        phaseHintLabel.setForeground(DarkTheme.FG_SECOND);
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        p.add(phaseHintLabel, g);
        g.gridwidth = 1;
        row++;

        // Input + Add button
        phaseInputField = new JTextField();
        phaseInputField.setBackground(DarkTheme.BG_INPUT);
        phaseInputField.setForeground(DarkTheme.FG_PRIMARY);
        phaseInputField.setCaretColor(DarkTheme.FG_PRIMARY);
        phaseInputField.setFont(FIELD_FONT);
        phaseInputField.setToolTipText("Type phase name (Tab to autocomplete)");
        phaseInputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onAddPhase();
                if (e.getKeyCode() == KeyEvent.VK_TAB)  onTabComplete(e);
            }
        });
        g.gridx = 0; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(phaseInputField, g);

        JButton addBtn = smallButton("Add");
        addBtn.addActionListener(e -> onAddPhase());
        g.gridx = 2; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(addBtn, g);
        row++;

        // Badge panel
        phaseConfirmedPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        phaseConfirmedPanel.setOpaque(false);
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        p.add(phaseConfirmedPanel, g);
        g.gridwidth = 1;
        row++;

        // ── Axis 0 ──────────────────────────────────────────────────
        row = addSection(p, g, row, isMap ? "AXIS 0  (X)" : "SCAN AXIS");
        axis0TypeCombo = axisTypeCombo(p, g, row++, isMap ? "COMPOSITION" : "TEMPERATURE");
        axis0Range = rangeField(p, g, row++, "Range", isMap ? "0.0, 1.0, 0.1" : "500, 1500, 100");
        axis0TypeCombo.addItemListener(e -> axis0Range.setText(
                "COMPOSITION".equals(axis0TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "500, 1500, 100"));

        // ── Axis 1 (MAP) ─────────────────────────────────────────────
        if (isMap) {
            row = addSection(p, g, row, "AXIS 1  (Y)");
            axis1TypeCombo = axisTypeCombo(p, g, row++, "TEMPERATURE");
            axis1Range = rangeField(p, g, row++, "Range", "500, 1500, 100");
            axis1TypeCombo.addItemListener(e -> axis1Range.setText(
                    "COMPOSITION".equals(axis1TypeCombo.getSelectedItem()) ? "0.0, 1.0, 0.1" : "500, 1500, 100"));
        }

        // ── Fixed conditions ─────────────────────────────────────────
        row = addSection(p, g, row, "FIXED CONDITIONS");
        fixedPField = textField(p, g, row++, "Pressure (Pa)", "101325.0");
        if (!isMap) {
            fixedTField = textField(p, g, row++, "Fixed T (K)",  "1000.0");
            fixedXField = textField(p, g, row++, "Fixed x(2)",  "0.5");
        }

        // Filler
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weighty = 1;
        p.add(Box.createVerticalGlue(), g);
        return p;
    }

    private JComponent buildButtonRow() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(DarkTheme.SIDEBAR_BG);
        p.setBorder(new EmptyBorder(8, 10, 10, 10));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusLabel.setForeground(DarkTheme.FG_SECOND);

        calcBtn = new JButton(isMap ? "Calculate MAP" : "Calculate STEP");
        calcBtn.setBackground(DarkTheme.ACCENT);
        calcBtn.setForeground(Color.WHITE);
        calcBtn.setFocusPainted(false);
        calcBtn.setBorderPainted(false);
        calcBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        calcBtn.setMargin(new Insets(8, 16, 8, 16));
        calcBtn.addActionListener(e -> {
            if (calcBtn.getText().startsWith("Abort")) {
                if (onAbort != null) onAbort.run();
            } else {
                fireCalculate();
            }
        });

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(statusLabel, BorderLayout.WEST);
        row.add(calcBtn, BorderLayout.EAST);
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private int addSection(JPanel p, GridBagConstraints g, int row, String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(SECTION_FONT);
        lbl.setForeground(DarkTheme.SECTION_FG);
        lbl.setBorder(new EmptyBorder(4, 0, 2, 0));
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        p.add(lbl, g);
        g.gridwidth = 1;
        return row + 1;
    }

    private void addLabel(JPanel p, GridBagConstraints g, int row, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(LABEL_FONT);
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(lbl, g);
    }

    private JTextField textField(JPanel p, GridBagConstraints g, int row,
                                 String label, String def) {
        addLabel(p, g, row, label);
        JTextField f = new JTextField(def);
        f.setBackground(DarkTheme.BG_INPUT);
        f.setForeground(DarkTheme.FG_PRIMARY);
        f.setCaretColor(DarkTheme.FG_PRIMARY);
        f.setFont(FIELD_FONT);
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(f, g);
        g.gridwidth = 1;
        return f;
    }

    private JComboBox<String> axisTypeCombo(JPanel p, GridBagConstraints g, int row, String sel) {
        addLabel(p, g, row, "Type");
        JComboBox<String> c = new JComboBox<>(new String[]{"COMPOSITION", "TEMPERATURE"});
        c.setSelectedItem(sel);
        c.setBackground(DarkTheme.BG_INPUT);
        c.setForeground(DarkTheme.FG_PRIMARY);
        c.setRenderer(new DarkTheme.ComboRenderer());
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        p.add(c, g);
        g.gridwidth = 1;
        return c;
    }

    private JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setFont(LABEL_FONT);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setBackground(DarkTheme.BG_INPUT);
        b.setForeground(DarkTheme.FG_PRIMARY);
        b.setOpaque(true);
        return b;
    }

    private RangeField rangeField(JPanel p, GridBagConstraints g, int row,
                                  String label, String defaults) {
        addLabel(p, g, row, label);
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
        service.DatabaseSelection sel = dbPanel.getSelection();

        PropertyScanRequest req = new PropertyScanRequest();
        req.setTdbFilePath(sel.getTdbPath() != null ? sel.getTdbPath() : "");
        req.setElements(sel.getElements());
        List<String> phases = getSelectedPhases();
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
    public void setAbortCallback(Runnable r)                 { this.onAbort = r; }
    public void setStatus(String msg, Color color)           { statusLabel.setText(msg); statusLabel.setForeground(color); }

    /** Switch button between Calculate and Abort modes. */
    public void setRunning(boolean running) {
        if (running) {
            calcBtn.setText("Abort");
            calcBtn.setBackground(DarkTheme.ERROR_COLOR);
        } else {
            calcBtn.setText(isMap ? "Calculate MAP" : "Calculate STEP");
            calcBtn.setBackground(DarkTheme.ACCENT);
        }
    }

    private void fireCalculate() {
        service.DatabaseSelection sel = dbPanel.getSelection();
        if (!sel.hasTdb())      { setStatus("Error: TDB required",      DarkTheme.ERROR_COLOR); return; }
        if (!sel.hasElements()) { setStatus("Error: Elements required",  DarkTheme.ERROR_COLOR); return; }
        if (!sel.hasPhases())   { setStatus("Error: No phases",          DarkTheme.ERROR_COLOR); return; }
        setStatus("Calculating...", DarkTheme.ACCENT);
        if (onCalculate != null) onCalculate.run();
    }
}
