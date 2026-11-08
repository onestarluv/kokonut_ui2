package org.crebs.coreui.menu.demo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.util.*;

/**
 * Feasibility prototype of an image-based quest journal: a pre-drawn background split into tiles, image sprites
 * layered on top, Galmuri text and invisible hotspots. Every click rebuilds the menu from an immutable state.
 *
 * Needs the PNGs under plugins/CoreUi/Glyphs/proto (ids "proto/...") plus their ascents in glyphs.yml.
 * Those images are local test material only; do not ship them in a public pack.
 */
public final class QuestPrototype {
    private static final int WIDTH = 641, HEIGHT = 306;
    private static final TextColor TITLE = TextColor.color(0xE8D5A9), GOLD = TextColor.color(0xD9A64A),
            TEXT = TextColor.color(0xE6DFD2), DIM = TextColor.color(0x9C8F7D), GREEN = TextColor.color(0x8FCB6A),
            INK = TextColor.color(0x4A3522), INK_STRONG = TextColor.color(0x2A1A0C);

    enum Tab { AVAILABLE("가능"), ACTIVE("진행"), DONE("완료");
        final String label; Tab(String label) { this.label = label; } }
    private record Objective(String text, int done, int total) {}
    private record Quest(String id, String region, int level, String name, Tab tab, String client,
                         List<String> description, List<Objective> objectives, int gold, int exp) {}
    record State(Tab tab, String selected, Set<String> collapsed) {
        /** Initial state, also used by offline checks. */
        static State initial() { return new State(Tab.AVAILABLE, "letter", Set.of()); }
    }

    // Original sample data for layout testing.
    private static final List<Quest> QUESTS = List.of(
            new Quest("letter", "초록 숲", 12, "잃어버린 편지", Tab.AVAILABLE, "숲지기 엘렌 · 초록 숲 입구",
                    List.of("숲길 어딘가에 떨어진 편지를 찾아 달라는 부탁이에요.", "슬라임이 반짝이는 물건을 좋아한대요."),
                    List.of(new Objective("슬라임 처치", 3, 5), new Objective("편지 조각 수집", 1, 3)), 500, 1200),
            new Quest("herb", "초록 숲", 13, "약초꾼의 부탁", Tab.AVAILABLE, "약초꾼 미라 · 숲속 오두막",
                    List.of("상처에 바를 약초가 모자라요.", "강가 근처에서 푸른 잎을 찾아 주세요."),
                    List.of(new Objective("푸른 잎 채집", 0, 8)), 300, 900),
            new Quest("bridge", "초록 숲", 15, "무너진 다리", Tab.AVAILABLE, "목수 단 · 강가 마을",
                    List.of("다리를 고칠 나무가 필요해요."), List.of(new Objective("단단한 통나무", 2, 10)), 800, 1500),
            new Quest("lamp", "바람 언덕", 18, "꺼진 등불", Tab.AVAILABLE, "등지기 로완 · 언덕 등대",
                    List.of("밤마다 언덕의 등불이 꺼져요.", "누가 그러는지 알아봐 주세요."),
                    List.of(new Objective("등불 다시 켜기", 0, 4), new Objective("범인 찾기", 0, 1)), 1000, 2200),
            new Quest("wind", "바람 언덕", 20, "바람의 노래", Tab.ACTIVE, "음유시인 셀 · 언덕 정상",
                    List.of("바람이 부르는 노래를 모아 오면", "새 노래를 들려준대요."),
                    List.of(new Objective("바람 조각", 4, 6)), 1200, 2600),
            new Quest("cellar", "항구 도시", 24, "지하 창고 정리", Tab.ACTIVE, "상인 보리 · 항구 창고",
                    List.of("창고에 쥐가 너무 많아요."), List.of(new Objective("창고 쥐 처치", 9, 12)), 1500, 3100),
            new Quest("first", "초록 숲", 10, "첫걸음", Tab.DONE, "안내원 하루 · 마을 광장",
                    List.of("마을을 한 바퀴 둘러봤어요."), List.of(new Objective("광장 둘러보기", 1, 1)), 100, 300));

    private QuestPrototype() {
    }

