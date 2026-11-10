package org.crebs.coreui.menu.book;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.quest.QuestCatalog.Reward;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One book-layout frame under construction (quest journal, NPC quests, village): the book chrome and the helpers
 * the mailbox and shop canvases use,
 * plus dex progress bars and reward slots. Vanilla item icons sit at y % 9 == 2. Nothing may start at y 145..152
 * and reach past y 153: the lower book tiles start there and are painted later, so they cover it (that is what
 * clipped "보상" and the shop's old "받는 개수" row).
 */
public final class BookCanvas {
    public static final int WIDTH = 480, HEIGHT = 292, LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258;
    public static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), RED = TextColor.color(0xA83A2A),
            WHITE = TextColor.color(0xFFFFFF), PAPER = TextColor.color(0xF6ECD0);
    public static final DecimalFormat NUMBER = new DecimalFormat("#,##0");

    public final Map<String, Glyph> g;
    public final CoreMenuBuilder b;
    int ids;

    public BookCanvas(Map<String, Glyph> g, String id, String title) {
        this.g = g;
        this.b = CoreMenu.builder(id).title(Component.text(title)).theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
    }

    /** The book's four page tiles (the lower ones start at y 153 and are painted after everything above). */
    public void pages() {
        for (int col = 0; col < 2; col++) {
            image("dex/ui/page_0_" + col, col * 228, 20);
            image("dex/ui/page_1_" + col, col * 228, 153);
        }
    }

    /** Book, title tab (icon glyph or vanilla item) joined to the book, close button. */
    public void chrome(String title, String iconGlyph, String iconItem, Consumer<Player> close) {
        pages();
        image("menu/ui/tab", 16, 0);
        if (iconGlyph != null) image(iconGlyph, 20, 2);
        else item(iconItem, 20, 2);
        text(title, 38, 6, WHITE);
        image("menu/ui/tab_join", 17, 20);
        close(close);
    }

    public void close(Consumer<Player> close) {
        image("dex/ui/close", 432, 0);
        click(432, 0, 20, 20, close, null);
    }

    public CoreMenu build() {
        return b.build();
    }

    public void image(String id, int x, int y) {
        b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
    }

    /** Vanilla item icon at offset-2 position, or the "?" icon when there is none. */
    public void item(String key, int x, int y) {
        Glyph icon = key == null ? null : g.get("item/" + key);
        if (icon != null && !icon.supportsOffset(Math.floorMod(y, 9))) icon = null;
        image(icon == null ? "menu/ui/unknown_item" : icon.id(), x, y);
    }

    /** 16 px gold icon with its top at {@code iconY}, the amount beside it (text 4 px lower); returns the width. */
    public int goldRight(long amount, int rightX, int iconY, TextColor color) {
        String value = NUMBER.format(amount);
        int width = 18 + TextWidths.width(Component.text(value));
        image("menu/icon/gold", rightX - width, iconY);
        text(value, rightX - width + 18, iconY + 4, color);
        return width;
    }

    /** The dex's 100 px bar ({@code dex/ui/bar_100}) filled with power-of-two pieces. */
    public void bar(int x, int y, double ratio) {
        image("dex/ui/bar_100", x, y);
        int amount = (int) Math.round(98 * Math.clamp(ratio, 0, 1)), cursor = x + 1;
        for (int piece = 64; piece >= 1; piece /= 2) {
            while (amount >= piece) {
                image("dex/ui/fill_" + piece, cursor, y + 1);
                cursor += piece;
                amount -= piece;
            }
        }
    }

    /**
     * Reward cards from {@code x} in one row: slot with the icon, then the amount beside it ("1,800", "×2"); the
     * tooltip names the reward. Amounts never go inside the 20 px slot, so long numbers cannot overflow it.
     */
    public void rewards(List<Reward> rewards, int x, int slotY, int maxX) {
        int cx = x;
        for (Reward r : rewards) {
            String value = r.kind() == Reward.Kind.ITEM ? "×" + NUMBER.format(r.amount()) : NUMBER.format(r.amount());
            int w = 22 + width(value);
            if (cx + w > maxX) break;
            image("dex/ui/slot_normal", cx, slotY);
            if (r.kind() == Reward.Kind.GOLD) image("menu/icon/gold", cx + 2, slotY + 2);
            else item(r.item(), cx + 2, slotY + 2);
            text(value, cx + 22, slotY + 6, INK);
            click(cx, slotY, w, 20, p -> { }, HoverEvent.showText(Component.text(r.name() + " " + NUMBER.format(r.amount()))));
            cx += w + 8;
        }
    }

    /** The client-drawn 8x8 skin face; put {@code y} on a 9 px line (y % 9 == 0) so it lands exactly there. */
    public void head(net.kyori.adventure.text.object.PlayerHeadObjectContents head, int x, int y) {
        b.playerHead("head" + ids++, head).position(x, y).add();
    }

    /** Splits {@code text} into lines no wider than {@code max} px (explicit newlines kept, long words cut). */
    public java.util.List<String> wrap(String text, int max) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : (text == null ? "" : text).split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (width(next) <= max) { line.setLength(0); line.append(next); continue; }
                if (!line.isEmpty()) lines.add(line.toString());
                line.setLength(0);
                for (int cp : word.codePoints().toArray()) {
                    String more = line.toString() + Character.toString(cp);
                    if (width(more) > max && !line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                    line.appendCodePoint(cp);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    public void click(int x, int y, int w, int h, Consumer<Player> onClick, HoverEventSource<?> hover) {
        var spot = b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick);
        if (hover != null) spot.hover(hover);
        spot.add();
    }

    public void text(String value, int x, int y, TextColor color) {
        Component text = Component.text(value, color);
        b.label("txt" + ids++, text).position(x, y).size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
    }

    public void fitted(String value, int x, int y, int max, TextColor color) {
        String shown = value == null ? "" : value;
        if (TextWidths.width(Component.text(shown)) > max) {
            while (!shown.isEmpty() && TextWidths.width(Component.text(shown + "…")) > max) shown = shown.substring(0, shown.length() - 1);
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

    public void centered(String value, int x, int width, int y, TextColor color) {
        text(value, x + (width - width(value)) / 2, y, color);
    }

    public void button(String kind, int width, String label, int x, int y, TextColor color, Consumer<Player> onClick) {
        image("dex/ui/btn_" + kind + "_" + width, x, y);
        centered(label, x, width, y + 6, color);
        if (onClick != null) click(x, y, width, 20, onClick, null);
    }
}
