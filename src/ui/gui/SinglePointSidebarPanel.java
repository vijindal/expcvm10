package ui.gui;

import ui.request.DatabaseSelection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar panel for single-point thermodynamic calculations.
 *
 * Top zone  : shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Lower zone: Phase selector, Method, T, P, Composition + Run/Reset.
 */
public class SinglePointSidebarPanel extends JPanel {

    private static final Font SECTION_FONT = new Font("Segoe UI", Font.BOLD, 10);
    private static final Font LABEL_FONT   = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FIELD_FONT   = new Font("Consolas", Font.PLAIN, 11);
    private static final Font HINT_FONT    = new Font("Segoe UI", Font.PLAIN,  9);
    private static final Font BADGE_FONT   = new Font("Consolas", Font.BOLD,  10);

    // ── Shared extraction panel ───────────────────────────────────────
    private final DatabaseExtractionPanel dbPanel;

    // ── Phase selector ────────────────────────────────────────────────
    private JTextField phaseInputField;
    private JPanel     phaseConfirmedPanel;
    private JLabel     phaseHintLabel;      // "HM: 1 phase | Gm/G: 1+ phases"

    // ── Activity-specific fields ──────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JTextField        temperatureField;
    private JTextField        pressureField;
    private JTextField        compositionField;
    private JLabel            inputStatusLabel;

    // ── Callbacks ─────────────────────────────────────────────────────
    private Runnable runCallback;
    private Runnable resetCallback;

    // ── State ─────────────────────────────────────────────────────────
    private List<String> availablePhases = new ArrayList<>();

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

    private JPanel buildLowerContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(6, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // ── METHOD ────────────────────────────────────────────────────
        row = addSection(panel, gbc, row, "METHOD");
        addLabel(panel, gbc, row, 0, "Method");
        methodCombo = new JComboBox<>(new String[]{"HM", "Gm", "G"});
        methodCombo.setBackground(DarkTheme.BG_INPUT);
        methodCombo.setForeground(DarkTheme.FG_PRIMARY);
        methodCombo.setRenderer(new DarkTheme.ComboRenderer());
        methodCombo.addActionListener(e -> updatePhaseHint());
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(methodCombo, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── PHASES ────────────────────────────────────────────────────
        row = addSection(panel, gbc, row, "PHASES");

        // Phase hint (tells user how many phases this method needs)
        phaseHintLabel = new JLabel(methodPhaseHint("HM"));
        phaseHintLabel.setFont(HINT_FONT);
        phaseHintLabel.setForeground(DarkTheme.FG_SECOND);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(phaseHintLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // Phase input + Add button
        phaseInputField = makeField("");
        phaseInputField.setToolTipText("Type phase name, e.g. LIQUID  (Tab to autocomplete)");
        phaseInputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onAddPhase();
                if (e.getKeyCode() == KeyEvent.VK_TAB)  onTabComplete(e);
            }
        });
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(phaseInputField, gbc);

        JButton addPhaseBtn = smallButton("Add");
        addPhaseBtn.addActionListener(e -> onAddPhase());
        gbc.gridx = 2; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(addPhaseBtn, gbc);
        row++;

