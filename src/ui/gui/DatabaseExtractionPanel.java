package ui.gui;

import service.DatabaseSelection;
import service.ModelInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared top-of-sidebar panel used by all non-assessment activities.
 *
 * Provides a guided three-step workflow:
 *   1. Select TDB database  (dropdown from data/ + Browse)
 *   2. Type element symbols (validated against loaded TDB, shown as badges)
 *   3. Phases auto-fetched  (display-only hint line)
 *
 * Callers embed this panel at the top of their sidebar and receive a
 * {@link DatabaseSelection} via the {@code onSelectionChanged} callback.
 */
public class DatabaseExtractionPanel extends JPanel {

    // ── Controller reference ─────────────────────────────────────────
    private final MainController controller;

    // ── Database section ─────────────────────────────────────────────
    private JComboBox<String> tdbCombo;
    private JLabel            dbStatusLabel;

    // ── Elements section ─────────────────────────────────────────────
    private JTextField elemInputField;
    private JLabel     availableHint;
    private JPanel     confirmedPanel;    // badge row
    private JLabel     phasesHint;        // "Phases: BCC_A2  HCP_A3 …"

    // ── State ────────────────────────────────────────────────────────
    private final DatabaseSelection selection = new DatabaseSelection();
    private Consumer<DatabaseSelection> onSelectionChanged;

    // ── Fonts ────────────────────────────────────────────────────────
    private static final Font F_LABEL  = new Font("Segoe UI",  Font.PLAIN, 11);
    private static final Font F_HINT   = new Font("Segoe UI",  Font.PLAIN,  9);
    private static final Font F_FIELD  = new Font("Consolas",  Font.PLAIN, 11);
    private static final Font F_SECT   = new Font("Segoe UI",  Font.BOLD,  10);
    private static final Font F_BADGE  = new Font("Consolas",  Font.BOLD,  10);

    // ─────────────────────────────────────────────────────────────────

    public DatabaseExtractionPanel(MainController controller) {
        this.controller = controller;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DarkTheme.SIDEBAR_BG);
        setBorder(new EmptyBorder(8, 10, 8, 10));

