package presentation.gui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

    /**
     * Apply the dark theme to the entire Swing application.
     * Must be called after UIManager.setLookAndFeel() and before any component is constructed.
     */
    public static void apply() {
        // ========== Metal Gradient Suppression ==========
        String[] gradientKeys = {
            "Button.gradient", "CheckBox.gradient", "RadioButton.gradient",
            "ToggleButton.gradient", "ScrollBar.gradient", "Slider.gradient",
            "ProgressBar.gradient", "MenuBar.gradient", "InternalFrame.activeTitleGradient"
        };
        for (String key : gradientKeys) {
            UIManager.put(key, null);
        }

        // ========== Panel / Viewport ==========
        UIManager.put("Panel.background",           CARD);
        UIManager.put("Panel.foreground",           FG_PRIMARY);
        UIManager.put("Viewport.background",        CARD);
        UIManager.put("Viewport.foreground",        FG_PRIMARY);

        // ========== Label ==========
        UIManager.put("Label.background",           CARD);
        UIManager.put("Label.foreground",           FG_PRIMARY);
        UIManager.put("Label.disabledForeground",   FG_SECOND);

        // ========== TextField / TextArea / TextPane ==========
        UIManager.put("TextField.background",            BG_INPUT);
        UIManager.put("TextField.foreground",            FG_PRIMARY);
        UIManager.put("TextField.caretForeground",       FG_PRIMARY);
        UIManager.put("TextField.selectionBackground",   SEL_BG);
        UIManager.put("TextField.selectionForeground",   FG_PRIMARY);
        UIManager.put("TextField.inactiveForeground",    FG_SECOND);
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

        // ========== Button ==========
        UIManager.put("Button.background",          CARD);
        UIManager.put("Button.foreground",          FG_PRIMARY);
        UIManager.put("Button.select",              BORDER);
        UIManager.put("Button.focus",               BORDER);
        UIManager.put("ToggleButton.background",    CARD);
        UIManager.put("ToggleButton.foreground",    FG_PRIMARY);

        // ========== ComboBox ==========
        UIManager.put("ComboBox.background",               BG_INPUT);
        UIManager.put("ComboBox.foreground",               FG_PRIMARY);
        UIManager.put("ComboBox.selectionBackground",      SEL_BG);
        UIManager.put("ComboBox.selectionForeground",      FG_PRIMARY);
        UIManager.put("ComboBox.buttonBackground",         CARD);
        UIManager.put("ComboBox.buttonShadow",             BORDER);
        UIManager.put("ComboBox.disabledBackground",       CARD);
        UIManager.put("ComboBox.disabledForeground",       FG_SECOND);

        // ========== List / PopupMenu ==========
        UIManager.put("List.background",               MENU_BG);
        UIManager.put("List.foreground",               FG_PRIMARY);
        UIManager.put("List.selectionBackground",      SEL_BG);
        UIManager.put("List.selectionForeground",      FG_PRIMARY);
        UIManager.put("PopupMenu.background",          MENU_BG);
        UIManager.put("PopupMenu.foreground",          FG_PRIMARY);
        UIManager.put("PopupMenu.border",              BorderFactory.createLineBorder(BORDER));

        // ========== MenuBar / Menu / MenuItem ==========
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

        // ========== ScrollPane / ScrollBar ==========
        UIManager.put("ScrollPane.background",      CARD);
        UIManager.put("ScrollPane.border",          BorderFactory.createLineBorder(BORDER));
        UIManager.put("ScrollBar.background",       BG);
        UIManager.put("ScrollBar.thumb",            SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbShadow",      SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbHighlight",   SCROLL_THUMB);
        UIManager.put("ScrollBar.track",            BG);
        UIManager.put("ScrollBar.trackHighlight",   BG);
        UIManager.put("ScrollBar.width",            8);

        // ========== SplitPane ==========
        UIManager.put("SplitPane.background",           BG);
        UIManager.put("SplitPaneDivider.border",        BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT);

        // ========== TabbedPane ==========
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

        // ========== ProgressBar ==========
        UIManager.put("ProgressBar.background",         BG_INPUT);
        UIManager.put("ProgressBar.foreground",         ACCENT);
        UIManager.put("ProgressBar.selectionBackground",FG_PRIMARY);
        UIManager.put("ProgressBar.selectionForeground",BG);
        UIManager.put("ProgressBar.border",             BorderFactory.createLineBorder(BORDER));

        // ========== Table / TableHeader ==========
        UIManager.put("Table.background",               CARD);
        UIManager.put("Table.foreground",               FG_PRIMARY);
        UIManager.put("Table.gridColor",                BORDER);
        UIManager.put("Table.selectionBackground",      SEL_BG);
        UIManager.put("Table.selectionForeground",      FG_PRIMARY);
        UIManager.put("TableHeader.background",         MENU_BG);
        UIManager.put("TableHeader.foreground",         FG_SECOND);
        UIManager.put("TableHeader.cellBorder",         BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER));

        // ========== OptionPane / Dialogs ==========
        UIManager.put("OptionPane.background",          CARD);
        UIManager.put("OptionPane.messageForeground",   FG_PRIMARY);
        UIManager.put("OptionPane.messageFont",         new Font("Segoe UI", Font.PLAIN, 11));
        UIManager.put("OptionPane.buttonFont",          new Font("Segoe UI", Font.PLAIN, 11));

        // ========== FileChooser ==========
        UIManager.put("FileChooser.background",         CARD);
        UIManager.put("FileChooser.foreground",         FG_PRIMARY);

        // ========== Tooltip ==========
        UIManager.put("ToolTip.background",             MENU_BG);
        UIManager.put("ToolTip.foreground",             FG_PRIMARY);
        UIManager.put("ToolTip.border",                 BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font",                   new Font("Segoe UI", Font.PLAIN, 11));
    }

    /**
     * Custom renderer for JComboBox dropdowns with dark theme.
     * Used by all combo boxes for consistent dark appearance.
     */
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

    /**
     * Create a styled JScrollPane with dark viewport and border.
     * Reusable helper for consistent scroll pane appearance across the app.
     *
     * @param view the component to wrap in a scroll pane
     * @return a JScrollPane with dark theme applied
     */
    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(BG);
        return sp;
    }
}
