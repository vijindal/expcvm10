package ui.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

/**
 * VS Code-style dark theme for expCVM 10 GUI.
 * Centralizes all colors, UIManager overrides, and reusable components.
 * Apply once at startup via DarkTheme.apply() before constructing any GUI components.
 */
public class DarkTheme {

    // ========== Color Palette ==========
    public static final Color BG          = new Color(0x1E1E1E);  // Main background
    public static final Color CARD        = new Color(0x2D2D2D);  // Card/section panels
    public static final Color BORDER      = new Color(0x3F3F46);  // 1px dividers/borders
    public static final Color BG_INPUT    = new Color(0x3C3C3C);  // Text fields, combos
    public static final Color ACCENT      = new Color(0x007ACC);  // VS Code blue
    public static final Color SUCCESS     = new Color(0x4EC9B0);  // Valid status (teal-green)
    public static final Color ERROR_COLOR = new Color(0xF44747);  // Error/invalid (muted red)
    public static final Color VALID_BG    = new Color(0x1E3A2E);  // Valid field tint
    public static final Color INVALID_BG  = new Color(0x3A1E1E);  // Invalid field tint
    public static final Color FG_PRIMARY  = new Color(0xD4D4D4);  // Main text
    public static final Color FG_SECOND   = new Color(0x858585);  // Secondary/disabled text
    public static final Color SEL_BG      = new Color(0x264F78);  // Selection background
    public static final Color SCROLL_THUMB= new Color(0x424242);  // Scrollbar thumb
    public static final Color MENU_BG     = new Color(0x252526);  // Menu bar/menus
    public static final Color SIDEBAR_BG  = new Color(0x252526);  // Sidebar panel background
    public static final Color SECTION_FG  = new Color(0xBBBBBB);  // Section/panel header text (VS Code style)
    public static final Color WARNING     = new Color(0xCCA700);  // Warning status (amber)
    public static final Color HEADER_BG   = new Color(0x1B1B1B);  // Application header bar

    // ========== Font Hierarchy ==========
    // Three levels only: section header, field/body label, hint/status. Data-bearing
    // fields (numeric input, log/param text) use the monospace family instead.
    public static final Font FONT_APP_TITLE = new Font("Segoe UI", Font.BOLD,   13);
    public static final Font FONT_SECTION   = new Font("Segoe UI", Font.BOLD,   10);
    public static final Font FONT_LABEL     = new Font("Segoe UI", Font.PLAIN,  11);
    public static final Font FONT_LABEL_BOLD= new Font("Segoe UI", Font.BOLD,   11);
    public static final Font FONT_HINT      = new Font("Segoe UI", Font.PLAIN,   9);
    public static final Font FONT_MONO      = new Font("Consolas",  Font.PLAIN, 11);
    public static final Font FONT_MONO_SM   = new Font("Consolas",  Font.PLAIN, 10);
    public static final Font FONT_BADGE     = new Font("Consolas",  Font.BOLD,  10);

    // ========== Spacing / Sizing ==========
    // One rhythm reused by every config panel instead of ad hoc EmptyBorder insets.
    public static final int SPACE_XS = 2;
    public static final int SPACE_SM = 4;
    public static final int SPACE_MD = 8;
    public static final int SPACE_LG = 12;
    public static final int SIDEBAR_WIDTH   = 300;
    public static final int ACTIVITY_WIDTH  = 52;
    public static final int CONTROL_HEIGHT  = 24;
    public static final Insets PANEL_PADDING  = new Insets(SPACE_MD, SPACE_LG, SPACE_MD, SPACE_LG);
    public static final Insets FIELD_INSETS   = new Insets(SPACE_XS + 1, SPACE_SM, SPACE_XS + 1, SPACE_SM);

