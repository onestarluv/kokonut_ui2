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
import java.util.Objects;
import java.util.function.Consumer;

/**
 * kokonut_damage skins as the 데미지 스킨 tab of the 꾸미기 book (same layout and equip flow as {@link ColorNickMenu}).
 * Ownership ({@code betterdamage.skin.<id>}) and the selection stay in kokonut_damage ({@link DamageSkins}); this
 * class only draws.
 *
 * Tab row: 칭호 (red, inactive), 색 닉네임 (green, inactive, when Core has nickname colors) and 데미지 스킨 (red,
 * active, joined to the book). Left page: owned skins with the coupon icon and a small "123" in the skin's digits;
 * right page: the skin's digits 1 to 0 in the field frame, info and the 장착 / 해제 buttons. Digit and icon glyphs
 * ({@code damage/digit/<id>/<n>}, {@code damage/icon/<id>}) come from the pack build (DamageSkinGlyphs).
 */
public final class DamageSkinMenu {
    static final String ID = "kokonut-damageskin";
    static final int ROWS = 8;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258;
    /** Same rows as the color screen: 20 px rows, icons on y % 9 == 2. */
    private static final int[] ROW_Y = {45, 72, 99, 126, 153, 180, 207, 234};
    /** The preview reads like a damage number: 1 to 0. */
    static final String PREVIEW = "1234567890", ROW_PREVIEW = "123";
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), WHITE = TextColor.color(0xFFFFFF),
            PAPER = TextColor.color(0xF6ECD0), STAMP = TextColor.color(0xFFD86A);

    /** One skin as drawn: kokonut_damage id (file name) and display name. */
    record Skin(String id, String name) {}

    /** What the screen needs from kokonut_damage. */
    interface Catalog {
        /** False while kokonut_damage is still loading the player's data. */
        boolean ready(Player player);
        List<Skin> owned(Player player);
        int total();
        /** Equipped skin id, or null. */
        String equipped(Player player);
        boolean equip(Player player, String id);
        void unequip(Player player);
    }

    /** What one frame shows. {@code equipped} and {@code selected} may be null. */
    record View(boolean ready, List<Skin> owned, int total, String equipped, Skin selected, int page, boolean colorTab) {
        int pages() {
            return Math.max(1, (owned.size() + ROWS - 1) / ROWS);
        }
    }

    private record State(int page, String selected) { }

    interface Actions {
        void select(Player p, Skin skin);
        void page(Player p, int page);
        void equip(Player p, Skin skin);
        void unequip(Player p);
        void titles(Player p);
        void colorNick(Player p);
        void close(Player p);
    }

    private DamageSkinMenu() {
    }

    /** False while the book glyphs are not loaded. {@code colorTab} may be null (no 색 닉네임 tab). */
    public static boolean open(CoreUi coreUi, Catalog catalog, Player player, Consumer<Player> titleTab, Consumer<Player> colorTab) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("menu/ui/tab") || !g.containsKey("menu/ui/tab_join") || !g.containsKey("menu/ui/tab_off")
                || !g.containsKey("dex/ui/frame_field") || !g.containsKey("dex/ui/page_0_0")) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, catalog, titleTab, colorTab, player, new State(0, null));
        return true;
    }

    private static void show(CoreUi coreUi, Catalog catalog, Consumer<Player> titleTab, Consumer<Player> colorTab, Player player, State state) {
        if (!player.isOnline()) return;
        boolean ready = catalog.ready(player);
        List<Skin> owned = ready ? catalog.owned(player) : List.of();
        String equipped = catalog.equipped(player);
        int page = Math.clamp(state.page(), 0, Math.max(1, (owned.size() + ROWS - 1) / ROWS) - 1);
        Skin selected = pick(owned, page, state.selected(), equipped);
        View view = new View(ready, owned, catalog.total(), equipped, selected, page,
                colorTab != null && coreUi.getGlyphs().all().containsKey("menu/ui/tab_green_off"));
        State now = new State(page, selected == null ? null : selected.id());
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, CoreViews.afterimageTab(coreUi), new Actions() {
            public void select(Player p, Skin skin) { show(coreUi, catalog, titleTab, colorTab, p, new State(now.page(), skin.id())); }
            public void page(Player p, int to) { show(coreUi, catalog, titleTab, colorTab, p, new State(to, null)); }
            public void equip(Player p, Skin skin) {
                if (catalog.equip(p, skin.id())) {
                    p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
                    org.crebs.coreui.Messages.send(p, skin.name() + " 데미지 스킨을 장착했어요.");
                } else {
                    p.playSound(p, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
                    org.crebs.coreui.Messages.send(p, "이 데미지 스킨을 장착할 수 없어요.");
                }
                show(coreUi, catalog, titleTab, colorTab, p, now);
            }
            public void unequip(Player p) {
                catalog.unequip(p);
                p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.9f);
                org.crebs.coreui.Messages.send(p, "데미지 스킨을 해제했어요.");
                show(coreUi, catalog, titleTab, colorTab, p, now);
            }
            public void titles(Player p) { titleTab.accept(p); }
            public void colorNick(Player p) { if (colorTab != null) colorTab.accept(p); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** The kept selection if still owned, else the equipped skin on this page, else the page's first row. */
    static Skin pick(List<Skin> owned, int page, String wanted, String equipped) {
        if (wanted != null) for (Skin s : owned) if (s.id().equals(wanted)) return s;
        List<Skin> rows = owned.subList(Math.min(owned.size(), page * ROWS), Math.min(owned.size(), (page + 1) * ROWS));
        if (equipped != null) for (Skin s : rows) if (s.id().equals(equipped)) return s;
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** X of the 데미지 스킨 tab: third with the 색 닉네임 tab, else second. */
    static int tabX(boolean colorTab) {
        return colorTab ? 200 : 108;
    }

    /** One frame; pure apart from the callbacks, so tests can build it with sample data. */
    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        return build(g, v, null, act);
    }

    /** {@code afterimageTab} opens the 잔상 screen; null = no tab. */
    static CoreMenu build(Map<String, Glyph> g, View v, Consumer<Player> afterimageTab, Actions act) {
        TitleMenu.Canvas c = new TitleMenu.Canvas(g, ID, "데미지 스킨");
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        // Tabs: 칭호 and 색 닉네임 inactive, 데미지 스킨 active and joined to the book.
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
        int tab = tabX(v.colorTab());
        c.image("menu/ui/tab", tab, 0);
        c.item(TitleMenu.SKIN_TAB_ICON, tab + 4, 2);
        c.text("데미지 스킨", tab + 22, 6, WHITE);
        c.image("menu/ui/tab_join", tab + 1, 20);
        if (afterimageTab != null) AfterimageMenu.tab(c, AfterimageMenu.tabX(v.colorTab(), true), afterimageTab);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: owned skins.
        c.text("보유 스킨", LEFT, TOP, INK);
        c.right(v.owned().size() + " / " + v.total() + "개", LEFT + PAGE, TOP, DIM);
        if (!v.ready()) c.centered("데미지 스킨 정보를 불러오는 중이에요", LEFT, PAGE, 120, DIM);
        else if (v.owned().isEmpty()) {
            c.centered("보유한 데미지 스킨이 없어요", LEFT, PAGE, 117, DIM);
            c.centered("데미지 스킨 쿠폰을 사용해 보세요", LEFT, PAGE, 129, DIM);
        }
        int from = v.page() * ROWS;
        for (int i = 0; i < ROWS && from + i < v.owned().size(); i++) {
            Skin skin = v.owned().get(from + i);
            int y = ROW_Y[i];
            boolean on = skin.id().equals(v.equipped());
            c.image(v.selected() != null && v.selected().id().equals(skin.id()) ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            icon(c, skin, LEFT + 2, y + 2);
            int digits = digitsWidth(g, skin.id(), ROW_PREVIEW);
            c.fitted(skin.name(), LEFT + 25, y + 2, PAGE - 30 - digits, INK);
            c.text(on ? "장착 중" : "보유 중", LEFT + 25, y + 12, on ? GREEN : DIM);
            if (digits > 0) digits(c, skin.id(), ROW_PREVIEW, LEFT + PAGE - 4 - digits, y + 2);
            c.click(LEFT, y, PAGE, 20, p -> act.select(p, skin), HoverEvent.showText(Component.text(skin.name() + " 데미지 스킨", WHITE)));
        }
        int pages = v.pages();
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, v.page() > 0 ? p -> act.page(p, v.page() - 1) : null);
        c.centered((v.page() + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, v.page() + 1 < pages ? p -> act.page(p, v.page() + 1) : null);

        // Right page: the digits 1 to 0 in the field frame, info rows, buttons.
        Skin s = v.selected();
        if (s == null) {
            c.centered(v.ready() ? "스킨을 고르면 미리보기가 보여요" : "잠시만 기다려 주세요", RIGHT, PAGE, 140, DIM);
            if (v.ready() && v.equipped() != null) c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
            return c.b.build();
        }
        boolean on = s.id().equals(v.equipped());
        c.image("dex/ui/frame_field", RIGHT, TOP);
        c.text(s.name(), RIGHT + 5, TOP + 4, PAPER);
        if (on) c.right("장착 중", RIGHT + 195, TOP + 4, STAMP);
        int width = digitsWidth(g, s.id(), PREVIEW);
        if (width > 0) digits(c, s.id(), PREVIEW, RIGHT + (PAGE - width) / 2, TOP + 32);
        else c.centered("미리보기 이미지가 없어요", RIGHT, PAGE, TOP + 36, PAPER);

        c.text(s.name() + " 데미지 스킨", RIGHT, 125, INK);
        c.text("스킨 ID", RIGHT, 141, DIM);
        c.text(s.id(), RIGHT + 48, 141, INK);
        c.text("상태", RIGHT, 153, DIM);
        c.text(on ? "장착 중" : "보유 중", RIGHT + 48, 153, on ? GREEN : INK);
        c.text("현재 장착", RIGHT, 165, DIM);
        String current = v.equipped() == null ? "기본" : v.owned().stream().filter(k -> k.id().equals(v.equipped()))
                .map(Skin::name).findFirst().orElse(v.equipped());
        c.text(current, RIGHT + 48, 165, INK);
        c.text("공격하면 이 숫자로 데미지가 보여요.", RIGHT, 179, BODY);
        c.text("해제하면 기본 숫자로 돌아가요.", RIGHT, 191, BODY);

        if (!v.ready()) c.button("off", 60, "준비 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (on) c.button("off", 60, "장착 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else c.button("green", 60, "장착", RIGHT + 140, BUTTON_Y, WHITE, p -> act.equip(p, s));
        if (v.ready() && v.equipped() != null) c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
        return c.b.build();
    }

    /** The coupon icon from Nexo, or a paper item while the pack has none for this skin. */
    private static void icon(TitleMenu.Canvas c, Skin skin, int x, int y) {
        if (c.g.containsKey("damage/icon/" + skin.id())) c.image("damage/icon/" + skin.id(), x, y);
        else c.item("paper", x, y);
    }

    /** Width of {@code number} in the skin's digit glyphs; 0 when any digit is missing. */
    static int digitsWidth(Map<String, Glyph> g, String skin, String number) {
        int width = 0;
        for (char digit : number.toCharArray()) {
            Glyph glyph = g.get("damage/digit/" + skin + "/" + digit);
            if (glyph == null) return 0;
            width += glyph.width();
        }
        return width;
    }

    private static void digits(TitleMenu.Canvas c, String skin, String number, int x, int y) {
        for (char digit : number.toCharArray()) {
            String id = "damage/digit/" + skin + "/" + digit;
            c.image(id, x, y);
            x += Objects.requireNonNull(c.g.get(id)).width();
        }
    }
}
