package ui.gui;

import system.ports.EquilibriumResult;
import ui.result.CalculationResult;
import calc.diagram.PhaseDiagramResult;
import ui.result.PropertyScanResult;
import util.AppLevel;
import util.LoggingConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.*;

/**
 * Main window for expCVM 10.
 *
 * Header (NORTH)        : application title + current-activity context.
 * Activity bar (WEST)   : Single Point | STEP | MAP | Phase Diagram | Coarse Diagram | Inspect
 * Sidebar (LEFT, 300 px): CardLayout — activity-specific config
 * Editor  (RIGHT, flex) : CardLayout — activity-specific results
 * Log console (SOUTH)   : OUTPUT panel
 *
 * MainFrame builds and coordinates the shell; per-activity SwingWorker
 * lifecycles are delegated to {@link ActivityPresenters}, and result
 * formatting/export to {@link ResultFormatter}/{@link ResultExporter}.
 */
public class MainFrame extends JFrame {

    private static final Color BG          = DarkTheme.BG;

    private final MainController controller;

    private ActivityBar activityBar;
    private JLabel headerContextLabel;

    private JPanel sidebarCard;
    private SinglePointSidebarPanel    singlePointSidebar;
    private PropertyCalcConfigPanel    stepCalcPanel;
    private PropertyCalcConfigPanel    mapCalcPanel;
    private PhaseDiagramConfigPanel    phaseDiagramConfigPanel;
    private PhaseDiagramConfigPanel    coarseDiagramConfigPanel;
    private ModelInspectorSidebarPanel modelInspectorSidebar;

    private JPanel                 editorCard;
    private SinglePointResultPanel singlePointResultPanel;
    private JLabel                 resultStatusLabel;
    private StepResultPanel        stepResultPanel;
    private PhaseDiagramPanel      mapResultPanel;
    private PhaseDiagramPanel      phaseDiagramPanel;
    private CoarseDiagramPanel     coarseDiagramPanel;
    private JList<String>          phaseJList;
    private JTextArea              paramArea;
    private String                 currentInspectorTdb;

    private JTextArea logArea;
    private JComboBox<String> logLevelCombo;
    private final ArrayList<String> logLines = new ArrayList<>();
    private SwingLogHandler swingLogHandler;

    private String            lastRunRequestSummary = "";
    private CalculationResult lastRunResult;

    private ActivityPresenters presenters;
    private final GuiCalculationContext calcContext = new GuiCalculationContext();

