package org.crebs.coreui.glyph;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * @param offsetMask bit n set = the glyph can be drawn n pixels into a 9px text line. Most glyphs have all nine;
 *                   large icon sets (vanilla item icons) only ship one to stay light, so they must be placed where
 *                   the renderer lands on that offset (see {@link #supportsOffset}).
 */
public record Glyph(String id, int width, int height, int ascent, int advance, int offsetMask) {
    public static final int ALL_OFFSETS = (1 << 9) - 1;

    public Glyph(String id, int width, int height, int ascent, int advance) {
        this(id, width, height, ascent, advance, ALL_OFFSETS);
    }

    public boolean supportsOffset(int verticalOffset) {
        return verticalOffset >= 0 && verticalOffset <= 8 && (offsetMask >> verticalOffset & 1) == 1;
    }

    public Key font() {
        return Key.key("coreui", "custom/" + id);
    }

    public Component component() {
        return component(0);
    }

    public Component component(int verticalOffset) {
        if (verticalOffset < 0 || verticalOffset > 8) {
            throw new IllegalArgumentException("Vertical offset must be between 0 and 8");
        }
        if (!supportsOffset(verticalOffset)) {
            throw new IllegalArgumentException("Glyph " + id + " has no font for vertical offset " + verticalOffset);
        }
        return Component.text(String.valueOf((char) ('' + verticalOffset))).font(font())
                .color(TextColor.color(0xFFFFFF)).shadowColor(ShadowColor.none());
    }
}