        // Phase badge panel
        phaseConfirmedPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        phaseConfirmedPanel.setOpaque(false);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(phaseConfirmedPanel, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── CONDITIONS ────────────────────────────────────────────────
        row = addSection(panel, gbc, row, "CONDITIONS");
        temperatureField = addLabeledField(panel, gbc, row++, "T (K)",  "500.0");
        pressureField    = addLabeledField(panel, gbc, row++, "P (Pa)", "10000.0");
        compositionField = addLabeledField(panel, gbc, row++, "Composition", "0.333,0.333,0.334");

        // ── STATUS ────────────────────────────────────────────────────
        inputStatusLabel = new JLabel(" ");
        inputStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        inputStatusLabel.setForeground(DarkTheme.FG_SECOND);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(inputStatusLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // ── ACTIONS ───────────────────────────────────────────────────
        JButton runBtn   = actionButton("Run Calculation", DarkTheme.ACCENT, Color.WHITE);
        runBtn.addActionListener(e -> { if (runCallback != null) runCallback.run(); });

        JButton resetBtn = smallButton("Reset");
        resetBtn.addActionListener(e -> { if (resetCallback != null) resetCallback.run(); });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(runBtn);
        actions.add(resetBtn);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        panel.add(actions, gbc);
        row++;

        // Filler
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    // ── Phase selector logic ──────────────────────────────────────────

    private void onAddPhase() {
        String raw = phaseInputField.getText().trim().toUpperCase();
        if (raw.isEmpty()) return;

        // For HM method: only one phase allowed
        if ("HM".equals(methodCombo.getSelectedItem()) && getSelectedPhases().size() >= 1) {
            inputStatusLabel.setText("HM uses exactly 1 phase — remove existing first");
            inputStatusLabel.setForeground(DarkTheme.ERROR_COLOR);
            return;
        }

        // Validate against available phases
        if (!availablePhases.isEmpty() && !availablePhases.contains(raw)) {
            // Show close matches as hint
            String hint = suggestPhase(raw);
            inputStatusLabel.setText("Unknown phase: " + raw + (hint != null ? "  Did you mean: " + hint : ""));
            inputStatusLabel.setForeground(DarkTheme.ERROR_COLOR);
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
        inputStatusLabel.setText(" ");
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

    private void updatePhaseHint() {
        phaseHintLabel.setText(methodPhaseHint((String) methodCombo.getSelectedItem()));
    }

    private String methodPhaseHint(String method) {
        if ("HM".equals(method))  return "HM: exactly 1 phase required";
        if ("Gm".equals(method))  return "Gm: 1 or more phases";
        return "G: 1 or more phases";
    }

    // ── Respond to database/element selection changes ─────────────────

    private void onSelectionChanged(DatabaseSelection sel) {
        availablePhases = sel.hasPhases() ? sel.getAvailablePhases() : new ArrayList<>();
        // Clear any now-invalid phase badges
        List<String> current = getSelectedPhases();
        if (!availablePhases.isEmpty()) {
            phaseConfirmedPanel.removeAll();
            for (String p : current) {
                if (availablePhases.contains(p)) addPhaseBadge(p);
            }
            phaseConfirmedPanel.revalidate();
            phaseConfirmedPanel.repaint();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private int addSection(JPanel panel, GridBagConstraints gbc, int row, String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(SECTION_FONT);
        lbl.setForeground(DarkTheme.SECTION_FG);
        lbl.setBorder(new EmptyBorder(4, 0, 2, 0));
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(lbl, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private void addLabel(JPanel panel, GridBagConstraints gbc, int row, int col, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(LABEL_FONT);
        gbc.gridx = col; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(lbl, gbc);
    }

    private JTextField addLabeledField(JPanel panel, GridBagConstraints gbc, int row,
                                       String label, String def) {
        addLabel(panel, gbc, row, 0, label);
        JTextField f = makeField(def);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1; gbc.gridwidth = 2;
        panel.add(f, gbc);
        gbc.gridwidth = 1;
        return f;
    }

    private JTextField makeField(String def) {
        JTextField f = new JTextField(def, 18);
        f.setFont(FIELD_FONT);
        f.setBackground(DarkTheme.BG_INPUT);
        f.setForeground(DarkTheme.FG_PRIMARY);
        f.setCaretColor(DarkTheme.FG_PRIMARY);
        f.setSelectionColor(DarkTheme.SEL_BG);
        return f;
    }

    private JButton actionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        return btn;
    }

    private JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(LABEL_FONT);
        btn.setMargin(new Insets(2, 8, 2, 8));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(DarkTheme.BG_INPUT);
        btn.setForeground(DarkTheme.FG_PRIMARY);
        btn.setOpaque(true);
        return btn;
    }

    // ── Public API ─────────────────────────────────────────────────────

    public void setRunCallback(Runnable r)    { this.runCallback = r; }
    public void setResetCallback(Runnable r)  { this.resetCallback = r; }

    public void setBrowseCallback(Runnable r) { /* handled by DatabaseExtractionPanel */ }

    public String getTdbPath() {
        String p = dbPanel.getSelection().getTdbPath();
        return p != null ? p : "";
    }

    public JTextField getTdbPathField() { return null; }

    public String[] getElements() {
        List<String> elems = dbPanel.getSelection().getElements();
        return elems.toArray(new String[0]);
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

    public void setAvailableLists(List<String> elements, List<String> phases) {
        // No-op: handled by DatabaseExtractionPanel
    }

    public void setStatus(String msg, Color color) {
        inputStatusLabel.setText(msg);
        inputStatusLabel.setForeground(color);
    }

    public void resetDefaults(String defaultTdb) {
        dbPanel.setDefaults(defaultTdb, List.of("TI", "ZR"));
        methodCombo.setSelectedItem("HM");
        phaseConfirmedPanel.removeAll();
        phaseConfirmedPanel.revalidate();
        phaseConfirmedPanel.repaint();
        temperatureField.setText("500.0");
        pressureField.setText("10000.0");
        compositionField.setText("0.333,0.333,0.334");
        inputStatusLabel.setText(" ");
    }

    public List<String> validateAll() {
        List<String> errors = new ArrayList<>();
        if (!dbPanel.getSelection().hasTdb()) errors.add("No database loaded");
        if (dbPanel.getSelection().getElements().isEmpty()) errors.add("No elements selected");
        if (getSelectedPhases().isEmpty()) errors.add("Select at least 1 phase");
        if ("HM".equals(getMethod()) && getSelectedPhases().size() > 1)
            errors.add("HM requires exactly 1 phase");
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

    // ── WrapLayout (inner copy — same as DatabaseExtractionPanel) ──────

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, true); }
        @Override public Dimension minimumLayoutSize(Container t) {
            Dimension d = layoutSize(t, false); d.width -= (getHgap() + 1); return d;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets ins = target.getInsets();
                int maxWidth = targetWidth - (ins.left + ins.right + hgap * 2);
                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > maxWidth) {
                        dim.height += rowH + vgap; dim.width = Math.max(dim.width, rowW);
                        rowW = 0; rowH = 0;
                    }
                    if (rowW != 0) rowW += hgap;
                    rowW += d.width; rowH = Math.max(rowH, d.height);
                }
                dim.height += rowH; dim.width = Math.max(dim.width, rowW);
                dim.width  += ins.left + ins.right + hgap * 2;
                dim.height += ins.top  + ins.bottom + vgap * 2;
                return dim;
            }
        }
    }
}
