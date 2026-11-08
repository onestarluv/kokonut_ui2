package org.crebs.coreui.menu.element;

import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import java.util.Objects;

/**
 * A player's skin face drawn by the client (text object, Minecraft 26.x). It is always 8×8 GUI px with an 8 px
 * advance and cannot be scaled; it sits on the 9 px text line that contains its y, so place it at a y on a line
 * boundary (a multiple of 9 below the content top) to know exactly where it lands.
 */
public record PlayerHeadElement(ElementProperties properties, PlayerHeadObjectContents head) implements MenuElement {
    public static final int SIZE = 8;

    public PlayerHeadElement {
        Objects.requireNonNull(properties);
        Objects.requireNonNull(head);
    }
}