        add(buildDatabaseSection());
        add(Box.createVerticalStrut(10));
        add(buildElementsSection());
    }

    // ================================================================
    //  DATABASE section
    // ================================================================

    private JPanel buildDatabaseSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = baseGbc();

        // Section header
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3; g.weightx = 1;
        panel.add(sectionLabel("DATABASE"), g);
        g.gridwidth = 1;

        // Dropdown (auto-scanned from data/)
        tdbCombo = new JComboBox<>();
        tdbCombo.setFont(F_FIELD);
        tdbCombo.setBackground(DarkTheme.BG_INPUT);
        tdbCombo.setForeground(DarkTheme.FG_PRIMARY);
        tdbCombo.setRenderer(new DarkTheme.ComboRenderer());
        tdbCombo.setEditable(true);   // allow free-text path too
        ((JTextField) tdbCombo.getEditor().getEditorComponent())
                .setBackground(DarkTheme.BG_INPUT);
        ((JTextField) tdbCombo.getEditor().getEditorComponent())
                .setForeground(DarkTheme.FG_PRIMARY);
        populateTdbCombo();
        g.gridx = 0; g.gridy = 1; g.weightx = 1; g.gridwidth = 2;
        panel.add(tdbCombo, g);
        g.gridwidth = 1;

        // Browse button
        JButton browse = smallButton("…");
        browse.setToolTipText("Browse for TDB file");
        browse.addActionListener(e -> onBrowse());
        g.gridx = 2; g.gridy = 1; g.weightx = 0;
        panel.add(browse, g);

        // Status
        dbStatusLabel = hintLabel("No database loaded");
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1;
        panel.add(dbStatusLabel, g);
        g.gridwidth = 1;

        // Wire combo action (Enter or selection)
        tdbCombo.addActionListener(e -> {
            if ("comboBoxChanged".equals(e.getActionCommand())) onTdbSelected();
        });
        JTextField editorField = (JTextField) tdbCombo.getEditor().getEditorComponent();
        editorField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onTdbSelected();
            }
        });

        return panel;
    }

    // ================================================================
    //  ELEMENTS section
    // ================================================================

    private JPanel buildElementsSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = baseGbc();

        // Section header
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3; g.weightx = 1;
        panel.add(sectionLabel("ELEMENTS"), g);
        g.gridwidth = 1;

        // Input field + Add button
        elemInputField = new JTextField();
        elemInputField.setFont(F_FIELD);
        elemInputField.setBackground(DarkTheme.BG_INPUT);
        elemInputField.setForeground(DarkTheme.FG_PRIMARY);
        elemInputField.setCaretColor(DarkTheme.FG_PRIMARY);
        elemInputField.setToolTipText("Type element symbols separated by commas, e.g. TI,ZR");
        elemInputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onAddElements();
            }
        });
        g.gridx = 0; g.gridy = 1; g.weightx = 1; g.gridwidth = 2;
        panel.add(elemInputField, g);
        g.gridwidth = 1;

        JButton addBtn = smallButton("Add");
        addBtn.addActionListener(e -> onAddElements());
        g.gridx = 2; g.gridy = 1; g.weightx = 0;
        panel.add(addBtn, g);

        // Available hint
        availableHint = hintLabel("Available: —");
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1;
        panel.add(availableHint, g);

        // Confirmed badges panel (wrapping FlowLayout)
        confirmedPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        confirmedPanel.setOpaque(false);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 3; g.weightx = 1;
        panel.add(confirmedPanel, g);

        // Phases hint
        phasesHint = hintLabel("Phases: —");
        g.gridx = 0; g.gridy = 4; g.gridwidth = 3; g.weightx = 1;
        panel.add(phasesHint, g);
        g.gridwidth = 1;

        return panel;
    }

    // ================================================================
    //  Event handlers
    // ================================================================

    private void onBrowse() {
        File dataDir = new File(System.getProperty("user.dir"), "data");
        if (!dataDir.exists()) dataDir = new File(System.getProperty("user.dir"));
        JFileChooser chooser = new JFileChooser(dataDir);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("TDB files (*.tdb)", "tdb"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = toRelativeIfPossible(chooser.getSelectedFile());
            tdbCombo.getEditor().setItem(path);
            onTdbSelected();
        }
    }

    private void onTdbSelected() {
        String raw = tdbCombo.getEditor().getItem().toString().trim();
        if (raw.isEmpty()) return;

        File f = new File(raw);
        if (!f.isAbsolute()) f = new File(System.getProperty("user.dir"), raw);
        if (!f.exists()) {
            dbStatusLabel.setText("File not found");
            dbStatusLabel.setForeground(DarkTheme.ERROR_COLOR);
            return;
        }

        String absPath = f.getAbsolutePath();
        ModelInfo info = controller.inspectModel(absPath, new String[]{});
        List<String> allElements = info.getAvailableElements() != null
                ? info.getAvailableElements() : new ArrayList<>();
        int nPhases = info.getAvailablePhases() != null ? info.getAvailablePhases().size() : 0;

        selection.setTdbPath(absPath);
        selection.setAvailableElements(allElements);
        selection.setElements(new ArrayList<>());
        selection.setAvailablePhases(new ArrayList<>());

        dbStatusLabel.setText(allElements.size() + " el · " + nPhases + " ph loaded");
        dbStatusLabel.setForeground(DarkTheme.SUCCESS);

        availableHint.setText("Available: " + buildElementHint(allElements));
        confirmedPanel.removeAll();
        confirmedPanel.revalidate();
        confirmedPanel.repaint();
        phasesHint.setText("Phases: —");
        elemInputField.setText("");

        fireSelectionChanged();
    }

    private void onAddElements() {
        String raw = elemInputField.getText().trim();
        if (raw.isEmpty()) return;

        List<String> available = selection.getAvailableElements();
        if (available == null || available.isEmpty()) {
            elemInputField.setForeground(DarkTheme.ERROR_COLOR);
            elemInputField.setToolTipText("Load a database first");
            return;
        }

        // Parse tokens
        String[] tokens = raw.split("[,\\s]+");
        List<String> accepted   = new ArrayList<>();
        List<String> rejected   = new ArrayList<>();
        List<String> alreadyIn  = new ArrayList<>(selection.getElements());

        for (String t : tokens) {
            String upper = t.trim().toUpperCase();
            if (upper.isEmpty()) continue;
            if (alreadyIn.contains(upper)) continue;       // skip duplicates silently
            if (available.contains(upper)) {
                accepted.add(upper);
                alreadyIn.add(upper);
            } else {
                rejected.add(upper);
            }
        }

        // Add accepted badges
        for (String elem : accepted) addBadge(elem);

        // Show rejected in red badge (non-clickable, informational)
        if (!rejected.isEmpty()) {
            JLabel warn = new JLabel("Unknown: " + String.join(",", rejected));
            warn.setFont(F_BADGE);
            warn.setForeground(DarkTheme.ERROR_COLOR);
            confirmedPanel.add(warn);
        }

        confirmedPanel.revalidate();
        confirmedPanel.repaint();
        elemInputField.setText("");
        elemInputField.setForeground(DarkTheme.FG_PRIMARY);

        refreshPhasesForConfirmed();
    }

    private void addBadge(String elem) {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        badge.setBackground(DarkTheme.VALID_BG);
        badge.setBorder(BorderFactory.createLineBorder(DarkTheme.SUCCESS, 1));

        JLabel nameLbl = new JLabel(elem);
        nameLbl.setFont(F_BADGE);
        nameLbl.setForeground(DarkTheme.SUCCESS);
        badge.add(nameLbl);

        JLabel removeLbl = new JLabel("×");
        removeLbl.setFont(F_BADGE);
        removeLbl.setForeground(DarkTheme.FG_SECOND);
        removeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeLbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                confirmedPanel.remove(badge);
                confirmedPanel.revalidate();
                confirmedPanel.repaint();
                refreshPhasesForConfirmed();
            }
        });
        badge.add(removeLbl);
        confirmedPanel.add(badge);
    }

    private void refreshPhasesForConfirmed() {
        List<String> confirmed = getConfirmedElements();
        selection.setElements(confirmed);

        if (confirmed.isEmpty() || selection.getTdbPath() == null) {
            selection.setAvailablePhases(new ArrayList<>());
            phasesHint.setText("Phases: —");
        } else {
            List<String> phases = controller.getPhasesForElements(selection.getTdbPath(), confirmed);
            selection.setAvailablePhases(phases);
            phasesHint.setText(buildPhasesHint(phases));
        }

        fireSelectionChanged();
    }

    // ================================================================
    //  Public API
    // ================================================================

    public void setOnSelectionChanged(Consumer<DatabaseSelection> cb) {
        this.onSelectionChanged = cb;
    }

    public DatabaseSelection getSelection() { return selection; }

    /**
     * Pre-populate panel with defaults (called once at construction by host sidebar).
     */
    public void setDefaults(String tdbRelPath, List<String> elements) {
        if (tdbRelPath != null && !tdbRelPath.isEmpty()) {
            tdbCombo.getEditor().setItem(tdbRelPath);
            onTdbSelected();
        }
        if (elements != null && !elements.isEmpty()) {
            elemInputField.setText(String.join(",", elements));
            onAddElements();
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private List<String> getConfirmedElements() {
        List<String> result = new ArrayList<>();
        for (Component c : confirmedPanel.getComponents()) {
            if (c instanceof JPanel) {
                // First label in badge is the element name
                for (Component inner : ((JPanel) c).getComponents()) {
                    if (inner instanceof JLabel) {
                        String text = ((JLabel) inner).getText();
                        if (!text.equals("×")) { result.add(text); break; }
                    }
                }
            }
        }
        return result;
    }

    private void populateTdbCombo() {
        File dataDir = new File(System.getProperty("user.dir"), "data");
        if (dataDir.exists()) {
            File[] tdbFiles = dataDir.listFiles(
                    (d, name) -> name.toLowerCase().endsWith(".tdb"));
            if (tdbFiles != null) {
                for (File f : tdbFiles) {
                    tdbCombo.addItem("data/" + f.getName());
                }
            }
        }
        if (tdbCombo.getItemCount() > 0) {
            tdbCombo.setSelectedIndex(0);
        }
    }

    private String buildElementHint(List<String> elements) {
        if (elements == null || elements.isEmpty()) return "—";
        // Show first 12 then "…"
        int max = Math.min(elements.size(), 12);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append("  ");
            sb.append(elements.get(i));
        }
        if (elements.size() > max) sb.append("  …(+").append(elements.size() - max).append(")");
        return sb.toString();
    }

    private String buildPhasesHint(List<String> phases) {
        if (phases == null || phases.isEmpty()) return "Phases: (none for selection)";
        int total = phases.size();
        int max = Math.min(total, 6);
        StringBuilder sb = new StringBuilder("Phases (").append(total).append("): ");
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append("  ");
            sb.append(phases.get(i));
        }
        if (total > max) sb.append("  …");
        return sb.toString();
    }

    private void fireSelectionChanged() {
        if (onSelectionChanged != null) onSelectionChanged.accept(selection);
    }

    private String toRelativeIfPossible(File file) {
        File cwd = new File(System.getProperty("user.dir"));
        try {
            String abs = file.getCanonicalPath();
            String cwdAbs = cwd.getCanonicalPath();
            if (abs.startsWith(cwdAbs)) {
                String rel = abs.substring(cwdAbs.length());
                if (rel.startsWith("\\") || rel.startsWith("/")) rel = rel.substring(1);
                return rel.replace('\\', '/');
            }
            return abs;
        } catch (java.io.IOException ex) { return file.getPath(); }
    }

    private GridBagConstraints baseGbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 2, 2, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;
        return g;
    }

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(F_SECT);
        lbl.setForeground(DarkTheme.SECTION_FG);
        lbl.setBorder(new EmptyBorder(2, 0, 2, 0));
        return lbl;
    }

    private JLabel hintLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(F_HINT);
        lbl.setForeground(DarkTheme.FG_SECOND);
        return lbl;
    }

    private JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(F_LABEL);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(DarkTheme.BG_INPUT);
        btn.setForeground(DarkTheme.FG_PRIMARY);
        btn.setOpaque(true);
        return btn;
    }

    // ================================================================
    //  WrapLayout — FlowLayout that wraps onto new lines inside panels
    // ================================================================

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth) {
                            dim.height += rowHeight + vgap;
                            dim.width = Math.max(dim.width, rowWidth);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        if (rowWidth != 0) rowWidth += hgap;
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                dim.height += rowHeight;
                dim.width = Math.max(dim.width, rowWidth);
                dim.width  += insets.left + insets.right + hgap * 2;
                dim.height += insets.top  + insets.bottom + vgap * 2;
                return dim;
            }
        }
    }
}