    /** Returns false when the prototype images are not loaded. */
    public static boolean open(CoreUi coreUi, Player player) {
        if (!coreUi.getGlyphs().all().containsKey("proto/bg_0_0")) return false;
        show(coreUi, player, State.initial());
        return true;
    }

    private static void show(CoreUi coreUi, Player player, State state) {
        coreUi.open(player, build(coreUi, coreUi.getGlyphs().all(), state));
    }

    /** Builds one frame; coreUi is only used inside click callbacks, so tests may pass null. */
    static CoreMenu build(CoreUi coreUi, Map<String, Glyph> g, State state) {
        CoreMenuBuilder b = CoreMenu.builder("quest-prototype").title(Component.text("퀘스트"))
                .theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
        int[] ids = {0};
        // Background tiles first: they hang from the first text line, so they are drawn under everything else.
        for (int r = 0, y = 0; r < 3; r++) {
            int x = 0;
            for (int c = 0; c < 3; c++) {
                Glyph tile = g.get("proto/bg_" + r + "_" + c);
                b.glyph("bg" + r + c, tile).position(x, y).add();
                x += tile.width();
                if (c == 2) y += tile.height();
            }
        }

        // Title bar.
        text(b, ids, "퀘스트", 48, 30, TITLE);
        image(b, ids, g, "close", 585, 28);
        b.hotspot("close").position(583, 26).size(15, 15).onClick(p -> coreUi.close(p)).add();

        // Tabs in the 14px strip.
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int x = 44 + i * 48;
            image(b, ids, g, tab == state.tab() ? "pill_selected" : "pill", x, 46);
            centered(b, ids, tab.label, x, 44, 49, tab == state.tab() ? TITLE : DIM);
            b.hotspot("tab-" + tab.name()).position(x, 46).size(44, 14)
                    .onClick(p -> show(coreUi, p, new State(tab, firstOf(tab), state.collapsed()))).add();
        }
        long active = QUESTS.stream().filter(q -> q.tab() == Tab.ACTIVE).count();
        right(b, ids, "진행 중 " + active + " / 5", 595, 49, DIM);

        // Left list: region headers that collapse, then quest rows.
        int y = 70;
        Map<String, List<Quest>> regions = new LinkedHashMap<>();
        for (Quest q : QUESTS) if (q.tab() == state.tab()) regions.computeIfAbsent(q.region(), k -> new ArrayList<>()).add(q);
        for (var entry : regions.entrySet()) {
            if (y > 222) break;
            String region = entry.getKey();
            boolean closed = state.collapsed().contains(region);
            text(b, ids, (closed ? "▶ " : "▼ ") + region + " (" + entry.getValue().size() + ")", 48, y, GOLD);
            b.hotspot("region-" + ids[0]++).position(46, y - 2).size(140, 12).onClick(p -> {
                Set<String> next = new HashSet<>(state.collapsed());
                if (!next.remove(region)) next.add(region);
                show(coreUi, p, new State(state.tab(), state.selected(), Set.copyOf(next)));
            }).add();
            y += 13;
            if (closed) continue;
            for (Quest q : entry.getValue()) {
                if (y + 22 > 234) break;
                boolean selected = q.id().equals(state.selected());
                image(b, ids, g, selected ? "list_item_selected" : "list_item", 48, y);
                image(b, ids, g, q.tab() == Tab.DONE ? "chk_done" : "chk", 54, y + 8);
                text(b, ids, "Lv." + q.level() + " " + q.name(), 66, y + 7, selected ? INK_STRONG : INK); // list_item is a light card
                b.hotspot("quest-" + q.id()).position(48, y).size(136, 22)
                        .onClick(p -> show(coreUi, p, new State(state.tab(), q.id(), state.collapsed()))).add();
                y += 24;
            }
        }
        if (regions.isEmpty()) text(b, ids, "퀘스트가 없어요", 48, 72, DIM);

