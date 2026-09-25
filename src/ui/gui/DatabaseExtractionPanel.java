package ui.gui;

import ui.request.DatabaseSelection;
import ui.result.ModelInfo;

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

    private final MainController controller;

    private JComboBox<String> tdbCombo;
    private JLabel            dbStatusLabel;

    private TagInputField elementsField;
    private JLabel         phasesHint;

    private final DatabaseSelection selection = new DatabaseSelection();
    private Consumer<DatabaseSelection> onSelectionChanged;

    private GuiCalculationContext context;
    private boolean syncingFromContext = false;

    public DatabaseExtractionPanel(MainController controller) {
        this.controller = controller;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DarkTheme.SIDEBAR_BG);
        setBorder(new EmptyBorder(DarkTheme.SPACE_MD, DarkTheme.SPACE_LG, DarkTheme.SPACE_MD, DarkTheme.SPACE_LG));

        add(buildDatabaseSection());
        add(Box.createVerticalStrut(DarkTheme.SPACE_LG - 2));
        add(buildElementsSection());
    }

    /**
     * Binds this panel to a shared {@link GuiCalculationContext}: local
     * selection changes are pushed into it, and it is used to restore
     * this panel's fields when {@link #syncFromContext()} is called
     * (e.g. when the host activity becomes visible again).
     */
    public void bindContext(GuiCalculationContext context) {
        this.context = context;
        syncFromContext();
    }

    /** Repopulates this panel's fields from the bound context without re-parsing the TDB. */
    public void syncFromContext() {
        if (context == null) return;
        DatabaseSelection ctxSel = context.getSelection();
        if (!ctxSel.hasTdb()) return;

        syncingFromContext = true;
        try {
            selection.setTdbPath(ctxSel.getTdbPath());
            selection.setAvailableElements(ctxSel.getAvailableElements());
            selection.setElements(ctxSel.getElements());
            selection.setAvailablePhases(ctxSel.getAvailablePhases());

            tdbCombo.getEditor().setItem(toRelativeIfPossible(new File(ctxSel.getTdbPath())));
            List<String> allElements = ctxSel.getAvailableElements() != null ? ctxSel.getAvailableElements() : new ArrayList<>();
            int nPhases = ctxSel.getAvailablePhases() != null ? ctxSel.getAvailablePhases().size() : 0;
            dbStatusLabel.setText(allElements.size() + " el · " + nPhases + " ph loaded");
            dbStatusLabel.setForeground(DarkTheme.SUCCESS);

            elementsField.setKnownValues(allElements);
            elementsField.setHintText("Available: " + buildElementHint(allElements));
            elementsField.clear();
            for (String el : ctxSel.getElements()) elementsField.addKnownSelectedValue(el);
            phasesHint.setText(buildPhasesHint(ctxSel.getAvailablePhases()));
        } finally {
            syncingFromContext = false;
        }
    }

    // ================================================================
    //  DATABASE section
    // ================================================================

    private JPanel buildDatabaseSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = DarkTheme.formGbc();
        g.insets = new Insets(2, 2, 2, 2);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 3; g.weightx = 1;
        panel.add(DarkTheme.sectionLabel("DATABASE"), g);
        g.gridwidth = 1;

        tdbCombo = DarkTheme.comboBox(new String[0]);
        tdbCombo.setFont(DarkTheme.FONT_MONO);
        tdbCombo.setEditable(true);
        ((JTextField) tdbCombo.getEditor().getEditorComponent()).setBackground(DarkTheme.BG_INPUT);
        ((JTextField) tdbCombo.getEditor().getEditorComponent()).setForeground(DarkTheme.FG_PRIMARY);
        populateTdbCombo();
        g.gridx = 0; g.gridy = 1; g.weightx = 1; g.gridwidth = 2;
        panel.add(tdbCombo, g);
        g.gridwidth = 1;

        JButton browse = DarkTheme.smallButton("…");
        browse.setToolTipText("Browse for TDB file");
        browse.addActionListener(e -> onBrowse());
        g.gridx = 2; g.gridy = 1; g.weightx = 0;
        panel.add(browse, g);

        dbStatusLabel = DarkTheme.hintLabel("No database loaded");
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3; g.weightx = 1;
        panel.add(dbStatusLabel, g);
        g.gridwidth = 1;

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
        JPanel panel = new JPanel(new BorderLayout(0, DarkTheme.SPACE_XS));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        panel.add(DarkTheme.sectionLabel("ELEMENTS"), BorderLayout.NORTH);

        elementsField = new TagInputField("Available: —", true);
        elementsField.setOnChanged(this::refreshPhasesForConfirmed);
        panel.add(elementsField, BorderLayout.CENTER);

        phasesHint = DarkTheme.hintLabel("Phases: —");
        panel.add(phasesHint, BorderLayout.SOUTH);

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

        elementsField.setKnownValues(allElements);
        elementsField.setHintText("Available: " + buildElementHint(allElements));
        elementsField.clear();
        phasesHint.setText("Phases: —");

        fireSelectionChanged();
    }

    private void refreshPhasesForConfirmed() {
        if (syncingFromContext) return;

        List<String> confirmed = new ArrayList<>(elementsField.getValues());
        java.util.Collections.sort(confirmed);
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
     * Pre-fills the database path and element fields without loading or
     * parsing anything -- no TDB access happens until the user interacts
     * with the panel (selects/confirms the database, then adds elements).
     */
    public void setDefaults(String tdbRelPath, List<String> elements) {
        if (tdbRelPath != null && !tdbRelPath.isEmpty()) {
            tdbCombo.getEditor().setItem(tdbRelPath);
        }
        if (elements != null && !elements.isEmpty()) {
            elementsField.getInputField().setText(String.join(",", elements));
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private void populateTdbCombo() {
        for (String path : controller.availableDatabases()) {
            tdbCombo.addItem(path);
        }
        if (tdbCombo.getItemCount() > 0) {
            tdbCombo.setSelectedIndex(0);
        }
    }

    private String buildElementHint(List<String> elements) {
        if (elements == null || elements.isEmpty()) return "—";
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
        if (context != null && !syncingFromContext) context.applySelection(selection);
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
}
