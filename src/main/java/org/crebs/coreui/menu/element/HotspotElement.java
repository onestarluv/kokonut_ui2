package org.crebs.coreui.menu.element;

import net.kyori.adventure.text.event.HoverEventSource;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An invisible click area, used to make image glyphs (cards, tabs, buttons drawn as pictures) clickable.
 * {@code hover} (optional) shows a tooltip over the area, e.g. an item's own tooltip via {@code ItemStack.asHoverEvent()};
 * the client renders it, so translated item names, lore and enchantments appear without measuring any text.
 */
public record HotspotElement(ElementProperties properties, Consumer<Player> onClick, @Nullable HoverEventSource<?> hover)
        implements MenuElement {
    public HotspotElement {
        Objects.requireNonNull(properties);
        Objects.requireNonNull(onClick);
    }

    public HotspotElement(ElementProperties properties, Consumer<Player> onClick) {
        this(properties, onClick, null);
    }
}
