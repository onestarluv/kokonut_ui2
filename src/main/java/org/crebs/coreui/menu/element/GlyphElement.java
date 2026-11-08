package org.crebs.coreui.menu.element;

import net.kyori.adventure.text.format.TextColor;
import org.crebs.coreui.glyph.Glyph;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/** {@code tint} multiplies the image colours (null = as drawn); e.g. a dark tint turns an item into a silhouette. */
public record GlyphElement(ElementProperties properties, Glyph glyph, @Nullable TextColor tint) implements MenuElement {
    public GlyphElement {
        Objects.requireNonNull(properties);
        Objects.requireNonNull(glyph);
    }

    public GlyphElement(ElementProperties properties, Glyph glyph) {
        this(properties, glyph, null);
    }
}
