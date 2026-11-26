package org.crebs.coreui.menu.smithy;

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
 * Drawing helpers for the blacksmith's forge screen (420 x 252 GUI px, art from tools/smithy_art.py). Elements are
 * painted line by line from the top, so an image covers whatever sits on earlier lines and is covered by later ones.
 */
final class SmithyCanvas {
    static final int WIDTH = 420, HEIGHT = 252;
    static final TextColor TEXT = TextColor.color(0xEDE3D1), DIM = TextColor.color(0x9A8F80),
            SUBTLE = TextColor.color(0x6E645A), GOLD = TextColor.color(0xF2C14E), GREEN = TextColor.color(0x7ED957),
            RED = TextColor.color(0xFF5A4A), EMBER = TextColor.color(0xFF8A2A), BLUE = TextColor.color(0x96DCFF),
            VIOLET = TextColor.color(0xC4A0FF), WARM = TextColor.color(0xC88C3C);

    final Map<String, Glyph> g;
    final CoreMenuBuilder b;
    private int ids;

    SmithyCanvas(Map<String, Glyph> g, String id, String title) {
        this.g = g;
        this.b = CoreMenu.builder(id).title(Component.text(title)).theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
    }

    CoreMenu build() {
        return b.build();
    }

    boolean has(String id) {
        return g.containsKey(id);
    }

    void image(String id, int x, int y) {
        b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
    }

    /** 16 px vanilla item icon; it only exists for y % 9 == 2, callers place it there. */
    void item(String key, int x, int y) {
        Glyph icon = g.get("item/" + key);
        if (icon == null || !icon.supportsOffset(Math.floorMod(y, 9))) icon = g.get("menu/ui/unknown_item");
        if (icon != null) b.glyph("img" + ids++, icon).position(x, y).add();
    }

    /** 48 px gear icon (smithy/item/&lt;key&gt;), or the 16 px one in its middle when there is none. */
    void bigItem(String key, int x, int y) {
        if (has("smithy/item/" + key)) image("smithy/item/" + key, x, y);
        else item(key, x + 16, y + 16 + Math.floorMod(2 - (y + 16), 9));
    }

    void text(String value, int x, int y, TextColor color) {
        Component text = Component.text(value, color);
        b.label("txt" + ids++, text).position(x, y).size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
    }

    void centered(String value, int centerX, int y, TextColor color) {
        text(value, centerX - width(value) / 2, y, color);
    }

    void right(String value, int rightX, int y, TextColor color) {
        text(value, rightX - width(value), y, color);
    }

    int width(String value) {
        return TextWidths.width(Component.text(value));
    }

    void click(int x, int y, int w, int h, Consumer<Player> onClick, HoverEventSource<?> hover) {
        var spot = b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick == null ? p -> { } : onClick);
        if (hover != null) spot.hover(hover);
        spot.add();
    }

    /** Big digits (smithy/num/&lt;colour&gt;_&lt;char&gt;, 20 px tall) centred on {@code centerX}. */
    void bigNumber(String value, String colour, int centerX, int y) {
        int total = 0;
        for (char ch : value.toCharArray()) total += digitWidth(colour, ch);
        int x = centerX - total / 2;
        for (char ch : value.toCharArray()) {
            String id = digitId(colour, ch);
            if (id != null) image(id, x, y);
            x += digitWidth(colour, ch);
        }
    }

    private int digitWidth(String colour, char ch) {
        String id = digitId(colour, ch);
        return id == null ? 6 : g.get(id).width() + 2;
    }

    private String digitId(String colour, char ch) {
        String name = Character.isDigit(ch) ? String.valueOf(ch) : ch == '%' ? "pct" : ch == '+' ? "plus" : null;
        if (name == null) return null;
        String id = "smithy/num/" + colour + "_" + name;
        return has(id) ? id : null;
    }

    /** The 134 px artisan gauge: frame, then ember pieces (64, 32, ... 1 px) filling {@code ratio} of 132 px. */
    void gauge(int x, int y, double ratio) {
        image("smithy/ui/bar", x, y);
        int amount = (int) Math.round(132 * Math.clamp(ratio, 0, 1)), cursor = x + 1;
        for (int piece = 64; piece >= 1; piece /= 2) {
            while (amount >= piece) {
                image("smithy/ui/fill_" + piece, cursor, y + 1);
                cursor += piece;
                amount -= piece;
            }
        }
    }
}
