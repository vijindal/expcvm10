package ui.gui;

import database.tdb;
import service.CalculationResult;
import service.PhaseDiagramResult;
import service.PropertyScanResult;
import infra.AppLevel;
import infra.LoggingConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
 * VS Code-style main window for expCVM 10.
 *
 * Activity bar (WEST): Single Point | STEP Calc | MAP Calc | Phase Diagram | Inspect
 * Sidebar (LEFT, 280 px): CardLayout — activity-specific config
 * Editor  (RIGHT, flex) : CardLayout — activity-specific results
 * Log console (SOUTH)   : OUTPUT panel
 */
public class MainFrame extends JFrame {

    // ── Theme shortcuts ──────────────────────────────────────────────
    private static final Color BG          = DarkTheme.BG;
    private static final Color SUCCESS     = DarkTheme.SUCCESS;
    private static final Color ERROR_COLOR = DarkTheme.ERROR_COLOR;

    private final MainController controller;

    // ── Activity bar ─────────────────────────────────────────────────
    private ActivityBar activityBar;

    // ── Sidebar (CardLayout) ─────────────────────────────────────────
    private JPanel sidebarCard;
    private SinglePointSidebarPanel    singlePointSidebar;
    private PropertyCalcConfigPanel    stepCalcPanel;
    private PropertyCalcConfigPanel    mapCalcPanel;
    private PhaseDiagramConfigPanel    phaseDiagramConfigPanel;
    private SwingWorker<?,?>           stepWorker;
    private SwingWorker<?,?>           mapWorker;
    private ModelInspectorSidebarPanel modelInspectorSidebar;

    // ── Editor (CardLayout) ──────────────────────────────────────────
    private JPanel           editorCard;
    private JTextArea        resultSummaryArea;
    private JLabel           resultStatusLabel;
    private StepResultPanel  stepResultPanel;
    private MapResultPanel   mapResultPanel;
    private PhaseDiagramPanel phaseDiagramPanel;
    private JList<String>    phaseJList;
    private JTextArea        paramArea;
    private String           currentInspectorTdb;

    // ── Log console ──────────────────────────────────────────────────
    private JTextArea logArea;
    private JComboBox<String> logLevelCombo;
    private final ArrayList<String> logLines = new ArrayList<>();
    private SwingLogHandler swingLogHandler;

    // ── State ────────────────────────────────────────────────────────
    private String            lastRunRequestSummary = "";
    private CalculationResult lastRunResult;

    // ─────────────────────────────────────────────────────────────────

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
        // Sidebars
        singlePointSidebar = new SinglePointSidebarPanel(controller);
        singlePointSidebar.setRunCallback(this::onRunCalculation);
        singlePointSidebar.setResetCallback(this::onReset);

        stepCalcPanel = new PropertyCalcConfigPanel(controller, false);
        stepCalcPanel.setCalculateCallback(this::onCalculateStep);
        stepCalcPanel.setAbortCallback(() -> { if (stepWorker != null) stepWorker.cancel(true); });

        mapCalcPanel = new PropertyCalcConfigPanel(controller, true);
        mapCalcPanel.setCalculateCallback(this::onCalculateMap);
        mapCalcPanel.setAbortCallback(() -> { if (mapWorker != null) mapWorker.cancel(true); });

        phaseDiagramConfigPanel = new PhaseDiagramConfigPanel(controller);
        phaseDiagramConfigPanel.setCalculateCallback(this::onCalculatePhaseDiagram);

        modelInspectorSidebar = new ModelInspectorSidebarPanel(controller);
        modelInspectorSidebar.setInspectCallback(this::onInspectModel);
        modelInspectorSidebar.setOnSelectionChanged(sel -> {
            if (sel.hasElements()) refreshInspectorPhaseList(sel);
        });

        sidebarCard = new JPanel(new CardLayout());
        sidebarCard.setPreferredSize(new Dimension(280, 0));
        sidebarCard.setMinimumSize(new Dimension(200, 0));
        sidebarCard.add(singlePointSidebar,     "singlepoint");
        sidebarCard.add(stepCalcPanel,           "stepcalc");
        sidebarCard.add(mapCalcPanel,            "mapcalc");
        sidebarCard.add(phaseDiagramConfigPanel, "phasediagram");
        sidebarCard.add(modelInspectorSidebar,   "inspector");

