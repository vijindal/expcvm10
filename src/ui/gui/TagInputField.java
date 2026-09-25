package ui.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Badge-style tag input: a text field + "Add" button that turns confirmed
 * entries into removable badges, validated against an optional known-value
 * list (with Tab-complete and "did you mean" suggestions).
 */
public class TagInputField extends JPanel {

    private final JTextField inputField;
    private final JPanel     badgePanel;
    private final JLabel     hintLabel;
    private final JLabel     emptyStateLabel;
    private final boolean    uppercase;

    private List<String> knownValues = new ArrayList<>();
    private Runnable onChanged;

    /**
     * @param placeholderHint hint text shown below the input row when idle
     * @param uppercase       true to normalize typed tokens to upper case
     *                        (element/phase symbols); false to keep as typed
     */
    public TagInputField(String placeholderHint, boolean uppercase) {
        this.uppercase = uppercase;
        setLayout(new BorderLayout(0, DarkTheme.SPACE_XS));
        setOpaque(false);

        hintLabel = DarkTheme.hintLabel(placeholderHint);

        inputField = DarkTheme.textField("");
        inputField.setToolTipText("Type a value, then Enter or Add (Tab to autocomplete)");
        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) onAdd();
                if (e.getKeyCode() == KeyEvent.VK_TAB)  onTabComplete(e);
            }
        });

        JButton addBtn = DarkTheme.smallButton("Add");
        addBtn.addActionListener(e -> onAdd());

        JPanel inputRow = new JPanel(new BorderLayout(DarkTheme.SPACE_SM, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(addBtn, BorderLayout.EAST);

        badgePanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        badgePanel.setOpaque(false);

        // Shown only while no badges are present.
        emptyStateLabel = new JLabel(" ");
        emptyStateLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        emptyStateLabel.setForeground(DarkTheme.FG_SECOND);
        emptyStateLabel.setBorder(BorderFactory.createEmptyBorder(DarkTheme.SPACE_XS, 0, 0, 0));
        emptyStateLabel.setVisible(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(hintLabel, BorderLayout.NORTH);
        top.add(inputRow,  BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(badgePanel, BorderLayout.NORTH);
        center.add(emptyStateLabel, BorderLayout.SOUTH);

        add(top,    BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    // ── Behaviour ─────────────────────────────────────────────────────

    private void onAdd() {
        String raw = inputField.getText().trim();
        if (raw.isEmpty()) return;
        String value = uppercase ? raw.toUpperCase() : raw;

        if (!knownValues.isEmpty() && !knownValues.contains(value)) {
            String hint = suggest(value);
            hintLabel.setText("Unknown: " + value + (hint != null ? "  Try: " + hint : ""));
            hintLabel.setForeground(DarkTheme.ERROR_COLOR);
            inputField.setForeground(DarkTheme.ERROR_COLOR);
            return;
        }
        if (getValues().contains(value)) {
            inputField.setText("");
            return;
        }

        addBadge(value);
        inputField.setText("");
        inputField.setForeground(DarkTheme.FG_PRIMARY);
        resetHint();
        badgePanel.revalidate();
        badgePanel.repaint();
        fireChanged();
    }

    private void onTabComplete(KeyEvent e) {
        String prefix = inputField.getText().trim();
        String prefixCmp = uppercase ? prefix.toUpperCase() : prefix;
        if (prefixCmp.isEmpty()) return;
        for (String v : knownValues) {
            if (v.startsWith(prefixCmp) && !v.equals(prefixCmp)) {
                inputField.setText(v);
                inputField.setForeground(DarkTheme.FG_PRIMARY);
                e.consume();
                return;
            }
        }
    }

    private String suggest(String input) {
        for (String v : knownValues) {
            if (v.startsWith(input) || v.contains(input)) return v;
        }
        return null;
    }

    private void addBadge(String value) {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        badge.setBackground(DarkTheme.BG_INPUT);
        badge.setBorder(BorderFactory.createLineBorder(DarkTheme.ACCENT, 1));

        JLabel nameLbl = new JLabel(value);
        nameLbl.setFont(DarkTheme.FONT_BADGE);
        nameLbl.setForeground(DarkTheme.ACCENT);
        badge.add(nameLbl);

        JLabel removeLbl = new JLabel("×");
        removeLbl.setFont(DarkTheme.FONT_BADGE);
        removeLbl.setForeground(DarkTheme.FG_SECOND);
        removeLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeLbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent ev) {
                badgePanel.remove(badge);
                badgePanel.revalidate();
                badgePanel.repaint();
                updateEmptyStateVisibility();
                fireChanged();
            }
        });
        badge.add(removeLbl);
        badgePanel.add(badge);
        updateEmptyStateVisibility();
    }

    private void updateEmptyStateVisibility() {
        emptyStateLabel.setVisible(badgePanel.getComponentCount() == 0
                && !emptyStateLabel.getText().isBlank());
    }

    private void resetHint() {
        hintLabel.setText(hintLabel.getText());
        hintLabel.setForeground(DarkTheme.FG_SECOND);
    }

    private void fireChanged() {
        if (onChanged != null) onChanged.run();
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Sets the values this field will accept; empty/null disables validation. */
    public void setKnownValues(List<String> values) {
        this.knownValues = values != null ? values : new ArrayList<>();
    }

    /** Removes any current badges whose value is no longer in {@code knownValues}. */
    public void pruneInvalid() {
        if (knownValues.isEmpty()) return;
        List<String> current = getValues();
        badgePanel.removeAll();
        for (String v : current) {
            if (knownValues.contains(v)) addBadge(v);
        }
        badgePanel.revalidate();
        badgePanel.repaint();
    }

    public List<String> getValues() {
        List<String> result = new ArrayList<>();
        for (Component c : badgePanel.getComponents()) {
            if (c instanceof JPanel) {
                for (Component inner : ((JPanel) c).getComponents()) {
                    if (inner instanceof JLabel && !((JLabel) inner).getText().equals("×")) {
                        result.add(((JLabel) inner).getText());
                        break;
                    }
                }
            }
        }
        return result;
    }

    public void clear() {
        badgePanel.removeAll();
        badgePanel.revalidate();
        badgePanel.repaint();
        updateEmptyStateVisibility();
    }

    /** Adds a badge for a value already known to be valid, without firing {@code onChanged}. */
    public void addKnownSelectedValue(String value) {
        if (getValues().contains(value)) return;
        addBadge(value);
        badgePanel.revalidate();
        badgePanel.repaint();
    }

    /** Text shown in place of the badge row while empty; pass null or blank to disable. */
    public void setEmptyStateText(String text) {
        emptyStateLabel.setText(text != null ? text : " ");
        updateEmptyStateVisibility();
    }

    public void setHintText(String text)      { hintLabel.setText(text); hintLabel.setForeground(DarkTheme.FG_SECOND); }
    public void setOnChanged(Runnable r)       { this.onChanged = r; }
    public JTextField getInputField()          { return inputField; }
}
