package ui.gui;

import ui.layer.SinglePointUseCase;
import ui.layer.OptimizationUseCase;
import ui.layer.PhaseDiagramUseCase;
import ui.layer.ModelInspectionService;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GUI entry point for the application.
 * Receives configured use-case objects from the composition root (Main).
 */
public class GuiApp {

    private final SinglePointUseCase singlePointUseCase;
    private final OptimizationUseCase optimizationUseCase;
    private final PhaseDiagramUseCase phaseDiagramUseCase;
    private final ModelInspectionService modelInspectionService;

    public GuiApp(SinglePointUseCase singlePointUseCase, OptimizationUseCase optimizationUseCase,
                  PhaseDiagramUseCase phaseDiagramUseCase, ModelInspectionService modelInspectionService) {
        this.singlePointUseCase = singlePointUseCase;
        this.optimizationUseCase = optimizationUseCase;
        this.phaseDiagramUseCase = phaseDiagramUseCase;
        this.modelInspectionService = modelInspectionService;
    }

    /**
     * Launch the GUI on the Swing Event Dispatch Thread.
     */
    public void launch(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Use cross-platform Metal L&F (respects UIManager overrides)
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                // Apply VS Code dark theme
                DarkTheme.apply();
            } catch (Exception e) {
                // fall back to default look and feel
            }
            MainController controller = new MainController(singlePointUseCase, optimizationUseCase,
                                                          phaseDiagramUseCase, modelInspectionService);
            MainFrame frame = new MainFrame(controller);
            frame.setVisible(true);
        });
    }
}
