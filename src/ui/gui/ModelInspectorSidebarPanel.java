package ui.gui;

import service.DatabaseSelection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Sidebar panel for the Model Inspector activity.
 *
 * Top zone: shared {@link DatabaseExtractionPanel} (TDB + element selection).
 * Bottom zone: Inspect button + status label.
 */
public class ModelInspectorSidebarPanel extends JPanel {

    private final DatabaseExtractionPanel dbPanel;
    private JLabel  statusLabel;
    private Runnable inspectCallback;

    public ModelInspectorSidebarPanel(MainController controller) {
        setLayout(new BorderLayout());
        setBackground(DarkTheme.SIDEBAR_BG);

        dbPanel = new DatabaseExtractionPanel(controller);
        dbPanel.setDefaults("data/tizr_kum_cvm.tdb", null);

        JPanel bottom = buildBottom();

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(dbPanel, BorderLayout.CENTER);
        top.add(DarkTheme.separator(), BorderLayout.SOUTH);

        add(top,    BorderLayout.NORTH);
        add(bottom, BorderLayout.CENTER);
    }

    private JPanel buildBottom() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DarkTheme.SIDEBAR_BG);
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 2, 4, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        // Inspect button
        JButton inspectBtn = new JButton("Inspect Database");
        inspectBtn.setBackground(DarkTheme.ACCENT);
        inspectBtn.setForeground(DarkTheme.FG_PRIMARY);
        inspectBtn.setFocusPainted(false);
        inspectBtn.setBorderPainted(false);
        inspectBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        inspectBtn.setMargin(new Insets(6, 12, 6, 12));
        inspectBtn.addActionListener(e -> { if (inspectCallback != null) inspectCallback.run(); });
        g.gridx = 0; g.gridy = 0; g.gridwidth = 1; g.weightx = 1;
        panel.add(inspectBtn, g);

        // Status
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        statusLabel.setForeground(DarkTheme.FG_SECOND);
        g.gridx = 0; g.gridy = 1;
        panel.add(statusLabel, g);

        return panel;
    }

    // ── Public API ─────────────────────────────────────────────────────

    public void setInspectCallback(Runnable r)    { this.inspectCallback = r; }

    public void setOnSelectionChanged(java.util.function.Consumer<DatabaseSelection> cb) {
        dbPanel.setOnSelectionChanged(cb);
    }

    public DatabaseSelection getSelection() { return dbPanel.getSelection(); }

    public String getTdbPath() {
        DatabaseSelection sel = dbPanel.getSelection();
        return sel.getTdbPath() != null ? sel.getTdbPath() : "";
    }

    public void showStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    public void showStatus(service.ModelInfo info) {
        if (info == null) { statusLabel.setText("No info"); return; }
        if (info.isFileExists()) {
            int nPhases = info.getAvailablePhases() != null ? info.getAvailablePhases().size() : 0;
            int nElems  = info.getAvailableElements() != null ? info.getAvailableElements().size() : 0;
            statusLabel.setText(new File(info.getFilePath()).getName()
                    + "  [" + nElems + " el, " + nPhases + " ph]");
            statusLabel.setForeground(DarkTheme.SUCCESS);
        } else {
            statusLabel.setText("File not found");
            statusLabel.setForeground(DarkTheme.ERROR_COLOR);
        }
    }
}