    public MainFrame(MainController controller) {
        super("expCVM 10 — Thermodynamic Workbench");
        this.controller = controller;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 740));
        setLocationRelativeTo(null);
        setJMenuBar(buildMenuBar());
        setContentPane(buildRoot());
        installSwingLogHandler();
        SwingUtilities.invokeLater(() -> addGuiLog("INFO", "Ready."));
    }

    // ================================================================
    //  ROOT
    // ================================================================

    private JComponent buildRoot() {
        singlePointSidebar = new SinglePointSidebarPanel(controller);
        singlePointSidebar.setRunCallback(this::onRunCalculation);
        singlePointSidebar.setResetCallback(this::onReset);
        singlePointSidebar.bindContext(calcContext);

        stepCalcPanel = new PropertyCalcConfigPanel(controller, false);
        stepCalcPanel.setCalculateCallback(this::onCalculateStep);
        stepCalcPanel.bindContext(calcContext);

        mapCalcPanel = new PropertyCalcConfigPanel(controller, true);
        mapCalcPanel.setCalculateCallback(this::onCalculateMap);
        mapCalcPanel.bindContext(calcContext);

        phaseDiagramConfigPanel = new PhaseDiagramConfigPanel(controller);
        phaseDiagramConfigPanel.setCalculateCallback(this::onCalculatePhaseDiagram);
        phaseDiagramConfigPanel.bindContext(calcContext);

        coarseDiagramConfigPanel = new PhaseDiagramConfigPanel(controller, PhaseDiagramConfigPanel.Mode.COARSE);
        coarseDiagramConfigPanel.setCalculateCallback(this::onCalculateCoarseDiagram);
        coarseDiagramConfigPanel.bindContext(calcContext);

        modelInspectorSidebar = new ModelInspectorSidebarPanel(controller);
        modelInspectorSidebar.setInspectCallback(this::onInspectModel);
        modelInspectorSidebar.setOnSelectionChanged(sel -> {
            if (sel.hasElements()) refreshInspectorPhaseList(sel);
        });
        modelInspectorSidebar.bindContext(calcContext);

        sidebarCard = new JPanel(new CardLayout());
        sidebarCard.setPreferredSize(new Dimension(DarkTheme.SIDEBAR_WIDTH, 0));
        sidebarCard.setMinimumSize(new Dimension(220, 0));
        sidebarCard.add(singlePointSidebar,     "singlepoint");
        sidebarCard.add(stepCalcPanel,           "stepcalc");
        sidebarCard.add(mapCalcPanel,            "mapcalc");
        sidebarCard.add(phaseDiagramConfigPanel, "phasediagram");
        sidebarCard.add(coarseDiagramConfigPanel, "coarsediagram");
        sidebarCard.add(modelInspectorSidebar,   "inspector");

        editorCard = new JPanel(new CardLayout());
        editorCard.add(buildSinglePointEditor(), "singlepoint");
        editorCard.add(buildStepEditor(),        "stepcalc");
        editorCard.add(buildMapEditor(),         "mapcalc");
        editorCard.add(buildPhaseDiagramEditor(),"phasediagram");
        editorCard.add(buildCoarseDiagramEditor(),"coarsediagram");
        editorCard.add(buildInspectorEditor(),   "inspector");

        presenters = new ActivityPresenters(controller, this);

        JPanel logPanel = buildLogPanel();
        logPanel.setPreferredSize(new Dimension(0, 150));

        JSplitPane editorWithLog = DarkTheme.sleekSplit(JSplitPane.VERTICAL_SPLIT);
        editorWithLog.setTopComponent(editorCard);
        editorWithLog.setBottomComponent(logPanel);
        editorWithLog.setResizeWeight(0.80);

        JSplitPane workspaceSplit = DarkTheme.sleekSplit(JSplitPane.HORIZONTAL_SPLIT);
        workspaceSplit.setLeftComponent(sidebarCard);
        workspaceSplit.setRightComponent(editorWithLog);
        workspaceSplit.setDividerLocation(DarkTheme.SIDEBAR_WIDTH);
        workspaceSplit.setResizeWeight(0.0);

        JComponent header = buildHeader();

        activityBar = new ActivityBar();
        activityBar.addActivity("Single Point",   new ActivityBar.CircleIcon(),   () -> switchActivity("singlepoint", "Single Point"));
        activityBar.addActivity("STEP Calc",      new ActivityBar.LineIcon(),     () -> switchActivity("stepcalc", "STEP Calculation"));
        activityBar.addActivity("MAP Calc",       new ActivityBar.GridIcon(),     () -> switchActivity("mapcalc", "MAP Calculation"));
        activityBar.addActivity("Phase Diagram",  new ActivityBar.DiamondIcon(),  () -> switchActivity("phasediagram", "Phase Diagram"));
        activityBar.addActivity("Coarse Diagram", new ActivityBar.ScatterIcon(),  () -> switchActivity("coarsediagram", "Coarse Diagram"));
        activityBar.addUtility("Inspect Database", new ActivityBar.InfoIcon(),    () -> switchActivity("inspector", "Model Inspector"));

        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setBackground(BG);
        center.add(activityBar,    BorderLayout.WEST);
        center.add(workspaceSplit, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.add(header,  BorderLayout.NORTH);
        root.add(center,  BorderLayout.CENTER);
        return root;
    }

    // ================================================================
    //  HEADER
    // ================================================================

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DarkTheme.HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER),
                new EmptyBorder(DarkTheme.SPACE_MD, DarkTheme.SPACE_LG, DarkTheme.SPACE_MD, DarkTheme.SPACE_LG)));

        JLabel title = new JLabel("expCVM 10");
        title.setFont(DarkTheme.FONT_APP_TITLE);
        title.setForeground(DarkTheme.FG_PRIMARY);

        headerContextLabel = new JLabel("Single Point");
        headerContextLabel.setFont(DarkTheme.FONT_LABEL);
        headerContextLabel.setForeground(DarkTheme.FG_SECOND);
        headerContextLabel.setBorder(new EmptyBorder(0, DarkTheme.SPACE_MD, 0, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(title);
        left.add(separatorDot());
        left.add(headerContextLabel);

        header.add(left, BorderLayout.WEST);
        return header;
    }

    private JLabel separatorDot() {
        JLabel dot = new JLabel(" • ");
        dot.setFont(DarkTheme.FONT_LABEL);
        dot.setForeground(DarkTheme.BORDER);
        return dot;
    }

    private void switchActivity(String key, String contextLabel) {
        ((CardLayout) sidebarCard.getLayout()).show(sidebarCard, key);
        ((CardLayout) editorCard.getLayout()).show(editorCard, key);
        headerContextLabel.setText(contextLabel);
        notifyActivityShown(key);
    }

    /** Refreshes the newly-shown sidebar's fields from the shared {@link GuiCalculationContext}. */
    private void notifyActivityShown(String key) {
        switch (key) {
            case "singlepoint"   -> singlePointSidebar.onActivityShown();
            case "stepcalc"      -> stepCalcPanel.onActivityShown();
            case "mapcalc"       -> mapCalcPanel.onActivityShown();
            case "phasediagram"  -> phaseDiagramConfigPanel.onActivityShown();
            case "coarsediagram" -> coarseDiagramConfigPanel.onActivityShown();
            case "inspector"     -> modelInspectorSidebar.onActivityShown();
            default -> { }
        }
    }

    // ================================================================
    //  EDITOR PANELS
    // ================================================================

    private JComponent buildSinglePointEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);

        resultStatusLabel = new JLabel("No run executed yet.");
        resultStatusLabel.setFont(DarkTheme.FONT_HINT);
        resultStatusLabel.setForeground(DarkTheme.FG_SECOND);
        resultStatusLabel.setBorder(new EmptyBorder(3, 8, 3, 8));
        resultStatusLabel.setOpaque(true);
        resultStatusLabel.setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout(0, 0));
        topBar.setOpaque(false);
        topBar.add(DarkTheme.panelHeader("RESULTS"), BorderLayout.NORTH);
        topBar.add(resultStatusLabel, BorderLayout.CENTER);
        panel.add(topBar, BorderLayout.NORTH);

        singlePointResultPanel = new SinglePointResultPanel();
        panel.add(singlePointResultPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildStepEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);
        panel.add(DarkTheme.panelHeader("STEP RESULTS"), BorderLayout.NORTH);
        stepResultPanel = new StepResultPanel();
        panel.add(stepResultPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMapEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);
        panel.add(DarkTheme.panelHeader("MAP RESULTS"), BorderLayout.NORTH);
        mapResultPanel = new PhaseDiagramPanel();
        mapResultPanel.setBackground(Color.WHITE);
        panel.add(mapResultPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildPhaseDiagramEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);
        panel.add(DarkTheme.panelHeader("PHASE DIAGRAM"), BorderLayout.NORTH);
        phaseDiagramPanel = new PhaseDiagramPanel();
        phaseDiagramPanel.setBackground(Color.WHITE);
        panel.add(phaseDiagramPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildCoarseDiagramEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);
        panel.add(DarkTheme.panelHeader("COARSE DIAGRAM"), BorderLayout.NORTH);
        coarseDiagramPanel = new CoarseDiagramPanel();
        coarseDiagramPanel.setBackground(Color.WHITE);
        panel.add(coarseDiagramPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildInspectorEditor() {
        phaseJList = new JList<>(new DefaultListModel<>());
        phaseJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        phaseJList.setFont(DarkTheme.FONT_MONO);
        phaseJList.setBackground(BG);
        phaseJList.setForeground(DarkTheme.FG_PRIMARY);
        phaseJList.setSelectionBackground(DarkTheme.SEL_BG);
        phaseJList.setSelectionForeground(DarkTheme.FG_PRIMARY);
        phaseJList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onPhaseSelected(); });

        JPanel col1 = new JPanel(new BorderLayout(0, 0));
        col1.setBackground(BG);
        col1.add(DarkTheme.panelHeader("PHASES"), BorderLayout.NORTH);
        col1.add(DarkTheme.scrollPane(phaseJList), BorderLayout.CENTER);
        col1.setPreferredSize(new Dimension(185, 0));

        paramArea = new JTextArea("Select a phase to view its parameters.");
        paramArea.setEditable(false);
        paramArea.setFont(DarkTheme.FONT_MONO);
        paramArea.setBackground(BG);
        paramArea.setForeground(DarkTheme.FG_PRIMARY);
        paramArea.setCaretColor(DarkTheme.FG_PRIMARY);

        JPanel col2 = new JPanel(new BorderLayout(0, 0));
        col2.setBackground(BG);
        col2.add(DarkTheme.panelHeader("PARAMETERS"), BorderLayout.NORTH);
        col2.add(DarkTheme.scrollPane(paramArea), BorderLayout.CENTER);

        JSplitPane split = DarkTheme.sleekSplit(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(col1);
        split.setRightComponent(col2);
        split.setDividerLocation(185);
        return split;
    }

    // ================================================================
    //  LOG CONSOLE
    // ================================================================

    private JPanel buildLogPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 0));
        outer.setBackground(BG);
        outer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER));

        JLabel levelLabel = DarkTheme.hintLabel("Level:");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.setOpaque(false);
        toolbar.add(levelLabel);

        logLevelCombo = DarkTheme.comboBox(new String[]{"ERROR","WARN","RESULT","FLOW","ENGINE","MODEL","SOLVER","ALL"});
        logLevelCombo.setSelectedItem("RESULT");
        logLevelCombo.addActionListener(e -> onLogLevelChanged());
        toolbar.add(logLevelCombo);

        JButton copyBtn = DarkTheme.smallButton("Copy");
        copyBtn.addActionListener(this::onCopyLogs);
        toolbar.add(copyBtn);

        JButton clearBtn = DarkTheme.smallButton("Clear");
        clearBtn.addActionListener(e -> { logLines.clear(); logArea.setText(""); });
        toolbar.add(clearBtn);

        outer.add(DarkTheme.panelHeader("OUTPUT", toolbar), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(DarkTheme.FONT_MONO);
        logArea.setBackground(BG);
        logArea.setForeground(DarkTheme.FG_PRIMARY);
        logArea.setCaretColor(DarkTheme.FG_PRIMARY);
        outer.add(DarkTheme.scrollPane(logArea), BorderLayout.CENTER);
        return outer;
    }

    // ================================================================
    //  MENU BAR
    // ================================================================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(DarkTheme.MENU_BG);
        bar.setForeground(DarkTheme.FG_PRIMARY);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        styleMenu(fileMenu);

        JMenuItem openTdb = new JMenuItem("Open TDB...");
        openTdb.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openTdb.addActionListener(e -> {
            File dataDir = new File(System.getProperty("user.dir"), "data");
            if (!dataDir.exists()) dataDir = new File(System.getProperty("user.dir"));
            JFileChooser chooser = new JFileChooser(dataDir);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("TDB files (*.tdb)", "tdb"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = toRelativeIfPossible(chooser.getSelectedFile());
                singlePointSidebar.resetDefaults(path);
                switchActivity("singlepoint", "Single Point");
            }
        });
        styleMenuItem(openTdb);
        fileMenu.add(openTdb);
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

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');
        styleMenu(helpMenu);

        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "expCVM 10 — Thermodynamic Workbench\n\n"
                + "CALPHAD / CVM thermodynamic calculation and assessment tool.\n\n"
                + "Java  |  Swing GUI  |  JUL Logging",
                "About expCVM 10", JOptionPane.INFORMATION_MESSAGE));
        styleMenuItem(about);
        helpMenu.add(about);
        bar.add(helpMenu);
        return bar;
    }

    // ================================================================
    //  EVENT HANDLERS
    // ================================================================

    // ── Single point ──────────────────────────────────────────────────

    private void onRunCalculation() {
        java.util.List<String> errors = singlePointSidebar.validateAll();
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String err : errors) sb.append("- ").append(err).append("\n");
            JOptionPane.showMessageDialog(this, sb.toString(), "Validation Errors", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final String runId = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        final long t0 = System.currentTimeMillis();
        final String tdbPath     = normalizePath(singlePointSidebar.getTdbPath());
        final String[] elements  = singlePointSidebar.getElements();
        final String method      = singlePointSidebar.getMethod();
        final String[] phases    = singlePointSidebar.getPhases();
        final double T           = singlePointSidebar.getTemperature();
        final double P           = singlePointSidebar.getPressure();
        final ArrayList<ArrayList<Double>> compositions = singlePointSidebar.getCompositions();

        lastRunRequestSummary = "runId=" + runId + ", method=" + method
                + ", T=" + T + ", P=" + P + ", tdb=" + tdbPath;

        resultStatusLabel.setText("Running [" + runId + "]...");
        resultStatusLabel.setForeground(DarkTheme.ACCENT);
        addGuiLog("RESULT", "Run started: " + lastRunRequestSummary);

        singlePointSidebar.setRunning(true);
        presenters.runSinglePoint(tdbPath, elements, method, phases, T, P, compositions, t0);
    }

    void onSinglePointDone(EquilibriumResult result, long elapsed) {
        singlePointSidebar.setRunning(false);
        singlePointResultPanel.showResult(result, elapsed);
        resultStatusLabel.setText(result.isConverged() ? "✓ Equilibrium computation complete (" + elapsed + " ms)"
                : "✗ Not converged (" + elapsed + " ms)");
        resultStatusLabel.setForeground(result.isConverged() ? DarkTheme.SUCCESS : DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "Converged=" + result.isConverged() + " Iterations=" + result.getIterations());
    }

    void onSinglePointFailed(Exception ex) {
        singlePointSidebar.setRunning(false);
        resultStatusLabel.setText("Failed: " + ex.getMessage());
        resultStatusLabel.setForeground(DarkTheme.ERROR_COLOR);
        addGuiLog("ERROR", "Run crashed: " + ex.getMessage());
    }

    /** Responds to Single Point's "Reset Calculation Inputs" button; the shared TDB/elements/phases context is untouched. */
    private void onReset() {
        singlePointResultPanel.showEmpty("No run executed yet.");
        resultStatusLabel.setText("Calculation inputs reset.");
        resultStatusLabel.setForeground(DarkTheme.FG_SECOND);
        lastRunResult = null;
        addGuiLog("RESULT", "Calculation inputs reset to defaults (database/elements/phases unchanged).");
    }

    // ── STEP property scan ────────────────────────────────────────────

    void onCalculateStep() {
        final ui.request.PropertyScanRequest req = stepCalcPanel.buildRequest();
        stepCalcPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        stepCalcPanel.setRunning(true);
        presenters.runStep(req, stepCalcPanel);
    }

    void onStepDone(PropertyScanResult r) {
        stepCalcPanel.setRunning(false);
        stepResultPanel.setResult(r);
        if (r.isSuccess()) {
            stepCalcPanel.setStatus("✓ " + r.getMessage(), DarkTheme.SUCCESS);
            addGuiLog("RESULT", "STEP complete: " + r.getMessage());
        } else {
            String msg = r.getMessage();
            stepCalcPanel.setStatus(("Aborted".equals(msg) ? "⊘ Aborted" : "✗ " + msg), DarkTheme.ERROR_COLOR);
            addGuiLog("RESULT", "STEP: " + msg);
        }
    }

    void onStepAborted() {
        stepCalcPanel.setRunning(false);
        stepCalcPanel.setStatus("⊘ Aborted", DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "STEP aborted by user");
    }

    void onStepFailed(Exception ex) {
        stepCalcPanel.setRunning(false);
        stepCalcPanel.setStatus("✗ Error: " + ex.getMessage(), DarkTheme.ERROR_COLOR);
        addGuiLog("ERROR", "STEP exception: " + ex.getMessage());
    }

    void onStepProgress(String s) { addGuiLog("STEP", s); }

    // ── MAP property scan ─────────────────────────────────────────────

    void onCalculateMap() {
        final ui.request.PropertyScanRequest req = mapCalcPanel.buildRequest();
        mapCalcPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        mapCalcPanel.setRunning(true);
        presenters.runMap(req, mapCalcPanel);
    }

    void onMapDone(PhaseDiagramResult result) {
        mapCalcPanel.setRunning(false);
        if (result != null && result.isComplete()) {
            mapResultPanel.setDiagram(result);
            mapCalcPanel.setStatus("✓ Calculation complete", DarkTheme.SUCCESS);
            addGuiLog("RESULT", "MAP complete");
        } else {
            String msg = result != null ? result.getMessage() : "Unknown error";
            mapCalcPanel.setStatus("✗ " + msg, DarkTheme.ERROR_COLOR);
            addGuiLog("RESULT", "MAP: " + msg);
        }
    }

    void onMapAborted() {
        mapCalcPanel.setRunning(false);
        mapCalcPanel.setStatus("⊘ Aborted", DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "MAP aborted by user");
    }

    void onMapFailed(Exception ex) {
        mapCalcPanel.setRunning(false);
        mapCalcPanel.setStatus("✗ Error: " + ex.getMessage(), DarkTheme.ERROR_COLOR);
        addGuiLog("ERROR", "MAP exception: " + ex.getMessage());
    }

    // ── Phase boundary diagram ────────────────────────────────────────

    void onCalculatePhaseDiagram() {
        phaseDiagramConfigPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        phaseDiagramConfigPanel.setRunning(true);
        presenters.runPhaseDiagram(phaseDiagramConfigPanel.buildRequest(), phaseDiagramConfigPanel);
    }

    void onPhaseDiagramDone(PhaseDiagramResult result) {
        phaseDiagramConfigPanel.setRunning(false);
        if (result != null && result.isComplete()) {
            phaseDiagramPanel.setDiagram(result);
            phaseDiagramConfigPanel.setStatus("✓ Calculation complete", DarkTheme.SUCCESS);
            addGuiLog("RESULT", "Phase diagram calculated successfully");
        } else {
            String msg = result != null ? result.getMessage() : "Unknown error";
            phaseDiagramConfigPanel.setStatus("✗ " + msg, DarkTheme.ERROR_COLOR);
            addGuiLog("RESULT", "Phase diagram failed: " + msg);
        }
    }

    void onPhaseDiagramAborted() {
        phaseDiagramConfigPanel.setRunning(false);
        phaseDiagramConfigPanel.setStatus("⊘ Aborted", DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "Phase diagram aborted by user");
    }

    void onPhaseDiagramFailed(Exception e) {
        phaseDiagramConfigPanel.setRunning(false);
        phaseDiagramConfigPanel.setStatus("✗ Error: " + e.getMessage(), DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "Exception: " + e.getMessage());
    }

    // ── Coarse binary/ternary diagram ───────────────────────────────────

    void onCalculateCoarseDiagram() {
        final ui.request.PhaseDiagramRequest req = coarseDiagramConfigPanel.buildRequest();
        coarseDiagramConfigPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        coarseDiagramConfigPanel.setRunning(true);
        presenters.runCoarseDiagram(req, coarseDiagramConfigPanel);
    }

    void onCoarseProgress(String s) {
        addGuiLog("COARSE", s);
        coarseDiagramConfigPanel.setStatus(s, DarkTheme.ACCENT);
    }

    void onCoarseDone(ui.result.CoarseDiagramResult result) {
        coarseDiagramConfigPanel.setRunning(false);
        if (result != null) {
            coarseDiagramPanel.setDiagram(result);
            if (result.isComplete()) {
                coarseDiagramConfigPanel.setStatus("✓ Calculation complete", DarkTheme.SUCCESS);
                addGuiLog("RESULT", "Coarse diagram calculated successfully");
            } else {
                coarseDiagramConfigPanel.setStatus("⚠ " + result.getMessage(), DarkTheme.WARNING);
                addGuiLog("RESULT", "Coarse diagram completed with gaps: " + result.getMessage());
            }
        } else {
            coarseDiagramConfigPanel.setStatus("✗ Unknown error", DarkTheme.ERROR_COLOR);
            addGuiLog("RESULT", "Coarse diagram failed: unknown error");
        }
    }

    void onCoarseAborted() {
        coarseDiagramConfigPanel.setRunning(false);
        coarseDiagramConfigPanel.setStatus("⊘ Aborted", DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "Coarse diagram aborted by user");
    }

    void onCoarseFailed(Exception e) {
        coarseDiagramConfigPanel.setRunning(false);
        coarseDiagramConfigPanel.setStatus("✗ Error: " + e.getMessage(), DarkTheme.ERROR_COLOR);
        addGuiLog("RESULT", "Exception: " + e.getMessage());
    }

    // ── Inspector ─────────────────────────────────────────────────────

    private void onInspectModel() {
        ui.request.DatabaseSelection sel = modelInspectorSidebar.getSelection();
        String tdbPath = sel.getTdbPath();
        if (tdbPath == null || tdbPath.isEmpty()) {
            addGuiLog("RESULT", "No database loaded in inspector sidebar."); return;
        }
        addGuiLog("RESULT", "Inspecting: " + tdbPath + "  elements: " + sel.getElements());
        modelInspectorSidebar.showStatus("Loaded  [" + sel.getElements().size()
                + " el, " + sel.getAvailablePhases().size() + " ph]", DarkTheme.SUCCESS);
        refreshInspectorPhaseList(sel);
        paramArea.setText("Select a phase to view its parameters.");
    }

    private void refreshInspectorPhaseList(ui.request.DatabaseSelection sel) {
        currentInspectorTdb = sel.getTdbPath();
        java.util.List<String> phases = sel.getAvailablePhases();
        DefaultListModel<String> model = (DefaultListModel<String>) phaseJList.getModel();
        String prevSelected = phaseJList.getSelectedValue();
        model.clear();
        if (phases != null) for (String p : phases) model.addElement(p);
        if (prevSelected != null && phases != null && phases.contains(prevSelected))
            phaseJList.setSelectedValue(prevSelected, true);
        else
            paramArea.setText("Select a phase to view its parameters.");
        addGuiLog("RESULT", "Phases shown: " + (phases != null ? phases.size() : 0));
    }

    private void onPhaseSelected() {
        String phase = phaseJList.getSelectedValue();
        if (phase == null || currentInspectorTdb == null) return;
        java.util.List<String> elems = modelInspectorSidebar.getSelection().getElements();
        java.util.List<?> params = controller.getPhaseParameters(currentInspectorTdb, elems, phase);
        paramArea.setText(ResultFormatter.formatParamText(phase, params));
        paramArea.setCaretPosition(0);
    }

    // ── Export ────────────────────────────────────────────────────────

    private void onCopyLogs(ActionEvent e) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(logArea.getText()), null);
    }

    private void onExportCsv(ActionEvent e) {
        if (lastRunResult == null) { JOptionPane.showMessageDialog(this, "No result to export."); return; }
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setSelectedFile(new File("run-result.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ResultExporter.writeCsv(chooser.getSelectedFile(), lastRunResult);
            addGuiLog("RESULT", "CSV exported: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) { addGuiLog("ERROR", "CSV export failed: " + ex.getMessage()); }
    }

    private void onExportJson(ActionEvent e) {
        if (lastRunResult == null) { JOptionPane.showMessageDialog(this, "No result to export."); return; }
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setSelectedFile(new File("run-result.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ResultExporter.writeJson(chooser.getSelectedFile(), lastRunResult, lastRunRequestSummary);
            addGuiLog("RESULT", "JSON exported: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) { addGuiLog("ERROR", "JSON export failed: " + ex.getMessage()); }
    }

    // ── Logging ───────────────────────────────────────────────────────

    private void installSwingLogHandler() {
        swingLogHandler = new SwingLogHandler();
        swingLogHandler.setLevel(AppLevel.RESULT);
        Logger.getLogger("").addHandler(swingLogHandler);
    }

    private class SwingLogHandler extends Handler {
        private final Formatter fmt = new LoggingConfig.CompactFormatter();
        @Override public void publish(LogRecord record) {
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

    void addGuiLog(String level, String message) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        logLines.add("[" + level + "] " + ts + " " + level + " [GUI] " + message);
        refreshLogView();
    }

    private void refreshLogView() {
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) {
            int cb = line.indexOf(']');
            sb.append(cb >= 0 ? line.substring(cb + 2) : line).append("\n");
        }
        if (logArea != null) {
            logArea.setText(sb.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String normalizePath(String rawPath) {
        File f = new File(rawPath);
        if (f.isAbsolute()) return f.getPath();
        return new File(System.getProperty("user.dir"), rawPath).getPath();
    }

    private String toRelativeIfPossible(File file) {
        File cwd = new File(System.getProperty("user.dir"));
        try {
            String abs = file.getCanonicalPath(), cwdAbs = cwd.getCanonicalPath();
            if (abs.startsWith(cwdAbs)) {
                String rel = abs.substring(cwdAbs.length());
                if (rel.startsWith("\\") || rel.startsWith("/")) rel = rel.substring(1);
                return rel.replace('\\', '/');
            }
            return abs;
        } catch (IOException ex) { return file.getPath(); }
    }

    private void styleMenu(JMenu menu) {
        menu.setBackground(DarkTheme.MENU_BG);
        menu.setForeground(DarkTheme.FG_PRIMARY);
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(DarkTheme.MENU_BG);
        item.setForeground(DarkTheme.FG_PRIMARY);
    }
}
