package org.crebs.coreui.menu.kokonut;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
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
 * kokonut_particle move effects as the 잔상 tab of the 꾸미기 book (same layout and equip flow as {@link DamageSkinMenu}).
 * Permissions ({@code particle.<id>}) and the selection stay in kokonut_particle ({@link Afterimages}); this class only draws.
 *
 * Tab row: 칭호, 색 닉네임 (when available), 데미지 스킨 (when available), 잔상 (active, joined to the book).
 * Left page: every effect with its icon and 사용 중 / 사용 가능 / 잠김; right page: the selected effect and 장착 / 해제.
 */
public final class AfterimageMenu {
    static final String ID = "kokonut-afterimage";
    static final int ROWS = 8;
    /** Vanilla item drawn on the 잔상 tab. */
    static final String TAB_ICON = "blaze_powder";
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258;
    private static final int[] ROW_Y = {45, 72, 99, 126, 153, 180, 207, 234};
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), RED = TextColor.color(0xA83228),
            WHITE = TextColor.color(0xFFFFFF), PAPER = TextColor.color(0xF6ECD0), STAMP = TextColor.color(0xFFD86A);

    /** One effect as drawn. {@code icon} is a vanilla item key. */
    record Effect(String id, String name, String icon, boolean usable) {}

    interface Catalog {
        List<Effect> effects(Player player);
        /** Selected effect id, or null. */
        String selected(Player player);
        boolean select(Player player, String id);
        void clear(Player player);
    }

    /** Which other tabs exist and what they open; null = no tab. */
    record Tabs(Consumer<Player> titles, Consumer<Player> colorNick, Consumer<Player> damageSkin) {}

    record View(List<Effect> effects, String selectedId, Effect shown, int page, boolean colorTab, boolean skinTab) {
        int pages() {
            return Math.max(1, (effects.size() + ROWS - 1) / ROWS);
        }
    }

    private record State(int page, String shown) { }

    interface Actions {
        void show(Player p, Effect effect);
        void page(Player p, int page);
        void equip(Player p, Effect effect);
        void unequip(Player p);
        void titles(Player p);
        void colorNick(Player p);
        void damageSkin(Player p);
        void close(Player p);
    }

    private AfterimageMenu() {
    }

    public static boolean open(CoreUi coreUi, Catalog catalog, Player player, Tabs tabs) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("menu/ui/tab") || !g.containsKey("menu/ui/tab_join") || !g.containsKey("menu/ui/tab_off")
                || !g.containsKey("dex/ui/frame_field") || !g.containsKey("dex/ui/page_0_0")) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, catalog, tabs, player, new State(0, null));
        return true;
    }

    private static void show(CoreUi coreUi, Catalog catalog, Tabs tabs, Player player, State state) {
        if (!player.isOnline()) return;
        List<Effect> effects = catalog.effects(player);
        String selected = catalog.selected(player);
        int page = Math.clamp(state.page(), 0, Math.max(1, (effects.size() + ROWS - 1) / ROWS) - 1);
        Effect shown = pick(effects, page, state.shown(), selected);
        var g = coreUi.getGlyphs().all();
        View view = new View(effects, selected, shown, page,
                tabs.colorNick() != null && g.containsKey("menu/ui/tab_green_off"), tabs.damageSkin() != null);
        State now = new State(page, shown == null ? null : shown.id());
        coreUi.open(player, build(g, view, new Actions() {
            public void show(Player p, Effect effect) { AfterimageMenu.show(coreUi, catalog, tabs, p, new State(now.page(), effect.id())); }
            public void page(Player p, int to) { AfterimageMenu.show(coreUi, catalog, tabs, p, new State(to, null)); }
            public void equip(Player p, Effect effect) {
                if (catalog.select(p, effect.id())) {
                    p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
                    org.crebs.coreui.Messages.send(p, effect.name() + " 잔상을 장착했어요.");
                } else {
                    p.playSound(p, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
                    org.crebs.coreui.Messages.send(p, "이 잔상을 장착할 수 없어요.");
                }
                AfterimageMenu.show(coreUi, catalog, tabs, p, now);
            }
            public void unequip(Player p) {
                catalog.clear(p);
                p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.9f);
                org.crebs.coreui.Messages.send(p, "잔상을 해제했어요.");
                AfterimageMenu.show(coreUi, catalog, tabs, p, now);
            }
            public void titles(Player p) { tabs.titles().accept(p); }
            public void colorNick(Player p) { if (tabs.colorNick() != null) tabs.colorNick().accept(p); }
            public void damageSkin(Player p) { if (tabs.damageSkin() != null) tabs.damageSkin().accept(p); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** The kept choice, else the equipped effect on this page, else the page's first row. */
    static Effect pick(List<Effect> effects, int page, String wanted, String selected) {
        if (wanted != null) for (Effect e : effects) if (e.id().equals(wanted)) return e;
        List<Effect> rows = effects.subList(Math.min(effects.size(), page * ROWS), Math.min(effects.size(), (page + 1) * ROWS));
        if (selected != null) for (Effect e : rows) if (e.id().equals(selected)) return e;
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** X of the 잔상 tab: after 칭호, then 색 닉네임 and 데미지 스킨 when they are shown. */
    static int tabX(boolean colorTab, boolean skinTab) {
        return 108 + (colorTab ? 92 : 0) + (skinTab ? 92 : 0);
    }

    /** The 잔상 tab (red, inactive) at {@code x}; drawn by the other 꾸미기 screens. */
    static void tab(TitleMenu.Canvas c, int x, Consumer<Player> onClick) {
        c.image("menu/ui/tab_off", x, 0);
        c.item(TAB_ICON, x + 4, 2);
        c.text("잔상", x + 22, 6, WHITE);
        c.click(x, 0, 88, 20, onClick, HoverEvent.showText(Component.text("걸을 때 남는 잔상 고르기")));
    }

    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        TitleMenu.Canvas c = new TitleMenu.Canvas(g, ID, "잔상");
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab_off", 16, 0);
        c.item("name_tag", 20, 2);
        c.text("칭호", 38, 6, WHITE);
        c.click(16, 0, 88, 20, act::titles, null);
        if (v.colorTab()) {
            c.image("menu/ui/tab_green_off", 108, 0);
            c.item(ColorNickMenu.TAB_ICON, 112, 2);
            c.text("색 닉네임", 130, 6, WHITE);
            c.click(108, 0, 88, 20, act::colorNick, null);
        }
        if (v.skinTab()) TitleMenu.skinTab(c, DamageSkinMenu.tabX(v.colorTab()), act::damageSkin);
        int tab = tabX(v.colorTab(), v.skinTab());
        c.image("menu/ui/tab", tab, 0);
        c.item(TAB_ICON, tab + 4, 2);
        c.text("잔상", tab + 22, 6, WHITE);
        c.image("menu/ui/tab_join", tab + 1, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: every effect.
        long usable = v.effects().stream().filter(Effect::usable).count();
        c.text("잔상 목록", LEFT, TOP, INK);
        c.right(usable + " / " + v.effects().size() + "개", LEFT + PAGE, TOP, DIM);
        if (v.effects().isEmpty()) c.centered("등록된 잔상이 없어요", LEFT, PAGE, 120, DIM);
        int from = v.page() * ROWS;
        for (int i = 0; i < ROWS && from + i < v.effects().size(); i++) {
            Effect e = v.effects().get(from + i);
            int y = ROW_Y[i];
            boolean on = e.id().equals(v.selectedId());
            c.image(v.shown() != null && v.shown().id().equals(e.id()) ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(e.icon(), LEFT + 2, y + 2);
            c.fitted(e.name(), LEFT + 25, y + 2, PAGE - 30, INK);
            c.text(on ? "사용 중" : e.usable() ? "사용 가능" : "잠김", LEFT + 25, y + 12, on ? GREEN : e.usable() ? DIM : RED);
            c.click(LEFT, y, PAGE, 20, p -> act.show(p, e), HoverEvent.showText(Component.text(e.name() + " 잔상", WHITE)));
        }
        int pages = v.pages();
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, v.page() > 0 ? p -> act.page(p, v.page() - 1) : null);
        c.centered((v.page() + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, v.page() + 1 < pages ? p -> act.page(p, v.page() + 1) : null);

        // Right page: the chosen effect.
        Effect s = v.shown();
        if (s == null) {
            c.centered("잔상을 고르면 정보가 보여요", RIGHT, PAGE, 140, DIM);
            if (v.selectedId() != null) c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
            return c.b.build();
        }
        boolean on = s.id().equals(v.selectedId());
        c.image("dex/ui/frame_field", RIGHT, TOP);
        c.text(s.name(), RIGHT + 5, TOP + 4, PAPER);
        if (on) c.right("사용 중", RIGHT + 195, TOP + 4, STAMP);
        c.item(s.icon(), RIGHT + (PAGE - 16) / 2, 65);

        c.text(s.name() + " 잔상", RIGHT, 125, INK);
        c.text("상태", RIGHT, 141, DIM);
        c.text(on ? "사용 중" : s.usable() ? "사용 가능" : "잠김", RIGHT + 48, 141, on ? GREEN : s.usable() ? INK : RED);
        c.text("현재 장착", RIGHT, 153, DIM);
        String current = v.selectedId() == null ? "없음" : v.effects().stream().filter(e -> e.id().equals(v.selectedId()))
                .map(Effect::name).findFirst().orElse(v.selectedId());
        c.text(current, RIGHT + 48, 153, INK);
        c.text("걸어 다니면 발밑에 잔상이 남아요.", RIGHT, 179, BODY);
        if (!s.usable()) c.text("아직 얻지 못한 잔상이에요.", RIGHT, 191, RED);

        if (on) c.button("off", 60, "사용 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (s.usable()) c.button("green", 60, "장착", RIGHT + 140, BUTTON_Y, WHITE, p -> act.equip(p, s));
        else c.button("off", 60, "잠김", RIGHT + 140, BUTTON_Y, DIM, null);
        if (v.selectedId() != null) c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
        return c.b.build();
    }
}
