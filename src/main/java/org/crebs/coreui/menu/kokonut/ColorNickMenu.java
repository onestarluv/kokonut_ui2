package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.nickname.ColorNickCatalog;
import kr.gomsv.kokonutCore.api.nickname.NickColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Kokonut-Core nickname colors ({@link NickColor}: vanilla, hex and gradients) as the 색 닉네임 tab of the title screen (same book, same equip flow as
 * {@link TitleMenu}). Ownership ({@code kokonut.colornick.<color>}), the saved selection and the chat messages stay in
 * Core's {@link ColorNickCatalog}; this class only draws. Only load it after confirming Kokonut-Core is enabled.
 *
 * Tab row: 칭호 (red, inactive, goes back to the titles) and 색 닉네임 (green, active, joined to the book), like the
 * village screen. Left page: the owned colors, the player's name in each; right page: name plate preview, info and
 * the 장착 / 해제 buttons. Text never straddles y = 153, where the lower book tiles start.
 */
public final class ColorNickMenu {
    static final String ID = "kokonut-colornick";
    /** Vanilla item drawn on the tab (here and on the title screen). */
    static final String TAB_ICON = "brush";
    static final int ROWS = 8;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258;
    /** 20 px rows on 9 px lines (item icons need y % 9 == 2); the row at 126 ends before the book seam. */
    private static final int[] ROW_Y = {45, 72, 99, 126, 153, 180, 207, 234};
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), WHITE = TextColor.color(0xFFFFFF),
            PAPER = TextColor.color(0xF6ECD0), STAMP = TextColor.color(0xFFD86A);

    /** Row icon (vanilla dye) of each vanilla chat color; hex colors and gradients get a white dye tinted to their color. */
    private static final Map<String, String> DYES = Map.ofEntries(
            Map.entry("black", "black_dye"), Map.entry("dark_blue", "blue_dye"), Map.entry("dark_green", "green_dye"),
            Map.entry("dark_aqua", "cyan_dye"), Map.entry("dark_red", "redstone"), Map.entry("dark_purple", "purple_dye"),
            Map.entry("gold", "orange_dye"), Map.entry("gray", "light_gray_dye"), Map.entry("dark_gray", "gray_dye"),
            Map.entry("blue", "lapis_lazuli"), Map.entry("green", "lime_dye"), Map.entry("aqua", "light_blue_dye"),
            Map.entry("red", "red_dye"), Map.entry("light_purple", "magenta_dye"), Map.entry("yellow", "yellow_dye"),
            Map.entry("white", "white_dye"));

    /** What one frame shows. {@code equipped} and {@code selected} may be null. */
    record View(String player, PlayerHeadObjectContents head, int[] face, boolean ready, boolean busy,
                List<NickColor> owned, NickColor equipped, NickColor selected, int page) {
        int pages() {
            return Math.max(1, (owned.size() + ROWS - 1) / ROWS);
        }
    }

    /** Per-player screen state carried between frames. */
    private record State(int page, NickColor selected) { }

    interface Actions {
        void select(Player p, NickColor color);
        void page(Player p, int page);
        void equip(Player p, NickColor color);
        void unequip(Player p);
        void titles(Player p);
        void close(Player p);
    }

    private ColorNickMenu() {
    }

    /**
     * {@code /별명색} and the title screen's tab: false (Core's chest GUI) while the glyphs are not loaded.
     * {@code titleTab} opens the title screen.
     */
    public static boolean open(CoreUi coreUi, ColorNickCatalog catalog, Player player, Consumer<Player> titleTab) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("menu/ui/tab_green") || !g.containsKey("menu/ui/tab_off") || !g.containsKey("menu/ui/plate_200")
                || !g.containsKey("menu/ui/tag_8") || !g.containsKey("menu/ui/px_2") || !g.containsKey("dex/ui/page_0_0")) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, catalog, titleTab, player, new State(0, null));
        return true;
    }

    private static void show(CoreUi coreUi, ColorNickCatalog catalog, Consumer<Player> titleTab, Player player, State state) {
        if (!player.isOnline()) return;
        boolean ready = catalog.ready(player);
        List<NickColor> owned = ready ? catalog.owned(player) : List.of();
        NickColor equipped = catalog.equipped(player);
        int page = Math.clamp(state.page(), 0, Math.max(1, (owned.size() + ROWS - 1) / ROWS) - 1);
        NickColor selected = pick(owned, page, state.selected(), equipped);
        View view = new View(TitleMenu.displayName(player), MainMenu.head(player), coreUi.getSkinFaces().face(player),
                ready, catalog.busy(player), owned, equipped, selected, page);
        State now = new State(page, selected);
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, new Actions() {
            public void select(Player p, NickColor color) { show(coreUi, catalog, titleTab, p, new State(now.page(), color)); }
            public void page(Player p, int to) { show(coreUi, catalog, titleTab, p, new State(to, null)); }
            public void equip(Player p, NickColor color) { change(coreUi, catalog, titleTab, p, now, color); }
            public void unequip(Player p) { change(coreUi, catalog, titleTab, p, now, null); }
            public void titles(Player p) { titleTab.accept(p); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** The kept selection if still owned, else the equipped color on this page, else the page's first row. */
    static NickColor pick(List<NickColor> owned, int page, NickColor wanted, NickColor equipped) {
        if (wanted != null && owned.contains(wanted)) return wanted;
        List<NickColor> rows = owned.subList(Math.min(owned.size(), page * ROWS), Math.min(owned.size(), (page + 1) * ROWS));
        if (equipped != null && rows.contains(equipped)) return equipped;
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Saves through Core (which sends the chat message), shows "처리 중" meanwhile and redraws when it finishes. */
    private static void change(CoreUi coreUi, ColorNickCatalog catalog, Consumer<Player> titleTab, Player player, State state, NickColor color) {
        if (catalog.busy(player)) return;
        catalog.equip(player, color, () -> {
            if (!player.isOnline()) return;
            if (java.util.Objects.equals(catalog.equipped(player), color)) player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
            else player.playSound(player, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
            if (TitleMenu.stillOpen(coreUi, player, ID)) show(coreUi, catalog, titleTab, player, state);
        });
        if (TitleMenu.stillOpen(coreUi, player, ID)) show(coreUi, catalog, titleTab, player, state);
    }

    /** One frame; pure apart from the callbacks, so tests and offline checks can build it with sample data. */
    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        TitleMenu.Canvas c = new TitleMenu.Canvas(g, ID, "색 닉네임");
        // Chrome: book, 칭호 tab (inactive, goes back), 색 닉네임 tab (active, joined to the book), close.
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab_off", 16, 0);
        c.item("name_tag", 20, 2);
        c.text("칭호", 38, 6, WHITE);
        c.click(16, 0, 88, 20, act::titles, null);
        c.image("menu/ui/tab_green", 108, 0);
        c.item(TAB_ICON, 112, 2);
        c.text("색 닉네임", 130, 6, WHITE);
        c.image("menu/ui/tab_green_join", 109, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: the owned colors, the player's name in each.
        c.text("보유 색상", LEFT, TOP, INK);
        c.right(v.owned().size() + " / " + NickColor.ALL.size() + "개", LEFT + PAGE, TOP, DIM);
        if (!v.ready()) c.centered("색상 정보를 불러오는 중이에요", LEFT, PAGE, 120, DIM);
        else if (v.owned().isEmpty()) c.centered("보유한 색상이 없어요", LEFT, PAGE, 120, DIM);
        int from = v.page() * ROWS;
        for (int i = 0; i < ROWS && from + i < v.owned().size(); i++) {
            NickColor color = v.owned().get(from + i);
            int y = ROW_Y[i];
            boolean on = color.equals(v.equipped());
            c.image(color.equals(v.selected()) ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            icon(c, color, LEFT + 2, y + 2);
            // Dark backing behind the name (chat colors are meant for a dark background), the color's name below.
            c.tagged(color.apply(v.player()), v.player(), LEFT + 24, y + 2, PAGE - 24);
            c.text(color.label() + (on ? " · 장착 중" : ""), LEFT + 25, y + 12, on ? GREEN : DIM);
            c.click(LEFT, y, PAGE, 20, p -> act.select(p, color), HoverEvent.showText(color.apply(color.label())));
        }
        int pages = v.pages();
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, v.page() > 0 ? p -> act.page(p, v.page() - 1) : null);
        c.centered((v.page() + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, v.page() + 1 < pages ? p -> act.page(p, v.page() + 1) : null);

        // Right page, laid out like the title detail: name plate preview, info rows, description, bottom buttons.
        NickColor s = v.selected();
        if (s == null) {
            c.centered(v.ready() ? "색상을 고르면 미리보기가 보여요" : "잠시만 기다려 주세요", RIGHT, PAGE, 140, DIM);
            if (v.ready() && !v.busy() && v.equipped() != null) c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
            return c.b.build();
        }
        boolean on = s.equals(v.equipped());
        Component name = s.apply(v.player());
        c.image("dex/ui/frame_field", RIGHT, TOP);
        c.text(s.label(), RIGHT + 5, TOP + 4, PAPER);
        if (on) c.right("장착 중", RIGHT + 195, TOP + 4, STAMP);
        int cx = RIGHT + PAGE / 2;
        c.plated(name, v.player(), cx, 68, WHITE);
        if (v.face() != null) c.face(v.face(), cx - 8, 81, 2);
        else if (v.head() != null) c.b.playerHead("head", v.head()).position(cx - 4, 81).add();

        c.tagged(name, v.player(), RIGHT, 125, PAGE);
        c.text("색상", RIGHT, 141, DIM);
        c.text(s.label(), RIGHT + 48, 141, INK);
        if (s.gradient()) c.text("그라데이션 · " + s.stops().size() + "색", RIGHT + 120, 141, DIM);
        c.text("상태", RIGHT, 153, DIM);
        c.text(on ? "장착 중" : "보유 중", RIGHT + 48, 153, on ? GREEN : INK);
        c.text("현재 장착", RIGHT, 165, DIM);
        if (v.equipped() == null) c.text("없음", RIGHT + 48, 165, INK);
        else c.tagged(v.equipped().apply(v.player()), v.player(), RIGHT + 48, 165, PAGE - 48);
        c.text("별명(없으면 계정 이름)에 입히는 색이에요.", RIGHT, 179, BODY);
        c.text("채팅·귓속말·이름표에 이 색으로 보여요.", RIGHT, 191, BODY);

        if (!v.ready()) c.button("off", 60, "준비 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (v.busy()) c.button("off", 60, "처리 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (on) c.button("off", 60, "장착 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else c.button("green", 60, "장착", RIGHT + 140, BUTTON_Y, WHITE, p -> act.equip(p, s));
        if (v.ready() && !v.busy() && v.equipped() != null)
            c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
        return c.b.build();
    }

    /** The row icon: a vanilla dye, or a white dye tinted to the (first) color. */
    private static void icon(TitleMenu.Canvas c, NickColor color, int x, int y) {
        String dye = DYES.get(color.id());
        if (dye != null) c.item(dye, x, y);
        else c.tinted("white_dye", x, y, color.first());
    }
}
