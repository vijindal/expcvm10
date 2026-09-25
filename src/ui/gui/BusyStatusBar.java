package ui.gui;

import javax.swing.*;
import java.awt.*;

/** Reusable status row: a status label, an indeterminate progress bar, and an Abort button, all shown only while running. */
public class BusyStatusBar extends JPanel {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JButton abortButton;

    private Runnable onAbort;

    public BusyStatusBar() {
        setOpaque(false);
        setLayout(new BorderLayout(DarkTheme.SPACE_SM, 0));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(DarkTheme.FONT_HINT);
        statusLabel.setForeground(DarkTheme.FG_SECOND);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(80, 6));
        progressBar.setBorderPainted(false);

        abortButton = DarkTheme.smallButton("Abort");
        abortButton.setForeground(DarkTheme.ERROR_COLOR);
        abortButton.setVisible(false);
        abortButton.addActionListener(e -> { if (onAbort != null) onAbort.run(); });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, DarkTheme.SPACE_SM, 0));
        left.setOpaque(false);
        left.add(statusLabel);
        left.add(progressBar);

        add(left, BorderLayout.WEST);
        add(abortButton, BorderLayout.EAST);
    }

    /** Registers the action to run when Abort is clicked; pass null if this run can't be aborted. */
    public void setAbortCallback(Runnable onAbort) {
        this.onAbort = onAbort;
        abortButton.setVisible(false);
    }

    /** Enters or leaves the busy state, showing the progress bar and (if set) the Abort button. */
    public void setRunning(boolean running, String runningMessage) {
        progressBar.setIndeterminate(running);
        progressBar.setVisible(running);
        abortButton.setVisible(running && onAbort != null);
        if (running) {
            statusLabel.setText(runningMessage);
            statusLabel.setForeground(DarkTheme.ACCENT);
        }
    }

    public void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
}
