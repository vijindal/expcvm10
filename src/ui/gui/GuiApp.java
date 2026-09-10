package ui.gui;

import ui.layer.OptimizationUseCase;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GUI entry point for the application.
 *
 * <p>Only {@link OptimizationUseCase} is still injected -- the GUI's
 * calculation and browsing paths go through {@code CalculationSession}
 * (held inside {@link MainController}), per the target data flow. See
 * {@code MainController} for the paths still pending that wiring.
 */
public class GuiApp {

    private final OptimizationUseCase optimizationUseCase;

    public GuiApp(OptimizationUseCase optimizationUseCase) {
        this.optimizationUseCase = optimizationUseCase;
    }

    /**
     * Launch the GUI on the Swing Event Dispatch Thread.
     */
    public void launch(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                DarkTheme.apply();
            } catch (Exception e) {
                // fall back to default look and feel
            }
            MainController controller = new MainController(optimizationUseCase);
            MainFrame frame = new MainFrame(controller);
            frame.setVisible(true);
        });
    }
}