        // Right detail panel.
        Quest q = QUESTS.stream().filter(x -> x.id().equals(state.selected())).findFirst().orElse(null);
        if (q != null) {
            image(b, ids, g, "portrait_frame", 208, 70);
            image(b, ids, g, "portrait_flora_slime", 213, 75);
            text(b, ids, q.name(), 290, 74, TITLE);
            text(b, ids, "권장 레벨 " + q.level() + " · " + q.region(), 290, 88, DIM);
            text(b, ids, "의뢰인  " + q.client(), 290, 101, TEXT);
            int done = q.objectives().stream().mapToInt(o -> Math.min(o.done(), o.total())).sum();
            int total = q.objectives().stream().mapToInt(Objective::total).sum();
            text(b, ids, "진행도", 290, 118, DIM);
            progress(b, ids, g, 322, 120, done / (double) total);
            right(b, ids, done + " / " + total, 592, 118, GREEN);

            // Description above the background's divider (y≈178); objectives and rewards in its two lower cells.
            int dy = 150;
            for (String line : q.description()) { text(b, ids, line, 210, dy, TEXT); dy += 12; }
            text(b, ids, "목표", 210, 186, GOLD);
            dy = 199;
            for (Objective o : q.objectives()) {
                image(b, ids, g, o.done() >= o.total() ? "chk_done" : "chk", 212, dy + 1);
                text(b, ids, o.text(), 224, dy, TEXT);
                right(b, ids, o.done() + " / " + o.total(), 430, dy, o.done() >= o.total() ? GREEN : TEXT);
                dy += 12;
            }
            text(b, ids, "보상", 446, 186, GOLD);
            image(b, ids, g, "small_coin", 446, 197);
            text(b, ids, String.format("%,d 골드", q.gold()), 466, 201, GOLD);
            image(b, ids, g, "small_scroll", 446, 215);
            text(b, ids, String.format("경험치 %,d", q.exp()), 466, 219, TEXT);
        }

        // Bottom bar.
        text(b, ids, "퀘스트를 누르면 자세한 내용이 보여요", 48, 252, DIM);
        String primary = state.tab() == Tab.AVAILABLE ? "수락하기" : state.tab() == Tab.ACTIVE ? "포기하기" : "다시 보기";
        button(b, ids, g, "button", "위치 찾기", 368, 246, p -> p.sendMessage("[프로토타입] 위치 찾기"));
        button(b, ids, g, "button_primary", primary, 484, 246,
                p -> p.sendMessage("[프로토타입] " + primary + (q == null ? "" : ": " + q.name())));
        return b.build();
    }

    private static String firstOf(Tab tab) {
        return QUESTS.stream().filter(q -> q.tab() == tab).map(Quest::id).findFirst().orElse("");
    }

    private static void image(CoreMenuBuilder b, int[] ids, Map<String, Glyph> g, String name, int x, int y) {
        b.glyph("img" + ids[0]++, Objects.requireNonNull(g.get("proto/" + name), "missing proto/" + name)).position(x, y).add();
    }

    private static void text(CoreMenuBuilder b, int[] ids, String value, int x, int y, TextColor color) {
        Component text = Component.text(value, color);
        b.label("txt" + ids[0]++, text).position(x, y).size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
    }

    private static void right(CoreMenuBuilder b, int[] ids, String value, int rightX, int y, TextColor color) {
        text(b, ids, value, rightX - TextWidths.width(Component.text(value)), y, color);
    }

    private static void centered(CoreMenuBuilder b, int[] ids, String value, int x, int width, int y, TextColor color) {
        text(b, ids, value, x + (width - TextWidths.width(Component.text(value))) / 2, y, color);
    }

    private static void button(CoreMenuBuilder b, int[] ids, Map<String, Glyph> g, String skin, String label, int x, int y,
                               java.util.function.Consumer<Player> onClick) {
        image(b, ids, g, skin, x, y);
        centered(b, ids, label, x, 110, y + 6, TITLE);
        b.hotspot("btn" + ids[0]++).position(x, y).size(110, 20).onClick(onClick).add();
    }

    /** Fills the 180px bar with power-of-two pieces, like the source pack's ifill_1..128 sprites. */
    private static void progress(CoreMenuBuilder b, int[] ids, Map<String, Glyph> g, int x, int y, double ratio) {
        image(b, ids, g, "ibar", x, y);
        int remaining = (int) Math.round(180 * Math.clamp(ratio, 0, 1)), cursor = x;
        for (int piece = 128; piece >= 1; piece /= 2) {
            if (remaining >= piece) {
                image(b, ids, g, "ifill_" + piece, cursor, y);
                cursor += piece;
                remaining -= piece;
            }
        }
    }
}