        // Editors
        editorCard = new JPanel(new CardLayout());
        editorCard.add(buildSinglePointEditor(), "singlepoint");
        editorCard.add(buildStepEditor(),        "stepcalc");
        editorCard.add(buildMapEditor(),         "mapcalc");
        editorCard.add(buildPhaseDiagramEditor(),"phasediagram");
        editorCard.add(buildInspectorEditor(),   "inspector");

        // Log panel
        JPanel logPanel = buildLogPanel();
        logPanel.setPreferredSize(new Dimension(0, 150));

        JSplitPane editorWithLog = DarkTheme.sleekSplit(JSplitPane.VERTICAL_SPLIT);
        editorWithLog.setTopComponent(editorCard);
        editorWithLog.setBottomComponent(logPanel);
        editorWithLog.setResizeWeight(0.80);

        JSplitPane workspaceSplit = DarkTheme.sleekSplit(JSplitPane.HORIZONTAL_SPLIT);
        workspaceSplit.setLeftComponent(sidebarCard);
        workspaceSplit.setRightComponent(editorWithLog);
        workspaceSplit.setDividerLocation(280);
        workspaceSplit.setResizeWeight(0.0);

        // Activity bar — 5 activities
        activityBar = new ActivityBar();
        activityBar.addActivity("Single Point",   new ActivityBar.CircleIcon(),   () -> switchActivity("singlepoint"));
        activityBar.addActivity("STEP Calc",      new ActivityBar.LineIcon(),     () -> switchActivity("stepcalc"));
        activityBar.addActivity("MAP Calc",       new ActivityBar.SquareIcon(),   () -> switchActivity("mapcalc"));
        activityBar.addActivity("Phase Diagram",  new ActivityBar.DiamondIcon(),  () -> switchActivity("phasediagram"));
        activityBar.addActivity("Inspect Model",  new ActivityBar.InfoIcon(),     () -> switchActivity("inspector"));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.add(activityBar,    BorderLayout.WEST);
        root.add(workspaceSplit, BorderLayout.CENTER);
        return root;
    }

    private void switchActivity(String key) {
        ((CardLayout) sidebarCard.getLayout()).show(sidebarCard, key);
        ((CardLayout) editorCard.getLayout()).show(editorCard, key);
    }

    // ================================================================
    //  EDITOR PANELS
    // ================================================================

    private JComponent buildSinglePointEditor() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG);

        // Status label replaces the JProgressBar
        resultStatusLabel = new JLabel("No run executed yet.");
        resultStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        resultStatusLabel.setForeground(DarkTheme.FG_SECOND);
        resultStatusLabel.setBorder(new EmptyBorder(3, 8, 3, 8));
        resultStatusLabel.setOpaque(true);
        resultStatusLabel.setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout(0, 0));
        topBar.setOpaque(false);
        topBar.add(DarkTheme.panelHeader("RESULTS"), BorderLayout.NORTH);
        topBar.add(resultStatusLabel, BorderLayout.CENTER);
        panel.add(topBar, BorderLayout.NORTH);

        resultSummaryArea = new JTextArea("No run executed yet.");
        resultSummaryArea.setEditable(false);
        resultSummaryArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        resultSummaryArea.setBackground(BG);
        resultSummaryArea.setForeground(DarkTheme.FG_PRIMARY);
        resultSummaryArea.setCaretColor(DarkTheme.FG_PRIMARY);
        resultSummaryArea.setSelectionColor(DarkTheme.SEL_BG);
        panel.add(DarkTheme.scrollPane(resultSummaryArea), BorderLayout.CENTER);
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
        mapResultPanel = new MapResultPanel();
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

    private JComponent buildInspectorEditor() {
        phaseJList = new JList<>(new DefaultListModel<>());
        phaseJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        phaseJList.setFont(new Font("Consolas", Font.PLAIN, 11));
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

        paramArea = new JTextArea("Add elements in the sidebar, then select a phase.");
        paramArea.setEditable(false);
        paramArea.setFont(new Font("Consolas", Font.PLAIN, 11));
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

        JLabel levelLabel = new JLabel("Level:");
        levelLabel.setForeground(DarkTheme.FG_SECOND);
        levelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.setOpaque(false);
        toolbar.add(levelLabel);

        logLevelCombo = new JComboBox<>(new String[]{"ERROR","WARN","RESULT","FLOW","ENGINE","MODEL","SOLVER","ALL"});
        logLevelCombo.setSelectedItem("RESULT");
        logLevelCombo.setBackground(DarkTheme.BG_INPUT);
        logLevelCombo.setForeground(DarkTheme.FG_PRIMARY);
        logLevelCombo.setRenderer(new DarkTheme.ComboRenderer());
        logLevelCombo.addActionListener(e -> onLogLevelChanged());
        toolbar.add(logLevelCombo);

        JButton copyBtn = smallButton("Copy");
        copyBtn.addActionListener(this::onCopyLogs);
        toolbar.add(copyBtn);

        JButton clearBtn = smallButton("Clear");
        clearBtn.addActionListener(e -> { logLines.clear(); logArea.setText(""); });
        toolbar.add(clearBtn);

        outer.add(DarkTheme.panelHeader("OUTPUT", toolbar), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
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
                switchActivity("singlepoint");
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

        new SwingWorker<CalculationResult, Void>() {
            @Override protected CalculationResult doInBackground() {
                return controller.runSinglePoint(tdbPath, elements, method, phases, T, P, compositions);
            }
            @Override protected void done() {
                long elapsed = System.currentTimeMillis() - t0;
                try {
                    renderRunResult(get(), elapsed);
                } catch (Exception ex) {
                    resultStatusLabel.setText("Failed: " + ex.getMessage());
                    resultStatusLabel.setForeground(ERROR_COLOR);
                    addGuiLog("ERROR", "Run crashed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void onReset() {
        String defaultTdb = "data/tizr_kum_cvm.tdb";
        singlePointSidebar.resetDefaults(defaultTdb);
        resultSummaryArea.setText("No run executed yet.");
        resultStatusLabel.setText("Reset.");
        resultStatusLabel.setForeground(DarkTheme.FG_SECOND);
        lastRunResult = null;
        addGuiLog("RESULT", "Inputs reset to defaults.");
    }

    // ── STEP property scan ────────────────────────────────────────────

    private void onCalculateStep() {
        final service.PropertyScanRequest req = stepCalcPanel.buildRequest();
        stepCalcPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        stepCalcPanel.setRunning(true);
        stepWorker = new SwingWorker<PropertyScanResult, String>() {
            @Override protected PropertyScanResult doInBackground() {
                req.setProgressCallback(this::publish);
                return controller.runPropertyScan(req);
            }
            @Override protected void process(java.util.List<String> chunks) {
                for (String s : chunks) addGuiLog("STEP", s);
            }
            @Override protected void done() {
                stepCalcPanel.setRunning(false);
                stepWorker = null;
                try {
                    PropertyScanResult r = get();
                    stepResultPanel.setResult(r);
                    if (r.isSuccess()) {
                        stepCalcPanel.setStatus("✓ " + r.getMessage(), SUCCESS);
                        addGuiLog("RESULT", "STEP complete: " + r.getMessage());
                    } else {
                        String msg = r.getMessage();
                        stepCalcPanel.setStatus(("Aborted".equals(msg) ? "⊘ Aborted" : "✗ " + msg), ERROR_COLOR);
                        addGuiLog("RESULT", "STEP: " + msg);
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    stepCalcPanel.setStatus("⊘ Aborted", ERROR_COLOR);
                    addGuiLog("RESULT", "STEP aborted by user");
                } catch (Exception ex) {
                    stepCalcPanel.setStatus("✗ Error: " + ex.getMessage(), ERROR_COLOR);
                    addGuiLog("ERROR", "STEP exception: " + ex.getMessage());
                }
            }
        };
        stepWorker.execute();
    }

    // ── MAP property scan ─────────────────────────────────────────────

    private void onCalculateMap() {
        final service.PropertyScanRequest req = mapCalcPanel.buildRequest();
        mapCalcPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        mapCalcPanel.setRunning(true);
        mapWorker = new SwingWorker<PropertyScanResult, String>() {
            @Override protected PropertyScanResult doInBackground() {
                req.setProgressCallback(this::publish);
                return controller.runPropertyScan(req);
            }
            @Override protected void process(java.util.List<String> chunks) {
                for (String s : chunks) addGuiLog("MAP", s);
            }
            @Override protected void done() {
                mapCalcPanel.setRunning(false);
                mapWorker = null;
                try {
                    PropertyScanResult r = get();
                    mapResultPanel.setResult(r);
                    if (r.isSuccess()) {
                        mapCalcPanel.setStatus("✓ " + r.getMessage(), SUCCESS);
                        addGuiLog("RESULT", "MAP complete: " + r.getMessage());
                    } else {
                        String msg = r.getMessage();
                        mapCalcPanel.setStatus(("Aborted".equals(msg) ? "⊘ Aborted" : "✗ " + msg), ERROR_COLOR);
                        addGuiLog("RESULT", "MAP: " + msg);
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    mapCalcPanel.setStatus("⊘ Aborted", ERROR_COLOR);
                    addGuiLog("RESULT", "MAP aborted by user");
                } catch (Exception ex) {
                    mapCalcPanel.setStatus("✗ Error: " + ex.getMessage(), ERROR_COLOR);
                    addGuiLog("ERROR", "MAP exception: " + ex.getMessage());
                }
            }
        };
        mapWorker.execute();
    }

    // ── Phase boundary diagram ────────────────────────────────────────

    private void onCalculatePhaseDiagram() {
        phaseDiagramConfigPanel.setStatus("Calculating...", DarkTheme.ACCENT);
        phaseDiagramConfigPanel.setRunning(true);
        SwingWorker<PhaseDiagramResult, Void> worker = new SwingWorker<PhaseDiagramResult, Void>() {
            @Override protected PhaseDiagramResult doInBackground() {
                return controller.runPhaseDiagram(phaseDiagramConfigPanel.buildRequest());
            }
            @Override protected void done() {
                phaseDiagramConfigPanel.setRunning(false);
                try {
                    PhaseDiagramResult result = get();
                    if (result != null && result.isComplete()) {
                        phaseDiagramPanel.setDiagram(result);
                        phaseDiagramConfigPanel.setStatus("✓ Calculation complete", SUCCESS);
                        addGuiLog("RESULT", "Phase diagram calculated successfully");
                    } else {
                        String msg = result != null ? result.getMessage() : "Unknown error";
                        phaseDiagramConfigPanel.setStatus("✗ " + msg, ERROR_COLOR);
                        addGuiLog("RESULT", "Phase diagram failed: " + msg);
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    phaseDiagramConfigPanel.setStatus("⊘ Aborted", ERROR_COLOR);
                    addGuiLog("RESULT", "Phase diagram aborted by user");
                } catch (Exception e) {
                    phaseDiagramConfigPanel.setStatus("✗ Error: " + e.getMessage(), ERROR_COLOR);
                    addGuiLog("RESULT", "Exception: " + e.getMessage());
                }
            }
        };
        phaseDiagramConfigPanel.setAbortCallback(() -> worker.cancel(true));
        worker.execute();
    }

    // ── Inspector ─────────────────────────────────────────────────────

    private void onInspectModel() {
        service.DatabaseSelection sel = modelInspectorSidebar.getSelection();
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

    private void refreshInspectorPhaseList(service.DatabaseSelection sel) {
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
        java.util.List<tdb.Parameter> params = controller.getPhaseParameters(currentInspectorTdb, elems, phase);
        paramArea.setText(buildParamText(phase, params));
        paramArea.setCaretPosition(0);
    }

    private String buildParamText(String phaseName, java.util.List<tdb.Parameter> params) {
        if (params == null || params.isEmpty()) return "No parameters found for " + phaseName + ".";
        String[] termLabels = {"constant","*T","*T·ln(T)","*T²","*T³","*T⁴","*T⁷","*T⁻¹","*T⁻²","*T⁻³","*T⁻⁹","*T⁻¹¹"};
        StringBuilder sb = new StringBuilder();
        sb.append("Parameters for ").append(phaseName).append("  (").append(params.size()).append(" total)\n");
        sb.append("─".repeat(60)).append("\n");
        for (tdb.Parameter p : params) {
            StringBuilder header = new StringBuilder();
            header.append(p.getType()).append("(").append(phaseName).append(",  ");
            java.util.List<java.util.ArrayList<String>> constit = p.getConstituentList();
            if (constit != null) {
                for (int s = 0; s < constit.size(); s++) {
                    if (s > 0) header.append(":");
                    header.append(String.join(",", constit.get(s)));
                }
            }
            header.append(";").append(p.getOrder()).append(")");
            sb.append(header).append("\n");
            java.util.ArrayList<tdb.Exp> exps = p.getExpList();
            if (exps != null) {
                for (tdb.Exp exp : exps) {
                    java.util.ArrayList<Double> tRange = exp.getTempRange();
                    if (tRange != null && tRange.size() >= 2)
                        sb.append(String.format("  %.2f – %.2f K\n", tRange.get(0), tRange.get(1)));
                    java.util.ArrayList<Double> coeffs = exp.getSubCoeffList();
                    if (coeffs != null) {
                        for (int i = 0; i < coeffs.size(); i++) {
                            double c = coeffs.get(i);
                            if (c == 0.0) continue;
                            String label = i < termLabels.length ? termLabels[i] : "*T^" + i;
                            sb.append(String.format("    %+.6e  (%s)\n", c, label));
                        }
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Assessment menu ───────────────────────────────────────────────

    private void onMenuCalModel(ActionEvent e) {
        JTextField exptField  = new JTextField("data/ExptData.txt", 25);
        JTextField phaseField = new JTextField("data/PhaseData.txt", 25);
        JPanel dlg = new JPanel(new GridLayout(2, 2, 6, 6));
        dlg.add(new JLabel("Experimental Data File:")); dlg.add(exptField);
        dlg.add(new JLabel("Phase Data File:"));        dlg.add(phaseField);
        if (JOptionPane.showConfirmDialog(this, dlg, "Run CalModel",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        final String expt  = normalizePath(exptField.getText().trim());
        final String phase = normalizePath(phaseField.getText().trim());
        if (!new File(expt).exists()) { JOptionPane.showMessageDialog(this, "File not found:\n" + expt); return; }
        if (!new File(phase).exists()){ JOptionPane.showMessageDialog(this, "File not found:\n" + phase); return; }
        resultStatusLabel.setText("CalModel running...");
        resultStatusLabel.setForeground(DarkTheme.ACCENT);
        switchActivity("singlepoint");
        new SwingWorker<CalculationResult, Void>() {
            @Override protected CalculationResult doInBackground() { return controller.runCalModel(expt, phase); }
            @Override protected void done() {
                resultStatusLabel.setText("CalModel done.");
                try { renderRunResult(get(), -1); } catch (Exception ex) { addGuiLog("ERROR", "CalModel failed: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void onMenuOptimize(ActionEvent e) {
        JTextField exptField   = new JTextField("data/ExptData.txt", 25);
        JTextField phaseField  = new JTextField("data/PhaseData.txt", 25);
        JTextField prefixField = new JTextField("data/log", 25);
        JTextField iterField   = new JTextField("50", 10);
        JPanel dlg = new JPanel(new GridLayout(4, 2, 6, 6));
        dlg.add(new JLabel("Experimental Data File:")); dlg.add(exptField);
        dlg.add(new JLabel("Phase Data File:"));        dlg.add(phaseField);
        dlg.add(new JLabel("Output Prefix:"));          dlg.add(prefixField);
        dlg.add(new JLabel("Max Iterations:"));         dlg.add(iterField);
        if (JOptionPane.showConfirmDialog(this, dlg, "Run Optimization",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        final String expt   = normalizePath(exptField.getText().trim());
        final String phase  = normalizePath(phaseField.getText().trim());
        final String prefix = normalizePath(prefixField.getText().trim());
        if (!new File(expt).exists()) { JOptionPane.showMessageDialog(this, "File not found:\n" + expt); return; }
        int maxIter;
        try { maxIter = Integer.parseInt(iterField.getText().trim()); if (maxIter <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Max iterations must be a positive integer."); return; }
        final int maxIterations = maxIter;
        resultStatusLabel.setText("Optimization running...");
        resultStatusLabel.setForeground(DarkTheme.ACCENT);
        switchActivity("singlepoint");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return controller.runOptimization(expt, phase, prefix, maxIterations); }
            @Override protected void done() {
                resultStatusLabel.setText("Optimization done.");
                resultStatusLabel.setForeground(DarkTheme.FG_SECOND);
                try {
                    String msg = get();
                    addGuiLog(msg.toLowerCase().contains("success") ? "RESULT" : "ERROR", msg);
                } catch (Exception ex) { addGuiLog("ERROR", "Optimization failed: " + ex.getMessage()); }
            }
        }.execute();
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
        try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
            fw.write("Metric,Value,Unit\n");
            fw.write("Success," + lastRunResult.isSuccess() + ",-\n");
            fw.write(csvEscape("Method") + "," + csvEscape(safe(lastRunResult.getMethod())) + ",-\n");
            fw.write(csvEscape("Message") + "," + csvEscape(safe(lastRunResult.getMessage())) + ",-\n");
            fw.write("Value," + lastRunResult.getValue() + ",J/mol\n");
            fw.write("Temperature," + lastRunResult.getTemperature() + ",K\n");
            fw.write("Pressure," + lastRunResult.getPressure() + ",Pa\n");
            fw.write("Composition," + csvEscape(formatComposition(lastRunResult.getCompositionResult())) + ",-\n");
            addGuiLog("RESULT", "CSV exported: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) { addGuiLog("ERROR", "CSV export failed: " + ex.getMessage()); }
    }

    private void onExportJson(ActionEvent e) {
        if (lastRunResult == null) { JOptionPane.showMessageDialog(this, "No result to export."); return; }
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setSelectedFile(new File("run-result.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
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

    private void addGuiLog(String level, String message) {
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

    // ── Result rendering ──────────────────────────────────────────────

    private void renderRunResult(CalculationResult result, long elapsedMillis) {
        lastRunResult = result;
        StringBuilder sb = new StringBuilder();
        sb.append("Status   : ").append(result.isSuccess() ? "SUCCESS" : "FAILED").append("\n");
        sb.append("Method   : ").append(safe(result.getMethod())).append("\n");
        sb.append("Value    : ").append(result.getValue()).append(" J/mol\n");
        sb.append("Temp     : ").append(result.getTemperature()).append(" K\n");
        sb.append("Pressure : ").append(result.getPressure()).append(" Pa\n");
        sb.append("Comp     : ").append(formatComposition(result.getCompositionResult())).append("\n");
        if (elapsedMillis >= 0) sb.append("Duration : ").append(elapsedMillis).append(" ms\n");
        sb.append("\n").append(safe(result.getMessage()));
        resultSummaryArea.setText(sb.toString());
        resultStatusLabel.setText(result.isSuccess() ? "Done (" + elapsedMillis + " ms)" : "Failed");
        resultStatusLabel.setForeground(result.isSuccess() ? SUCCESS : ERROR_COLOR);
        addGuiLog(result.isSuccess() ? "RESULT" : "ERROR",
                  "Run " + (result.isSuccess() ? "completed" : "failed") + ": " + safe(result.getMessage()));
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

    private String csvEscape(Object val) {
        String s = String.valueOf(val);
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
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

    private void styleMenu(JMenu menu) {
        menu.setBackground(DarkTheme.MENU_BG);
        menu.setForeground(DarkTheme.FG_PRIMARY);
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(DarkTheme.MENU_BG);
        item.setForeground(DarkTheme.FG_PRIMARY);
    }
}
