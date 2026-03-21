package gui;

import service.CalculationService;
import service.OptimizationService;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GUI entry point for the application.
 * Receives configured use-case objects from the composition root (Main).
 */
public class GuiApp {

    private final CalculationService calculationService;
    private final OptimizationService optimizationService;

    public GuiApp(CalculationService calculationService, OptimizationService optimizationService) {
        this.calculationService = calculationService;
        this.optimizationService = optimizationService;
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
            MainController controller = new MainController(calculationService, optimizationService);
            MainFrame frame = new MainFrame(controller);
            frame.setVisible(true);
        });
    }
}
