package org.crebs.coreui.menu.vanilla;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One frame under construction in the vanilla container style (grey panel, inset slots, stone buttons): the
 * furniture workbench and the server travel screen. The panel image of each screen comes from
 * {@code tools/dex_glyphs.py}; the shared pieces are {@code furniture/ui/slot}, {@code wood_on} (white selection
 * frame), {@code scroller} and {@code button_50}.
 * <p>Vanilla item icons have one font provider, so they sit at y % 9 == 2. CoreUi paints 9 px text lines top to
 * bottom and, inside one line, in the order added, so anything drawn over an image is added after it.</p>
 */
public final class VanillaCanvas {
    /** Side of a slot; an item icon sits 1 px inside it. */
    public static final int SLOT = 18;
    /** Vanilla label grey, a lighter hint, white and red with their drop shadows, and the disabled button label. */
    public static final TextColor LABEL = TextColor.color(0x404040), HINT = TextColor.color(0x6A6A6A),
            WHITE = TextColor.color(0xFFFFFF), WHITE_SHADOW = TextColor.color(0x3F3F3F),
            RED = TextColor.color(0xFF5555), RED_SHADOW = TextColor.color(0x3F1515), OFF = TextColor.color(0xA0A0A0);
    public static final int BUTTON_W = 50, BUTTON_H = 20;

    public final Map<String, Glyph> g;
    public final CoreMenuBuilder b;
    private final int width;
    private int ids;

    public VanillaCanvas(Map<String, Glyph> g, String id, String title, int width, int height) {
        this.g = g;
        this.width = width;
        this.b = CoreMenu.builder(id).title(Component.text(title)).theme(ThemeColour.DARK).noBackground().size(width, height);
    }

    public CoreMenu build() {
        return b.build();
    }

    public void image(String id, int x, int y) {
        b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
    }

    /** An image whose colours are multiplied by {@code tint} (a white shape takes the tint colour). */
    public void image(String id, int x, int y, TextColor tint) {
        b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).tint(tint).add();
    }

    /** Vanilla item icon (one font provider: y % 9 == 2), or the "?" icon when there is none. */
    public void item(String key, int x, int y) {
        Glyph icon = g.get("item/" + key);
        if (icon != null && !icon.supportsOffset(Math.floorMod(y, 9))) icon = null;
        image(icon == null ? "menu/ui/unknown_item" : icon.id(), x, y);
    }

    /** Stack size in the slot's bottom-right corner like an inventory slot; red when the player has too few. */
    public void count(int amount, int slotX, int slotY, boolean enough) {
        String value = String.valueOf(amount);
        shadowed(value, slotX + SLOT - width(value), slotY + 10, enough ? WHITE : RED, enough ? WHITE_SHADOW : RED_SHADOW);
    }

    /**
     * Vanilla-style text with a drop shadow: the same text one pixel down-right in the shadow colour, painted
     * first. Both must start on the same 9 px text line (later lines are painted over earlier ones).
     */
    public void shadowed(String value, int x, int y, TextColor color, TextColor shadow) {
        if (Math.floorMod(y, 9) == 8) throw new IllegalArgumentException("shadowed text cannot start on the last pixel of a text line: y " + y);
        text(value, x + 1, y + 1, shadow);
        text(value, x, y, color);
    }

    public void click(int x, int y, int w, int h, Consumer<Player> onClick, HoverEventSource<?> hover) {
        var spot = b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick);
        if (hover != null) spot.hover(hover);
        spot.add();
    }

    public void text(String value, int x, int y, TextColor color) {
        Component text = Component.text(value, color);
        b.label("txt" + ids++, text).position(x, y).size(Math.max(1, Math.min(TextWidths.width(text), width - x)), 9).add();
    }

    /** Text cut with "…" to {@code max} px. */
    public void fitted(String value, int x, int y, int max, TextColor color) {
        String shown = value == null ? "" : value;
        if (width(shown) > max) {
            while (!shown.isEmpty() && width(shown + "…") > max) shown = shown.substring(0, shown.length() - 1);
            shown += "…";
        }
        text(shown, x, y, color);
    }

    public int width(String value) {
        return TextWidths.width(Component.text(value));
    }

    public void right(String value, int rightX, int y, TextColor color) {
        text(value, rightX - width(value), y, color);
    }

    /**
     * The 50 px stone button with a white shadowed label, or the dark one with a grey label when {@code onClick}
     * is null. The hover text shows either way.
     */
    public void button(String label, int x, int y, Consumer<Player> onClick, HoverEventSource<?> hover) {
        image(onClick == null ? "furniture/ui/button_50_off" : "furniture/ui/button_50", x, y);
        int textX = x + (BUTTON_W - width(label)) / 2;
        if (onClick == null) text(label, textX, y + 6, OFF);
        else shadowed(label, textX, y + 6, WHITE, WHITE_SHADOW);
        click(x, y, BUTTON_W, BUTTON_H, onClick == null ? p -> { } : onClick, hover);
    }
}
