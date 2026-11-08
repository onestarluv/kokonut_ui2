package org.crebs.coreui.menu.element;

import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.function.Consumer;

/** An invisible click area, used to make image glyphs (cards, tabs, buttons drawn as pictures) clickable. */
public record HotspotElement(ElementProperties properties, Consumer<Player> onClick) implements MenuElement {
    public HotspotElement {
        Objects.requireNonNull(properties);
        Objects.requireNonNull(onClick);
    }
}