    public static void apply() {
        String[] gradientKeys = {
            "Button.gradient", "CheckBox.gradient", "RadioButton.gradient",
            "ToggleButton.gradient", "ScrollBar.gradient", "Slider.gradient",
            "ProgressBar.gradient", "MenuBar.gradient", "InternalFrame.activeTitleGradient"
        };
        for (String key : gradientKeys) UIManager.put(key, null);

        UIManager.put("Panel.background",           CARD);
        UIManager.put("Panel.foreground",           FG_PRIMARY);
        UIManager.put("Viewport.background",        CARD);
        UIManager.put("Viewport.foreground",        FG_PRIMARY);
        UIManager.put("Label.background",           CARD);
        UIManager.put("Label.foreground",           FG_PRIMARY);
        UIManager.put("Label.disabledForeground",   FG_SECOND);
        javax.swing.border.Border inputBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x555555), 1),
            BorderFactory.createEmptyBorder(2, 5, 2, 5));
        UIManager.put("TextField.background",            BG_INPUT);
        UIManager.put("TextField.foreground",            FG_PRIMARY);
        UIManager.put("TextField.caretForeground",       FG_PRIMARY);
        UIManager.put("TextField.selectionBackground",   SEL_BG);
        UIManager.put("TextField.selectionForeground",   FG_PRIMARY);
        UIManager.put("TextField.inactiveForeground",    FG_SECOND);
        UIManager.put("TextField.border",                inputBorder);
        UIManager.put("TextArea.background",             BG_INPUT);
        UIManager.put("TextArea.foreground",             FG_PRIMARY);
        UIManager.put("TextArea.caretForeground",        FG_PRIMARY);
        UIManager.put("TextArea.selectionBackground",    SEL_BG);
        UIManager.put("TextArea.selectionForeground",    FG_PRIMARY);
        UIManager.put("TextArea.inactiveForeground",     FG_SECOND);
        UIManager.put("TextPane.background",             BG_INPUT);
        UIManager.put("TextPane.foreground",             FG_PRIMARY);
        UIManager.put("EditorPane.background",           BG_INPUT);
        UIManager.put("EditorPane.foreground",           FG_PRIMARY);
        UIManager.put("Button.background",          CARD);
        UIManager.put("Button.foreground",          FG_PRIMARY);
        UIManager.put("Button.select",              BORDER);
        UIManager.put("Button.focus",               BORDER);
        UIManager.put("ToggleButton.background",    CARD);
        UIManager.put("ToggleButton.foreground",    FG_PRIMARY);
        UIManager.put("ComboBox.background",               BG_INPUT);
        UIManager.put("ComboBox.foreground",               FG_PRIMARY);
        UIManager.put("ComboBox.selectionBackground",      SEL_BG);
        UIManager.put("ComboBox.selectionForeground",      FG_PRIMARY);
        UIManager.put("ComboBox.buttonBackground",         BG_INPUT);
        UIManager.put("ComboBox.buttonShadow",             BG_INPUT);
        UIManager.put("ComboBox.buttonDarkShadow",         BG_INPUT);
        UIManager.put("ComboBox.buttonHighlight",          BG_INPUT);
        UIManager.put("ComboBox.border",                   BorderFactory.createLineBorder(new Color(0x4A4A4A), 1));
        UIManager.put("ComboBox.disabledBackground",       CARD);
        UIManager.put("ComboBox.disabledForeground",       FG_SECOND);
        UIManager.put("List.background",               MENU_BG);
        UIManager.put("List.foreground",               FG_PRIMARY);
        UIManager.put("List.selectionBackground",      SEL_BG);
        UIManager.put("List.selectionForeground",      FG_PRIMARY);
        UIManager.put("PopupMenu.background",          MENU_BG);
        UIManager.put("PopupMenu.foreground",          FG_PRIMARY);
        UIManager.put("PopupMenu.border",              BorderFactory.createLineBorder(BORDER));
        UIManager.put("MenuBar.background",             MENU_BG);
        UIManager.put("MenuBar.foreground",             FG_PRIMARY);
        UIManager.put("MenuBar.border",                 BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        UIManager.put("Menu.background",                MENU_BG);
        UIManager.put("Menu.foreground",                FG_PRIMARY);
        UIManager.put("Menu.selectionBackground",       SEL_BG);
        UIManager.put("Menu.selectionForeground",       FG_PRIMARY);
        UIManager.put("MenuItem.background",            MENU_BG);
        UIManager.put("MenuItem.foreground",            FG_PRIMARY);
        UIManager.put("MenuItem.selectionBackground",   SEL_BG);
        UIManager.put("MenuItem.selectionForeground",   FG_PRIMARY);
        UIManager.put("MenuItem.acceleratorForeground", FG_SECOND);
        UIManager.put("Separator.background",           BORDER);
        UIManager.put("Separator.foreground",           BORDER);
        UIManager.put("ScrollPane.background",      CARD);
        UIManager.put("ScrollPane.border",          BorderFactory.createLineBorder(BORDER));
        UIManager.put("ScrollBar.background",       BG);
        UIManager.put("ScrollBar.thumb",            SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbShadow",      SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbHighlight",   SCROLL_THUMB);
        UIManager.put("ScrollBar.track",            BG);
        UIManager.put("ScrollBar.trackHighlight",   BG);
        UIManager.put("ScrollBar.width",            8);
        UIManager.put("SplitPane.background",           BG);
        UIManager.put("SplitPaneDivider.border",        BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT);
        UIManager.put("TabbedPane.background",              CARD);
        UIManager.put("TabbedPane.foreground",              FG_SECOND);
        UIManager.put("TabbedPane.selected",                BG);
        UIManager.put("TabbedPane.selectedForeground",      FG_PRIMARY);
        UIManager.put("TabbedPane.tabAreaBackground",       MENU_BG);
        UIManager.put("TabbedPane.contentAreaColor",        CARD);
        UIManager.put("TabbedPane.focus",                   new Color(0, 0, 0, 0));
        UIManager.put("TabbedPane.selectHighlight",         ACCENT);
        UIManager.put("TabbedPane.darkShadow",              BG);
        UIManager.put("TabbedPane.shadow",                  BORDER);
        UIManager.put("TabbedPane.highlight",               BORDER);
        UIManager.put("TabbedPane.light",                   MENU_BG);
        UIManager.put("TabbedPane.contentBorderInsets",     new Insets(1, 0, 0, 0));
        UIManager.put("ProgressBar.background",         BG_INPUT);
        UIManager.put("ProgressBar.foreground",         ACCENT);
        UIManager.put("ProgressBar.selectionBackground",FG_PRIMARY);
        UIManager.put("ProgressBar.selectionForeground",BG);
        UIManager.put("ProgressBar.border",             BorderFactory.createLineBorder(BORDER));
        UIManager.put("Table.background",               CARD);
        UIManager.put("Table.foreground",               FG_PRIMARY);
        UIManager.put("Table.gridColor",                BORDER);
        UIManager.put("Table.selectionBackground",      SEL_BG);
        UIManager.put("Table.selectionForeground",      FG_PRIMARY);
        UIManager.put("TableHeader.background",         MENU_BG);
        UIManager.put("TableHeader.foreground",         FG_SECOND);
        UIManager.put("TableHeader.cellBorder",         BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER));
        UIManager.put("OptionPane.background",          CARD);
        UIManager.put("OptionPane.messageForeground",   FG_PRIMARY);
        UIManager.put("OptionPane.messageFont",         new Font("Segoe UI", Font.PLAIN, 11));
        UIManager.put("OptionPane.buttonFont",          new Font("Segoe UI", Font.PLAIN, 11));
        UIManager.put("FileChooser.background",         CARD);
        UIManager.put("FileChooser.foreground",         FG_PRIMARY);
        UIManager.put("ToolTip.background",             MENU_BG);
        UIManager.put("ToolTip.foreground",             FG_PRIMARY);
        UIManager.put("ToolTip.border",                 BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font",                   new Font("Segoe UI", Font.PLAIN, 11));
    }

    public static class ComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setBackground(isSelected ? SEL_BG : BG_INPUT);
            setForeground(FG_PRIMARY);
            setBorder(new EmptyBorder(2, 6, 2, 6));
            return this;
        }
    }

    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        // No top border — header bar above provides the visual top edge
        sp.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, BORDER));
        sp.getViewport().setBackground(BG);
        return sp;
    }

    /**
     * Creates a JSplitPane with a flat 1-pixel BORDER-colour divider,
     * matching the sleek MatteBorder used on the activity bar.
     * The divider paint is completely overridden — no platform LAF rendering.
     */
    public static JSplitPane sleekSplit(int orientation) {
        JSplitPane sp = new JSplitPane(orientation);
        sp.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(BORDER);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        sp.setBorder(null);
        sp.setDividerSize(1);
        sp.setContinuousLayout(true);
        return sp;
    }

    /**
     * VS Code-style panel header bar (OUTPUT, RESULTS, PHASES, PARAMETERS, …).
     * Background matches the panel content (BG). Title in SECTION_FG (near-white).
     * Toolbar components (buttons, combos) are appended to the right if provided.
     */
    public static JPanel panelHeader(String title, java.awt.Component... rightControls) {
        JPanel bar = new JPanel(new java.awt.BorderLayout());
        bar.setBackground(BG);
        bar.setBorder(new javax.swing.border.EmptyBorder(0, 0, 0, 0));

        JLabel lbl = new JLabel(title);
        lbl.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
        lbl.setForeground(SECTION_FG);
        lbl.setBorder(new javax.swing.border.EmptyBorder(3, 8, 3, 8));
        bar.add(lbl, java.awt.BorderLayout.WEST);

        if (rightControls != null && rightControls.length > 0) {
            JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 1));
            right.setOpaque(false);
            for (java.awt.Component c : rightControls) right.add(c);
            bar.add(right, java.awt.BorderLayout.EAST);
        }
        return bar;
    }

    /** 1-px bottom separator line for use between sidebar sections. */
    public static JPanel separator() {
        JPanel line = new JPanel();
        line.setBackground(BORDER);
        line.setPreferredSize(new Dimension(0, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setMinimumSize(new Dimension(0, 1));
        return line;
    }

    // ================================================================
    //  Form building helpers — shared by config panels
    // ================================================================

    /** Section header label ("PHASES", "CONDITIONS", "AXIS 0 (X)", …). */
    public static JLabel sectionLabel(String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(FONT_SECTION);
        lbl.setForeground(SECTION_FG);
        lbl.setBorder(new EmptyBorder(SPACE_SM, 0, SPACE_XS, 0));
        return lbl;
    }

    /** Small hint/status line under a field or section. */
    public static JLabel hintLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_HINT);
        lbl.setForeground(FG_SECOND);
        return lbl;
    }

    /** Plain field-row label ("T (K)", "Type", "Range", …). */
    public static JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_LABEL);
        return lbl;
    }

    /** Compact flat utility button (Add, Browse, Clear, Copy, All/None, …). */
    public static JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_LABEL);
        btn.setMargin(new Insets(2, SPACE_MD, 2, SPACE_MD));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(BG_INPUT);
        btn.setForeground(FG_PRIMARY);
        btn.setOpaque(true);
        return btn;
    }

    /** Primary call-to-action button (Run, Calculate, Inspect). */
    public static JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setMargin(new Insets(SPACE_MD, SPACE_LG + 4, SPACE_MD, SPACE_LG + 4));
        return btn;
    }

    /** Standard themed text field for numeric/text entry rows. */
    public static JTextField textField(String defaultValue) {
        JTextField f = new JTextField(defaultValue);
        f.setFont(FONT_MONO_SM);
        f.setBackground(BG_INPUT);
        f.setForeground(FG_PRIMARY);
        f.setCaretColor(FG_PRIMARY);
        f.setSelectionColor(SEL_BG);
        return f;
    }

    /** Themed combo box using {@link ComboRenderer}. */
    public static <T> JComboBox<T> comboBox(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setBackground(BG_INPUT);
        c.setForeground(FG_PRIMARY);
        c.setRenderer(new ComboRenderer());
        return c;
    }

    /** Adds a label + field row spanning the standard 3-column grid, returning the field. */
    public static JTextField addLabeledRow(JPanel panel, GridBagConstraints g, int row,
                                            String label, String defaultValue) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        panel.add(fieldLabel(label), g);

        JTextField field = textField(defaultValue);
        g.gridx = 1; g.gridy = row; g.weightx = 1; g.gridwidth = 2;
        panel.add(field, g);
        g.gridwidth = 1;
        return field;
    }

    /** Adds a full-width section header row, returning the label for later mutation. */
    public static JLabel addSectionRow(JPanel panel, GridBagConstraints g, int row, String title) {
        JLabel lbl = sectionLabel(title);
        g.gridx = 0; g.gridy = row; g.gridwidth = 3; g.weightx = 1;
        panel.add(lbl, g);
        g.gridwidth = 1;
        return lbl;
    }

    /** Fresh GridBagConstraints matching the standard config-panel grid rhythm. */
    public static GridBagConstraints formGbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(SPACE_SM, SPACE_SM + 2, SPACE_SM, SPACE_SM + 2);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }
}
