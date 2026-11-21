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
 * Auto pickup settings of one pet (Kokonut_Pets {@code PickupFilters}), opened from the pet screen's 자동 줍기 row.
 *
 * Left page: the three modes (일반 · 화이트리스트 · 블랙리스트), radius, and 인벤토리에서 고르기 / 목록 비우기.
 * Right page: the item list as a 7 × 6 grid (click removes), or in the picker the item kinds in your inventory and the
 * summoned pet's bag (click adds or removes; listed ones are framed and checked). The 펫 tab goes back to the pet screen.
 */
public final class PetPickupMenu {
    static final String ID = "kokonut-pet-pickup";
    static final int COLS = 7, ROWS = 6;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258, STEP = 27;
    private static final int[] MODE_Y = {45, 72, 99};
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), WHITE = TextColor.color(0xFFFFFF);

    enum Mode {
        NORMAL("일반", "hopper", "보이는 아이템을 모두 주워요"),
        WHITELIST("화이트리스트", "paper", "목록에 있는 아이템만 주워요"),
        BLACKLIST("블랙리스트", "barrier", "목록에 있는 아이템은 줍지 않아요");
        final String label, icon, help;
        Mode(String label, String icon, String help) { this.label = label; this.icon = icon; this.help = help; }
    }

    /** One listed item: Kokonut_Pets key, vanilla icon id and display name (hover). */
    record Entry(String key, String icon, Component name) { }

    record View(String petId, String petName, int radius, Mode mode, List<Entry> entries, int max) { }

    /** One item kind in the picker and whether it is already listed. */
    record Candidate(Entry entry, boolean listed) { }

    /** What the screen needs from Kokonut_Pets. {@code add} returns the chat message to show, or null when added. */
    interface Catalog {
        View pickup(Player player, String petId, String petName, int radius);
        void mode(Player player, String petId, Mode mode);
        /** Item kinds in the inventory and the summoned pet's bag, at most {@code limit}. */
        List<Candidate> candidates(Player player, String petId, int limit);
        /** Adds or removes; returns the chat message to show, or null. */
        String toggle(Player player, String petId, String key);
        void remove(Player player, String petId, String key);
        void clear(Player player, String petId);
    }

    interface Actions {
        void mode(Player p, Mode mode);
        void picker(Player p, boolean open);
        void toggle(Player p, Entry entry);
        void remove(Player p, Entry entry);
        void clear(Player p);
        void back(Player p);
        void close(Player p);
    }

    private PetPickupMenu() {
    }

    public static void open(CoreUi coreUi, Catalog catalog, Player player, String petId, String petName, int radius, Consumer<Player> back) {
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, catalog, player, petId, petName, radius, back, false);
    }

    private static void show(CoreUi coreUi, Catalog catalog, Player player, String petId, String petName, int radius,
                             Consumer<Player> back, boolean picking) {
        if (!player.isOnline()) return;
        View view = catalog.pickup(player, petId, petName, radius);
        List<Candidate> candidates = picking ? catalog.candidates(player, petId, COLS * ROWS) : List.of();
        Runnable again = () -> show(coreUi, catalog, player, petId, petName, radius, back, picking);
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, picking, candidates, new Actions() {
            public void mode(Player p, Mode mode) {
                catalog.mode(p, petId, mode);
                p.playSound(p, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.2f);
                again.run();
            }
            public void picker(Player p, boolean open) {
                p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.5f, 1.1f);
                show(coreUi, catalog, p, petId, petName, radius, back, open);
            }
            public void toggle(Player p, Entry entry) {
                String problem = catalog.toggle(p, petId, entry.key());
                if (problem != null) {
                    p.playSound(p, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
                    org.crebs.coreui.Messages.send(p, problem);
                } else p.playSound(p, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.2f);
                again.run();
            }
            public void remove(Player p, Entry entry) {
                catalog.remove(p, petId, entry.key());
                p.playSound(p, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 0.9f);
                again.run();
            }
            public void clear(Player p) {
                catalog.clear(p, petId);
                again.run();
            }
            public void back(Player p) { back.accept(p); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** One frame; pure apart from the callbacks. */
    static CoreMenu build(Map<String, Glyph> g, View v, boolean picking, List<Candidate> candidates, Actions act) {
        TitleMenu.Canvas c = new TitleMenu.Canvas(g, ID, "줍기 설정");
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        // Tabs: 펫 (back) inactive, 줍기 설정 active and joined to the book.
        c.image("menu/ui/tab_off", 16, 0);
        c.item(PetMenu.ICON, 20, 2);
        c.text("펫", 38, 6, WHITE);
        c.click(16, 0, 88, 20, act::back, HoverEvent.showText(Component.text("펫 화면으로", WHITE)));
        c.image("menu/ui/tab", 108, 0);
        c.item("hopper", 112, 2);
        c.text("줍기 설정", 130, 6, WHITE);
        c.image("menu/ui/tab_join", 109, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: modes.
        c.fitted(v.petName() + " 줍기 모드", LEFT, TOP, PAGE, INK);
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Mode m = modes[i];
            int y = MODE_Y[i];
            boolean on = m == v.mode();
            c.image(on ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(m.icon, LEFT + 2, y + 2);
            c.text(m.label, LEFT + 25, y + 2, INK);
            if (on) c.right("사용 중", LEFT + PAGE - 2, y + 2, GREEN);
            c.text(m.help, LEFT + 25, y + 12, DIM);
            c.click(LEFT, y, PAGE, 20, on ? p -> { } : p -> act.mode(p, m), null);
        }
        c.text("반경 " + v.radius() + "칸 · 1초마다 가방으로", LEFT, 129, BODY);
        c.text("목록 " + v.entries().size() + " / " + v.max() + "개", LEFT, 141, DIM);
        c.text("Nexo 아이템은 종류별로,", LEFT, 165, DIM);
        c.text("일반 아이템은 재료별로 구분해요.", LEFT, 177, DIM);
        if (picking) c.button("paper", 100, "목록 보기", LEFT, BUTTON_Y, INK, p -> act.picker(p, false));
        else c.button("green", 100, "인벤토리에서 고르기", LEFT, BUTTON_Y, WHITE, p -> act.picker(p, true));
        c.button("paper", 98, "목록 비우기", LEFT + 102, BUTTON_Y, INK, v.entries().isEmpty() ? null : act::clear);

        if (picking) picker(c, candidates, act);
        else list(c, v, act);
        return c.b.build();
    }

    /** Right page: the list (click removes). */
    private static void list(TitleMenu.Canvas c, View v, Actions act) {
        c.text("아이템 목록", RIGHT, TOP, INK);
        c.right(v.mode() == Mode.WHITELIST ? "이것만 줍기" : v.mode() == Mode.BLACKLIST ? "이것은 안 줍기" : "일반 모드: 쓰지 않음",
                RIGHT + PAGE, TOP, v.mode() == Mode.NORMAL ? DIM : GREEN);
        if (v.entries().isEmpty()) {
            c.centered("목록이 비어 있어요", RIGHT, PAGE, 117, DIM);
            c.centered("인벤토리에서 고르기를 눌러 보세요", RIGHT, PAGE, 129, DIM);
        }
        for (int i = 0; i < v.entries().size() && i < COLS * ROWS; i++) {
            Entry e = v.entries().get(i);
            int x = RIGHT + (i % COLS) * STEP, y = 45 + (i / COLS) * STEP;
            c.image("dex/ui/slot_normal", x, y);
            c.item(e.icon(), x + 2, y + 2);
            c.click(x, y, 20, 20, p -> act.remove(p, e), hover(e.name(), "누르면 목록에서 빠져요"));
        }
        c.text(v.mode() == Mode.NORMAL ? "화이트·블랙리스트를 고르면 목록이 쓰여요" : "아이템을 누르면 목록에서 빠져요", RIGHT, 213, DIM);
    }

    /** Right page: item kinds in the inventory and the summoned pet's bag (click adds or removes). */
    private static void picker(TitleMenu.Canvas c, List<Candidate> candidates, Actions act) {
        c.text("인벤토리 · 펫 가방", RIGHT, TOP, INK);
        c.right("누르면 넣기 / 빼기", RIGHT + PAGE, TOP, GREEN);
        if (candidates.isEmpty()) {
            c.centered("인벤토리와 가방이 비어 있어요", RIGHT, PAGE, 117, DIM);
            c.centered("펫 가방은 소환 중일 때만 보여요", RIGHT, PAGE, 129, DIM);
        }
        for (int i = 0; i < candidates.size() && i < COLS * ROWS; i++) {
            Candidate cand = candidates.get(i);
            Entry e = cand.entry();
            int x = RIGHT + (i % COLS) * STEP, y = 45 + (i / COLS) * STEP;
            c.image(cand.listed() ? "dex/ui/slot_selected" : "dex/ui/slot_normal", x, y);
            c.item(e.icon(), x + 2, y + 2);
            if (cand.listed()) c.image("skill/ui/check", x + 12, y + 12);
            c.click(x, y, 20, 20, p -> act.toggle(p, e), hover(e.name(), cand.listed() ? "누르면 목록에서 빠져요" : "누르면 목록에 들어가요"));
        }
        c.text("체크된 아이템이 목록에 있는 아이템이에요", RIGHT, 213, DIM);
    }

    private static net.kyori.adventure.text.event.HoverEventSource<?> hover(Component name, String hint) {
        return HoverEvent.showText(name.append(Component.newline()).append(Component.text(hint, TextColor.color(0xAAAAAA))));
    }
}
