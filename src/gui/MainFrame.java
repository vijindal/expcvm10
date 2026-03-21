package gui;

import service.CalculationResult;
import service.ModelInfo;
import infra.AppLevel;
import infra.LoggingConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.*;

/**
 * Professional single-window GUI for thermodynamic calculations.
 *
 * Layout:
 *   Menu bar  : File | Assessment | Help
 *   Left      : Calculation inputs (TDB, elements, method, phases, T, P, composition)
 *   Right     : Progress bar + tabbed results — "Text" and "Graphics"
 *   Bottom    : Log console with level control
 *
 * CalModel and Optimize are legacy file-based workflows available from the
 * Assessment menu. The main panel is focused on direct GUI calculations.
 */
public class MainFrame extends JFrame {
    // --- Label fields for available elements/phases ---
    private JLabel elementsLabel;
    private JLabel phasesLabel;

    // Colors are now defined in DarkTheme - imported above
    private static final Color BG           = DarkTheme.BG;
    private static final Color CARD         = DarkTheme.CARD;
    private static final Color ACCENT       = DarkTheme.ACCENT;
    private static final Color SUCCESS      = DarkTheme.SUCCESS;
    private static final Color ERROR_COLOR  = DarkTheme.ERROR_COLOR;
    private static final Color VALID_BG     = DarkTheme.VALID_BG;
    private static final Color INVALID_BG   = DarkTheme.INVALID_BG;

    private final MainController controller;

    // --- Calculation inputs ---
    private JTextField tdbPathField;
    private JTextField elementsField;
    private JComboBox<String> methodCombo;
    private JTextField phasesField;
    private JTextField temperatureField;
    private JTextField pressureField;
    private JTextField compositionField;
    private JLabel inputStatusLabel;

    // --- Status ---
    private JProgressBar progressBar;

    // --- Result tabs ---
    private JTextArea resultSummaryArea;
    private JPanel graphicsPanel;

    // --- Log console ---
    private JTextArea logArea;
    private JComboBox<String> logLevelCombo;
    private final ArrayList<String> logLines = new ArrayList<>();
    private SwingLogHandler swingLogHandler;

    // --- Export state ---
    private String lastRunRequestSummary = "";
    private String lastRunResultSummary = "";
    private CalculationResult lastRunResult;

    // --- Available from TDB ---
    private java.util.List<String> availableElements = new ArrayList<>();
    private java.util.List<String> availablePhases = new ArrayList<>();

