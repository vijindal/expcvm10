package ui.gui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Compact single-line axis range input: one JTextField takes "min, max, step".
 * A hint label below updates in real-time to confirm parsed values and point count.
 */
public class RangeField extends JPanel {

    private static final Font FIELD_FONT = new Font("Consolas", Font.PLAIN, 10);
    private static final Font HINT_FONT  = new Font("Segoe UI",  Font.PLAIN,  9);

    private final JTextField field;
    private final JLabel     hint;

    public RangeField(String defaultValue) {
        setLayout(new BorderLayout(0, 1));
        setOpaque(false);

        field = new JTextField(defaultValue);
        field.setFont(FIELD_FONT);
        field.setBackground(DarkTheme.BG_INPUT);
        field.setForeground(DarkTheme.FG_PRIMARY);
        field.setCaretColor(DarkTheme.FG_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x555555), 1),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        field.setToolTipText("Format: min, max, step   e.g. 500, 2000, 50");

        hint = new JLabel(buildHint(defaultValue));
        hint.setFont(HINT_FONT);
        hint.setForeground(DarkTheme.FG_SECOND);

        add(field, BorderLayout.CENTER);
        add(hint,  BorderLayout.SOUTH);

        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateHint(); }
            public void removeUpdate(DocumentEvent e)  { updateHint(); }
            public void changedUpdate(DocumentEvent e) { updateHint(); }
        });
    }

    private void updateHint() {
        hint.setText(buildHint(field.getText()));
    }

    private static String buildHint(String text) {
        double[] r = parse(text);
        if (r == null) return "⚠ expected: min, max, step";
        int n = (int) Math.round((r[1] - r[0]) / r[2]) + 1;
        return String.format("→ %.4g … %.4g  step %.4g  (%d pts)", r[0], r[1], r[2], Math.max(0, n));
    }

    /** Returns [min, max, step] or null if unparseable. */
    public double[] getRange() {
        return parse(field.getText());
    }

    public double getMin()  { double[] r = getRange(); return r != null ? r[0] : Double.NaN; }
    public double getMax()  { double[] r = getRange(); return r != null ? r[1] : Double.NaN; }
    public double getStep() { double[] r = getRange(); return r != null ? r[2] : Double.NaN; }

    public double getMinOrDefault(double def)  { double v = getMin();  return Double.isNaN(v) ? def : v; }
    public double getMaxOrDefault(double def)  { double v = getMax();  return Double.isNaN(v) ? def : v; }
    public double getStepOrDefault(double def) { double v = getStep(); return Double.isNaN(v) ? def : v; }

    public boolean isValid() { return field != null && getRange() != null; }

    public void setText(String text) { if (field != null) { field.setText(text); updateHint(); } }

    public JTextField getTextField() { return field; }

    private static double[] parse(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split("[,\\s]+");
        if (parts.length < 3) return null;
        try {
            double min  = Double.parseDouble(parts[0]);
            double max  = Double.parseDouble(parts[1]);
            double step = Double.parseDouble(parts[2]);
            if (step <= 0 || min > max) return null;
            return new double[]{min, max, step};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