    public MainFrame(MainController controller) {
        super("expCVM 10 — Thermodynamic Workbench");
        this.controller = controller;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 740));
        setLocationRelativeTo(null);
        setJMenuBar(buildMenuBar());
        setContentPane(buildRoot());
        installSwingLogHandler();
        // Load default database on startup
        SwingUtilities.invokeLater(() -> loadDatabaseAndUpdateUI("data/tizr_kum_cvm.tdb"));
    }

    // ================================================================
    //  MENU BAR
    // ================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(DarkTheme.MENU_BG);
        bar.setForeground(DarkTheme.FG_PRIMARY);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));

        // --- File ---
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        styleMenu(fileMenu);

        JMenuItem openTdb = new JMenuItem("Open TDB...");
        openTdb.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openTdb.addActionListener(this::onBrowseTdb);
        styleMenuItem(openTdb);
        fileMenu.add(openTdb);

        JMenuItem inspectTdb = new JMenuItem("Inspect TDB...");
        inspectTdb.addActionListener(this::onLoadModelMetadata);
        styleMenuItem(inspectTdb);
        fileMenu.add(inspectTdb);

        fileMenu.addSeparator();

        JMenuItem exportCsv = new JMenuItem("Export Results as CSV...");
        exportCsv.addActionListener(this::onExportCsv);
        styleMenuItem(exportCsv);
        fileMenu.add(exportCsv);

        JMenuItem exportJson = new JMenuItem("Export Results as JSON...");
        exportJson.addActionListener(this::onExportJson);
        styleMenuItem(exportJson);
        fileMenu.add(exportJson);

        fileMenu.addSeparator();

        JMenuItem exit = new JMenuItem("Exit");
        exit.setAccelerator(KeyStroke.getKeyStroke("alt F4"));
        exit.addActionListener(e -> dispose());
        styleMenuItem(exit);
        fileMenu.add(exit);

        bar.add(fileMenu);

        // --- Assessment ---
        JMenu assessMenu = new JMenu("Assessment");
        assessMenu.setMnemonic('A');
        styleMenu(assessMenu);

        JMenuItem calModel = new JMenuItem("Run CalModel...");
        calModel.addActionListener(this::onMenuCalModel);
        styleMenuItem(calModel);
        assessMenu.add(calModel);

        JMenuItem optimize = new JMenuItem("Run Optimization...");
        optimize.addActionListener(this::onMenuOptimize);
        styleMenuItem(optimize);
        assessMenu.add(optimize);

        bar.add(assessMenu);

        // --- Help ---
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');
        styleMenu(helpMenu);

        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e ->
                JOptionPane.showMessageDialog(this,
                        "expCVM 10 — Thermodynamic Workbench\n\n"
                                + "CALPHAD / CVM thermodynamic calculation\n"
                                + "and assessment tool.\n\n"
                                + "Java 8  |  Swing GUI  |  JUL Logging",
                        "About expCVM 10", JOptionPane.INFORMATION_MESSAGE));
        styleMenuItem(about);
        helpMenu.add(about);

        bar.add(helpMenu);

        return bar;
    }

    // ================================================================
    //  ROOT LAYOUT
    // ================================================================

    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(6, 10, 6, 10));

        root.add(buildMainContent(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildMainContent() {
        // Left: inputs   Right: status + results
        JPanel leftPanel = buildCard("Calculation", buildInputPanel());
        JPanel rightPanel = buildRightPanel();

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        topSplit.setDividerLocation(420);
        topSplit.setResizeWeight(0.35);
        topSplit.setBorder(null);
        topSplit.setDividerSize(1);
        topSplit.setContinuousLayout(true);
        topSplit.setBackground(DarkTheme.BORDER);

        // Bottom: log console
        JPanel logPanel = buildCard("Log Console", buildLogConsole());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, logPanel);
        mainSplit.setDividerLocation(440);
        mainSplit.setResizeWeight(0.68);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(1);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBackground(DarkTheme.BORDER);

        return mainSplit;
    }

    private JPanel buildRightPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setOpaque(false);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setBackground(DarkTheme.BG_INPUT);
        progressBar.setForeground(DarkTheme.ACCENT);
        progressBar.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER));
        wrapper.add(progressBar, BorderLayout.NORTH);

        wrapper.add(buildCard("Results", buildResultTabs()), BorderLayout.CENTER);
        return wrapper;
    }

    // ================================================================
    //  INPUT PANEL  (calculation only — no assessment fields)
    // ================================================================

    private JComponent buildInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD);
        GridBagConstraints gbc = baseGbc();

        // --- TDB File selection ---
        tdbPathField = addField(panel, gbc, 0, "TDB File", "data/tizr_kum_cvm.tdb");
        JButton browseTdb = new JButton("...");
        browseTdb.setMargin(new Insets(1, 4, 1, 4));
        browseTdb.setToolTipText("Browse for TDB file");
        browseTdb.addActionListener(this::onBrowseTdb);
        browseTdb.setBackground(DarkTheme.BG_INPUT);
        browseTdb.setForeground(DarkTheme.FG_PRIMARY);
        browseTdb.setFocusPainted(false);
        browseTdb.setBorderPainted(false);
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(browseTdb, gbc);

        // --- Available elements (label) ---
        elementsLabel = new JLabel("Available Elements: ");
        elementsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        panel.add(elementsLabel, gbc);
        gbc.gridwidth = 1;

        // --- Elements input ---
        elementsField = addField(panel, gbc, 2, "Elements", "TI,ZR");

        // --- Available phases (label) ---
        phasesLabel = new JLabel("Available Phases: ");
        phasesLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        panel.add(phasesLabel, gbc);
        gbc.gridwidth = 1;

        // --- Phases input (moved above method) ---
        phasesField = addField(panel, gbc, 4, "Phases", "LIQUID");

        // --- Method dropdown ---
        JLabel methodLabel = new JLabel("Method");
        methodLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        panel.add(methodLabel, gbc);
        methodCombo = new JComboBox<>(new String[]{"HM", "Gm", "G"}); // Will be updated dynamically if needed
        methodCombo.setEditable(false);
        methodCombo.setBackground(DarkTheme.BG_INPUT);
        methodCombo.setForeground(DarkTheme.FG_PRIMARY);
        methodCombo.setRenderer(new DarkTheme.ComboRenderer());
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 1;
        panel.add(methodCombo, gbc);

        temperatureField = addField(panel, gbc, 6, "T (K)", "500.0");
        pressureField = addField(panel, gbc, 7, "P (Pa)", "10000.0");
        compositionField = addField(panel, gbc, 8, "Composition", "0.333,0.333,0.334");

        // Inline validation status label
        inputStatusLabel = new JLabel(" ");
        inputStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 3; gbc.weightx = 1;
        panel.add(inputStatusLabel, gbc);
        gbc.gridwidth = 1;

        // Register live validation on every field
        attachLiveValidation(tdbPathField);
        attachLiveValidation(elementsField);
        attachLiveValidation(phasesField);
        attachLiveValidation(temperatureField);
        attachLiveValidation(pressureField);
        attachLiveValidation(compositionField);

        // --- Action buttons ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);

        JButton runBtn = smallButton("Run Calculation");
        runBtn.setBackground(ACCENT);
        runBtn.setForeground(Color.WHITE);
        runBtn.setFocusPainted(false);
        runBtn.setBorderPainted(false);
        runBtn.setToolTipText("Execute single-point calculation (validates inputs first)");
        runBtn.addActionListener(this::onRunCalculation);

        JButton resetBtn = smallButton("Reset");
        resetBtn.setToolTipText("Reset all inputs to defaults");
        resetBtn.addActionListener(this::onReset);

        actions.add(runBtn);
        actions.add(resetBtn);

        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 3;
        panel.add(actions, gbc);

        // No need to store references as client properties; now using fields

        return panel;
    }
    // Helper to update available elements/phases/methods in the input panel
    private void updateAvailableLists(java.util.List<String> elements, java.util.List<String> phases) {
        this.availableElements = elements;
        this.availablePhases = phases;
        // Update labels (now uses all available elements from TDB)
        if (elementsLabel != null) {
            elementsLabel.setText("Available Elements: " + String.join(", ", elements));
        }
        if (phasesLabel != null) {
            phasesLabel.setText("Available Phases: " + String.join(", ", phases));
        }
        // TODO: Restrict input fields (e.g., autocomplete or validation) to these lists
        // If you have element/phase input fields, ensure their allowed values are set to 'elements' and 'phases' respectively
        if (elementsLabel != null) elementsLabel.revalidate();
        if (phasesLabel != null) phasesLabel.repaint();
    }

    // Load database, update available lists, and show metadata
    private void loadDatabaseAndUpdateUI(String tdbPath) {
        tdbPathField.setText(tdbPath);
        ModelInfo info = controller.inspectModel(tdbPath, new String[]{});
        java.util.List<String> elements = info.getAvailableElements() != null ? info.getAvailableElements() : new ArrayList<>();
        java.util.List<String> phases = info.getAvailablePhases() != null ? info.getAvailablePhases() : new ArrayList<>();
        updateAvailableLists(elements, phases);
        // Show metadata and parsed results in result window
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(info.getFilePath()).append("\n");
        sb.append("Exists: ").append(info.isFileExists()).append("\n");
        if (info.isFileExists()) {
            sb.append("Modified: ").append(new java.util.Date(info.getLastModifiedEpochMillis())).append("\n");
        }
        sb.append("\nParsed Elements: ").append(String.join(", ", elements)).append("\n");
        sb.append("Parsed Phases: ").append(phases.isEmpty() ? "(none loaded)" : String.join(", ", phases)).append("\n");
        sb.append("\nMethods: HM, Gm, G\n");
        if (info.getError() != null && !info.getError().isEmpty()) {
            sb.append("Error: ").append(info.getError()).append("\n");
        }
        resultSummaryArea.setText(sb.toString());
    }

    // ================================================================
    //  RESULT TABS  (Text + Graphics)
    // ================================================================

    private JComponent buildResultTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tabs.setBackground(DarkTheme.CARD);
        tabs.setForeground(DarkTheme.FG_SECOND);
        tabs.setOpaque(true);

        // --- Text tab ---
        JPanel textTab = new JPanel(new BorderLayout(4, 4));
        textTab.setBackground(CARD);

        resultSummaryArea = new JTextArea();
        resultSummaryArea.setEditable(false);
        resultSummaryArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        resultSummaryArea.setText("No run executed yet.");
        resultSummaryArea.setBackground(DarkTheme.BG);
        resultSummaryArea.setForeground(DarkTheme.FG_PRIMARY);
        resultSummaryArea.setCaretColor(DarkTheme.FG_PRIMARY);
        resultSummaryArea.setSelectionColor(DarkTheme.SEL_BG);

        textTab.add(DarkTheme.scrollPane(resultSummaryArea), BorderLayout.CENTER);

        tabs.addTab("Text", textTab);

        // --- Graphics tab ---
        graphicsPanel = new JPanel(new BorderLayout());
        graphicsPanel.setBackground(CARD);
        JLabel placeholder = new JLabel("Graphics output will appear here after a calculation run.",
                SwingConstants.CENTER);
        placeholder.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        placeholder.setForeground(DarkTheme.FG_SECOND);
        graphicsPanel.add(placeholder, BorderLayout.CENTER);

        tabs.addTab("Graphics", graphicsPanel);

        return tabs;
    }

    // ================================================================
    //  LOG CONSOLE
    // ================================================================

    private JComponent buildLogConsole() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(CARD);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.setOpaque(false);

        toolbar.add(new JLabel("Level:"));
        logLevelCombo = new JComboBox<>(new String[]{"ERROR", "WARN", "RESULT", "FLOW", "ENGINE", "MODEL", "SOLVER", "ALL"});
        logLevelCombo.setSelectedItem("RESULT");
        logLevelCombo.setBackground(DarkTheme.BG_INPUT);
        logLevelCombo.setForeground(DarkTheme.FG_PRIMARY);
        logLevelCombo.setRenderer(new DarkTheme.ComboRenderer());
        logLevelCombo.addActionListener(e -> onLogLevelChanged());
        toolbar.add(logLevelCombo);

        toolbar.add(Box.createHorizontalStrut(12));
        JButton copyBtn = smallButton("Copy");
        copyBtn.addActionListener(this::onCopyLogs);
        toolbar.add(copyBtn);

        JButton clearBtn = smallButton("Clear");
        clearBtn.addActionListener(e -> { logLines.clear(); logArea.setText(""); });
        toolbar.add(clearBtn);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(DarkTheme.BG);
        logArea.setForeground(DarkTheme.FG_PRIMARY);
        logArea.setCaretColor(DarkTheme.FG_PRIMARY);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(DarkTheme.scrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    // ================================================================
    //  JUL  →  GUI  HANDLER
    // ================================================================

    private void installSwingLogHandler() {
        swingLogHandler = new SwingLogHandler();
        swingLogHandler.setLevel(AppLevel.RESULT);
        Logger.getLogger("").addHandler(swingLogHandler);
    }

    private class SwingLogHandler extends Handler {
        private final Formatter fmt = new LoggingConfig.CompactFormatter();

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) return;
            final String formatted = fmt.format(record);
            final String levelName = record.getLevel().getName();
            SwingUtilities.invokeLater(() -> {
                logLines.add("[" + levelName + "] " + formatted.trim());
                refreshLogView();
            });
        }

        @Override public void flush() { }
        @Override public void close() throws SecurityException { }
    }

    private void onLogLevelChanged() {
        String selected = (String) logLevelCombo.getSelectedItem();
        Level level = AppLevel.parse(selected);
        LoggingConfig.setAllHandlerLevels(level);
        addGuiLog("RESULT", "Log level changed to " + selected);
    }

    // ================================================================
    //  INLINE VALIDATION
    // ================================================================

    private void attachLiveValidation(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { validateFieldLive(field); }
            @Override public void removeUpdate(DocumentEvent e) { validateFieldLive(field); }
            @Override public void changedUpdate(DocumentEvent e) { validateFieldLive(field); }
        });
    }

    private void validateFieldLive(JTextField field) {
        String text = field.getText().trim();
        boolean valid = true;
        if (field == tdbPathField) {
            valid = !text.isEmpty() && new File(normalizePath(text)).exists();
        } else if (field == elementsField) {
            valid = splitCsv(text).length >= 2;
        } else if (field == phasesField) {
            String[] p = splitCsv(text);
            valid = p.length > 0 && !p[0].isEmpty();
        } else if (field == temperatureField) {
            valid = parsePositiveDouble(text) > 0;
        } else if (field == pressureField) {
            valid = parsePositiveDouble(text) >= 0;
        } else if (field == compositionField) {
            valid = isValidComposition(text);
        }
        // methodField removed; methodCombo is now used
        field.setBackground(valid ? VALID_BG : INVALID_BG);
        updateInputStatusLabel();
    }

    private void updateInputStatusLabel() {
        ArrayList<String> errors = validateRunInputs();
        if (errors.isEmpty()) {
            inputStatusLabel.setText("All inputs valid");
            inputStatusLabel.setForeground(SUCCESS);
        } else {
            inputStatusLabel.setText(errors.size() + " issue(s): " + errors.get(0));
            inputStatusLabel.setForeground(ERROR_COLOR);
        }
    }

    private double parsePositiveDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return -1; }
    }

    private boolean isValidComposition(String text) {
        try {
            ArrayList<ArrayList<Double>> x = parseCompositions(text);
            double sum = 0;
            for (Double v : x.get(0)) {
                if (v < 0 || v > 1) return false;
                sum += v;
            }
            return Math.abs(sum - 1.0) <= 0.05;
        } catch (Exception e) { return false; }
    }

    // ================================================================
    //  EVENT HANDLERS — Main Panel
    // ================================================================

    private void onBrowseTdb(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String newPath = toRelativeIfPossible(chooser.getSelectedFile());
            tdbPathField.setText(newPath);
            loadDatabaseAndUpdateUI(newPath);
        }
    }

    private void onReset(ActionEvent e) {
        String defaultTdb = "data/tizr_kum_cvm.tdb";
        tdbPathField.setText(defaultTdb);
        elementsField.setText("TI,ZR");
        methodCombo.setSelectedItem("HM");
        phasesField.setText("LIQUID");
        temperatureField.setText("500.0");
        pressureField.setText("10000.0");
        compositionField.setText("0.333,0.333,0.334");
        resultSummaryArea.setText("No run executed yet.");
        lastRunResult = null;
        progressBar.setIndeterminate(false);
        progressBar.setString("Ready");
        addGuiLog("RESULT", "Inputs reset to defaults.");
        loadDatabaseAndUpdateUI(defaultTdb);
    }

    private void onRunCalculation(ActionEvent e) {
        ArrayList<String> errors = validateRunInputs();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String err : errors) sb.append("- ").append(err).append("\n");
            JOptionPane.showMessageDialog(this, sb.toString(), "Validation Errors", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String runId = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        final long t0 = System.currentTimeMillis();

        final String tdbPath = normalizePath(tdbPathField.getText().trim());
        final String[] elements = splitCsv(elementsField.getText().trim());
        final String method = (String) methodCombo.getSelectedItem();
        final String[] phases = splitCsv(phasesField.getText().trim());
        final double T = Double.parseDouble(temperatureField.getText().trim());
        final double P = Double.parseDouble(pressureField.getText().trim());
        final ArrayList<ArrayList<Double>> compositions = parseCompositions(compositionField.getText().trim());

        lastRunRequestSummary = "runId=" + runId + ", method=" + method + ", phases=" + phasesField.getText().trim()
                + ", elements=" + elementsField.getText().trim() + ", T=" + T + ", P=" + P
                + ", x=" + compositionField.getText().trim() + ", tdb=" + tdbPath;

        progressBar.setIndeterminate(true);
        progressBar.setString("Running [" + runId + "]...");
        addGuiLog("RESULT", "Run started: " + lastRunRequestSummary);

        SwingWorker<CalculationResult, Void> worker = new SwingWorker<CalculationResult, Void>() {
            @Override
            protected CalculationResult doInBackground() {
                return controller.runSinglePoint(tdbPath, elements, method, phases, T, P, compositions);
            }

            @Override
            protected void done() {
                long elapsed = System.currentTimeMillis() - t0;
                progressBar.setIndeterminate(false);
                progressBar.setString("Done (" + elapsed + " ms)");
                try {
                    CalculationResult result = get();
                    renderRunResult(result, elapsed);
                } catch (Exception ex) {
                    progressBar.setString("Failed");
                    addGuiLog("ERROR", "Run crashed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onLoadModelMetadata(ActionEvent e) {
        String tdbPath = normalizePath(tdbPathField.getText().trim());
        String[] elements = splitCsv(elementsField.getText().trim());

        addGuiLog("RESULT", "Loading model metadata from " + tdbPath);
        ModelInfo info = controller.inspectModel(tdbPath, elements);

        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(info.getFilePath()).append("\n");
        sb.append("Exists: ").append(info.isFileExists()).append("\n");
        if (info.isFileExists()) {
            sb.append("Modified: ").append(new Date(info.getLastModifiedEpochMillis())).append("\n");
        }
        sb.append("Elements: ").append(String.join(", ", info.getDetectedElements())).append("\n");
        sb.append("Methods: HM, Gm, G\n");
        sb.append("Phases:");
        if (info.getAvailablePhases() == null || info.getAvailablePhases().isEmpty()) {
            sb.append(" (none loaded)\n");
        } else {
            for (String phase : info.getAvailablePhases()) sb.append(" ").append(phase);
            sb.append("\n");
        }
        if (info.getError() != null && !info.getError().isEmpty()) {
            sb.append("Error: ").append(info.getError()).append("\n");
            addGuiLog("WARN", "Metadata warning: " + info.getError());
        } else {
            addGuiLog("RESULT", "Metadata loaded: " + info.getAvailablePhases().size() + " phase(s).");
        }
        resultSummaryArea.setText(sb.toString());
    }

    // ================================================================
    //  EVENT HANDLERS — Assessment menu dialogs
    // ================================================================

    private void onMenuCalModel(ActionEvent e) {
        JTextField exptField = new JTextField("data/ExptData.txt", 25);
        JTextField phaseField = new JTextField("data/PhaseData.txt", 25);

        JPanel dialogPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        dialogPanel.add(new JLabel("Experimental Data File:"));
        dialogPanel.add(exptField);
        dialogPanel.add(new JLabel("Phase Data File:"));
        dialogPanel.add(phaseField);

        int result = JOptionPane.showConfirmDialog(this, dialogPanel,
                "Run CalModel", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        final String expt = normalizePath(exptField.getText().trim());
        final String phase = normalizePath(phaseField.getText().trim());

        if (!new File(expt).exists()) {
            JOptionPane.showMessageDialog(this, "Experimental data file not found:\n" + expt,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!new File(phase).exists()) {
            JOptionPane.showMessageDialog(this, "Phase data file not found:\n" + phase,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        progressBar.setIndeterminate(true);
        progressBar.setString("CalModel...");
        addGuiLog("RESULT", "CalModel started: expt=" + expt + ", phase=" + phase);

        SwingWorker<CalculationResult, Void> worker = new SwingWorker<CalculationResult, Void>() {
            @Override
            protected CalculationResult doInBackground() {
                return controller.runCalModel(expt, phase);
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setString("Done");
                try {
                    CalculationResult r = get();
                    renderRunResult(r, -1);
                } catch (Exception ex) {
                    progressBar.setString("Failed");
                    addGuiLog("ERROR", "CalModel failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onMenuOptimize(ActionEvent e) {
        JTextField exptField = new JTextField("data/ExptData.txt", 25);
        JTextField phaseField = new JTextField("data/PhaseData.txt", 25);
        JTextField prefixField = new JTextField("data/log", 25);
        JTextField iterField = new JTextField("50", 10);

        JPanel dialogPanel = new JPanel(new GridLayout(4, 2, 6, 6));
        dialogPanel.add(new JLabel("Experimental Data File:"));
        dialogPanel.add(exptField);
        dialogPanel.add(new JLabel("Phase Data File:"));
        dialogPanel.add(phaseField);
        dialogPanel.add(new JLabel("Output Prefix:"));
        dialogPanel.add(prefixField);
        dialogPanel.add(new JLabel("Max Iterations:"));
        dialogPanel.add(iterField);

        int result = JOptionPane.showConfirmDialog(this, dialogPanel,
                "Run Optimization", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        final String expt = normalizePath(exptField.getText().trim());
        final String phase = normalizePath(phaseField.getText().trim());
        final String prefix = normalizePath(prefixField.getText().trim());

        if (!new File(expt).exists()) {
            JOptionPane.showMessageDialog(this, "Experimental data file not found:\n" + expt,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!new File(phase).exists()) {
            JOptionPane.showMessageDialog(this, "Phase data file not found:\n" + phase,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int maxIter;
        try {
            maxIter = Integer.parseInt(iterField.getText().trim());
            if (maxIter <= 0) throw new NumberFormatException("must be > 0");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid max iterations. Must be a positive integer.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int maxIterations = maxIter;
        progressBar.setIndeterminate(true);
        progressBar.setString("Optimization...");
        addGuiLog("RESULT", "Optimization started (maxIter=" + maxIterations + ").");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return controller.runOptimization(expt, phase, prefix, maxIterations);
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setString("Done");
                try {
                    String msg = get();
                    boolean ok = msg.toLowerCase().contains("success");
                    progressBar.setString(ok ? "Done" : "Failed");
                    addGuiLog(ok ? "RESULT" : "ERROR", msg);
                    lastRunResultSummary = msg;
                } catch (Exception ex) {
                    progressBar.setString("Failed");
                    addGuiLog("ERROR", "Optimization failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ================================================================
    //  EXPORT  (accessed from File menu)
    // ================================================================

    private void onCopyLogs(ActionEvent e) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(logArea.getText()), null);
    }

    private void onExportCsv(ActionEvent e) {
        if (lastRunResult == null) {
            JOptionPane.showMessageDialog(this, "No result to export.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setSelectedFile(new File("run-result.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            FileWriter fw = new FileWriter(file);
            fw.write("Metric,Value,Unit\n");
            fw.write("Success," + lastRunResult.isSuccess() + ",-\n");
            fw.write(csvEscape("Method") + "," + csvEscape(safe(lastRunResult.getMethod())) + ",-\n");
            fw.write(csvEscape("Message") + "," + csvEscape(safe(lastRunResult.getMessage())) + ",-\n");
            fw.write("Value," + lastRunResult.getValue() + ",J/mol\n");
            fw.write("Temperature," + lastRunResult.getTemperature() + ",K\n");
            fw.write("Pressure," + lastRunResult.getPressure() + ",Pa\n");
            fw.write("Composition," + csvEscape(formatComposition(lastRunResult.getCompositionResult())) + ",-\n");
            fw.close();
            addGuiLog("RESULT", "CSV exported: " + file.getAbsolutePath());
        } catch (IOException ex) {
            addGuiLog("ERROR", "CSV export failed: " + ex.getMessage());
        }
    }

    private void onExportJson(ActionEvent e) {
        if (lastRunResult == null) {
            JOptionPane.showMessageDialog(this, "No result to export.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setSelectedFile(new File("run-result.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            FileWriter fw = new FileWriter(file);
            fw.write("{\n");
            fw.write("  \"timestamp\": \"" + escapeJson(new Date().toString()) + "\",\n");
            fw.write("  \"requestSummary\": \"" + escapeJson(lastRunRequestSummary) + "\",\n");
            fw.write("  \"success\": " + lastRunResult.isSuccess() + ",\n");
            fw.write("  \"method\": \"" + escapeJson(safe(lastRunResult.getMethod())) + "\",\n");
            fw.write("  \"message\": \"" + escapeJson(safe(lastRunResult.getMessage())) + "\",\n");
            fw.write("  \"value\": " + lastRunResult.getValue() + ",\n");
            fw.write("  \"temperature\": " + lastRunResult.getTemperature() + ",\n");
            fw.write("  \"pressure\": " + lastRunResult.getPressure() + ",\n");
            fw.write("  \"composition\": \"" + escapeJson(formatComposition(lastRunResult.getCompositionResult())) + "\"\n");
            fw.write("}\n");
            fw.close();
            addGuiLog("RESULT", "JSON exported: " + file.getAbsolutePath());
        } catch (IOException ex) {
            addGuiLog("ERROR", "JSON export failed: " + ex.getMessage());
        }
    }

    // ================================================================
    //  VALIDATION
    // ================================================================

    private ArrayList<String> validateRunInputs() {
        ArrayList<String> errors = new ArrayList<>();

        String path = normalizePath(tdbPathField.getText().trim());
        if (!(new File(path).exists())) {
            errors.add("TDB file does not exist: " + path);
        }
        String[] elements = splitCsv(elementsField.getText().trim());
        if (elements.length < 2) {
            errors.add("At least two elements are required.");
        }
        if (methodCombo.getSelectedItem() == null || ((String)methodCombo.getSelectedItem()).trim().isEmpty()) {
            errors.add("Method is required (e.g. HM, Gm, G).");
        }
        String[] phases = splitCsv(phasesField.getText().trim());
        if (phases.length == 0 || (phases.length == 1 && phases[0].isEmpty())) {
            errors.add("At least one phase is required.");
        }
        try {
            double t = Double.parseDouble(temperatureField.getText().trim());
            if (t <= 0) errors.add("Temperature must be > 0 K.");
        } catch (NumberFormatException ex) {
            errors.add("Invalid temperature value.");
        }
        try {
            double p = Double.parseDouble(pressureField.getText().trim());
            if (p < 0) errors.add("Pressure must be >= 0 Pa.");
        } catch (NumberFormatException ex) {
            errors.add("Invalid pressure value.");
        }
        try {
            ArrayList<ArrayList<Double>> x = parseCompositions(compositionField.getText().trim());
            double sum = 0.0;
            for (Double v : x.get(0)) {
                if (v < 0 || v > 1) { errors.add("Composition values must be in [0, 1]."); break; }
                sum += v;
            }
            if (Math.abs(sum - 1.0) > 0.05) {
                errors.add("Composition sum should be ~1.0 (got " + String.format("%.4f", sum) + ").");
            }
        } catch (Exception ex) {
            errors.add("Invalid composition list. Use comma-separated numbers.");
        }
        return errors;
    }

    // ================================================================
    //  RESULT RENDERING
    // ================================================================

    private void renderRunResult(CalculationResult result, long elapsedMillis) {
        lastRunResult = result;
        StringBuilder sb = new StringBuilder();
        sb.append("Status   : ").append(result.isSuccess() ? "SUCCESS" : "FAILED").append("\n");
        sb.append("Method   : ").append(safe(result.getMethod())).append("\n");
        sb.append("Value    : ").append(result.getValue()).append(" J/mol\n");
        sb.append("Temp     : ").append(result.getTemperature()).append(" K\n");
        sb.append("Pressure : ").append(result.getPressure()).append(" Pa\n");
        sb.append("Comp     : ").append(formatComposition(result.getCompositionResult())).append("\n");
        if (elapsedMillis >= 0) {
            sb.append("Duration : ").append(elapsedMillis).append(" ms\n");
        }
        sb.append("\n").append(safe(result.getMessage()));

        resultSummaryArea.setText(sb.toString());
        lastRunResultSummary = safe(result.getMessage());

        if (result.isSuccess()) {
            addGuiLog("RESULT", "Run completed: " + safe(result.getMessage()));
        } else {
            addGuiLog("ERROR", "Run failed: " + safe(result.getMessage()));
        }
    }

    // ================================================================
    //  HELPERS
    // ================================================================

    private void addGuiLog(String level, String message) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String line = "[" + level + "] " + ts + " " + level + " [GUI] " + message;
        logLines.add(line);
        refreshLogView();
    }

    private void refreshLogView() {
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) {
            int closeBracket = line.indexOf(']');
            sb.append(closeBracket >= 0 ? line.substring(closeBracket + 2) : line).append("\n");
        }
        if (logArea != null) {
            logArea.setText(sb.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    // ==================== UI Utilities ====================

    private JPanel buildCard(String title, JComponent content) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(CARD);
        outer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER),
                new EmptyBorder(6, 8, 6, 8)));

        JLabel cardTitle = new JLabel(title);
        cardTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cardTitle.setForeground(ACCENT);

        outer.add(cardTitle, BorderLayout.NORTH);
        outer.add(content, BorderLayout.CENTER);
        return outer;
    }

    private GridBagConstraints baseGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private JTextField addField(JPanel panel, GridBagConstraints gbc, int row,
                                String label, String defaultValue) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JTextField f = new JTextField(defaultValue, 22);
        f.setFont(new Font("Consolas", Font.PLAIN, 11));
        f.setBackground(DarkTheme.BG_INPUT);
        f.setForeground(DarkTheme.FG_PRIMARY);
        f.setCaretColor(DarkTheme.FG_PRIMARY);
        f.setSelectionColor(DarkTheme.SEL_BG);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(l, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(f, gbc);

        return f;
    }

    private JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setMargin(new Insets(2, 8, 2, 8));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(DarkTheme.BG_INPUT);
        btn.setForeground(DarkTheme.FG_PRIMARY);
        btn.setOpaque(true);
        return btn;
    }

    private String[] splitCsv(String in) {
        if (in == null || in.trim().isEmpty()) return new String[0];
        return in.trim().split("\\s*,\\s*");
    }

    private ArrayList<ArrayList<Double>> parseCompositions(String input) {
        String[] parts = splitCsv(input);
        ArrayList<Double> row = new ArrayList<>();
        for (String part : parts) row.add(Double.parseDouble(part));
        ArrayList<ArrayList<Double>> out = new ArrayList<>();
        out.add(row);
        return out;
    }

    private String normalizePath(String rawPath) {
        File f = new File(rawPath);
        if (f.isAbsolute()) return f.getPath();
        return new File(System.getProperty("user.dir"), rawPath).getPath();
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
        } catch (IOException ex) {
            return file.getPath();
        }
    }

    private String csvEscape(Object val) {
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatComposition(double[] x) {
        if (x == null || x.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < x.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", x[i]));
        }
        return sb.toString();
    }

    // ========== Menu Styling Helpers ==========
    private void styleMenu(JMenu menu) {
        menu.setBackground(DarkTheme.MENU_BG);
        menu.setForeground(DarkTheme.FG_PRIMARY);
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(DarkTheme.MENU_BG);
        item.setForeground(DarkTheme.FG_PRIMARY);
    }
}
