package org.crebs.coreui.menu.dex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.dex.DexCatalog.*;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.util.*;
import java.util.function.Consumer;

/**
 * Book-style item dex (categories: 광물·농작물·식물·물고기·유물·기타) drawn with the bundled {@code dex/...} glyphs.
 * Like {@code QuestPrototype}, every click rebuilds the menu from an immutable {@link State}.
 * All sizes are GUI pixels; the book is 456×272 with tabs above it and bookmarks on its right edge.
 */
public final class ItemDexMenu {
    static final int WIDTH = 480, HEIGHT = 292;
    private static final int BOOK_Y = 20, TOP = 31, LEFT = 15, RIGHT = 240, PAGE = 200;
    private static final int GRID_COLS = 8, GRID_ROWS = 4, PER_PAGE = GRID_COLS * GRID_ROWS, BUTTON_Y = 258;
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), GOLD = TextColor.color(0xA8740A),
            RED = TextColor.color(0xA83A2A), PURPLE = TextColor.color(0x7A4FB0), PAPER = TextColor.color(0xF6ECD0),
            WHITE = TextColor.color(0xFFFFFF), STAMP = TextColor.color(0xFFD86A),
            SILHOUETTE = TextColor.color(0x3A2A1A);

    public enum View { DEX, REWARD, STATS }

    public enum Filter {
        ALL("전체"), FOUND("등록"), MISSING("미등록");
        final String label;
        Filter(String label) { this.label = label; }
    }

    /** {@code claimed} holds milestone ids and {@code "entry:<id>"} registration rewards taken this session. */
    record State(View view, String category, String selected, Filter filter, int page, Set<String> claimed) {
        static State initial(DexCatalog catalog) {
            String category = catalog.categories().getFirst().key();
            return new State(View.DEX, category, firstFound(catalog.category(category)), Filter.ALL, 0, Set.of());
        }

        State with(View v) { return new State(v, category, selected, filter, page, claimed); }
        State category(Category c) { return new State(view, c.key(), firstFound(c), Filter.ALL, 0, claimed); }
        State select(String id) { return new State(view, category, id, filter, page, claimed); }
        State filter(Filter f) { return new State(view, category, selected, f, 0, claimed); }
        State page(int p) { return new State(view, category, selected, filter, p, claimed); }
        State claim(String id) {
            Set<String> next = new HashSet<>(claimed);
            next.add(id);
            return new State(view, category, selected, filter, page, Set.copyOf(next));
        }
    }

    private ItemDexMenu() {
    }

    /** Returns false while the dex glyphs are not loaded (pack still generating or images removed). */
    public static boolean open(CoreUi coreUi, Player player) {
        if (!coreUi.getGlyphs().all().containsKey("dex/ui/page_0_0")) return false;
        show(coreUi, player, DexCatalog.SAMPLE, State.initial(DexCatalog.SAMPLE));
        return true;
    }

    private static void show(CoreUi coreUi, Player player, DexCatalog catalog, State state) {
        coreUi.open(player, build(coreUi, coreUi.getGlyphs().all(), catalog, state));
    }

    /** Builds one frame; {@code coreUi} is only used inside click callbacks, so offline checks may pass null. */
    static CoreMenu build(CoreUi coreUi, Map<String, Glyph> glyphs, DexCatalog catalog, State state) {
        Canvas c = new Canvas(coreUi, glyphs, catalog, state);
        c.frame();
        switch (state.view()) {
            case DEX -> { c.dexList(); c.dexDetail(); }
            case REWARD -> { c.rewardCategory(); c.rewardGlobal(); }
            case STATS -> { c.statsOverview(); c.statsDetail(); }
        }
        return c.b.build();
    }

    /** Opens on the newest registration when there is one, otherwise the first registered entry. */
    private static String firstFound(Category category) {
        return category.entries().stream().filter(e -> e.found() && e.isNew()).map(Entry::id).findFirst()
                .or(() -> category.entries().stream().filter(Entry::found).map(Entry::id).findFirst()).orElse("");
    }

    /** One frame under construction; small helpers keep element ids unique. */
    private static final class Canvas {
        final CoreUi coreUi;
        final Map<String, Glyph> g;
        final DexCatalog catalog;
        final State state;
        final Category category;
        final CoreMenuBuilder b;
        int ids;

        Canvas(CoreUi coreUi, Map<String, Glyph> g, DexCatalog catalog, State state) {
            this.coreUi = coreUi;
            this.g = g;
            this.catalog = catalog;
            this.state = state;
            this.category = catalog.category(state.category());
            this.b = CoreMenu.builder("item-dex").title(Component.text("도감"))
                    .theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
        }

        // ------------------------------------------------------------ shared chrome
        void frame() {
            // CoreUi paints text line by line and every dex glyph hangs from the line its top is on, so elements
            // whose top is lower are painted later (on top). The lower book tiles start on a line boundary (y=153).
            for (int col = 0; col < 2; col++) {
                image("ui/page_0_" + col, col * 228, BOOK_Y);
                image("ui/page_1_" + col, col * 228, BOOK_Y + 133);
            }
            List<Category> cats = catalog.categories();
            for (int i = 0; i < cats.size(); i++) {
                Category cat = cats.get(i);
                boolean active = state.view() == View.DEX && cat.key().equals(category.key());
                int x = 16 + i * 60;
                image("ui/tab_" + cat.key() + (active ? "_on" : "_off"), x, 0);
                image("item/" + cat.icon(), x + 4, 2);
                text(cat.label(), x + 22, 6, WHITE);
                // The book (top line 2) is painted after the tab, so a strip on that line reconnects the active tab.
                if (active) image("ui/tab_" + cat.key() + "_join", x + 1, BOOK_Y);
                click(x, 0, 56, 20, p -> go(p, state.with(View.DEX).category(cat)));
            }
            image("ui/close", 432, 0);
            click(432, 0, 20, 20, p -> coreUi.close(p));
            // Bookmarks: {glyph name, vanilla item icon}. The active one sticks out 4px further.
            String[][] marks = {{"dex", "knowledge_book"}, {"reward", "nether_star"}, {"stats", "map"}};
            for (int i = 0; i < marks.length; i++) {
                View view = View.values()[i];
                boolean active = state.view() == view;
                int y = 34 + i * 24, width = active ? 26 : 22;
                image("ui/bookmark_" + marks[i][0] + (active ? "_on" : "_off"), 454, y);
                image("item/" + marks[i][1], 454 + width - 19, y + 2);
                click(454, y, width, 20, p -> go(p, state.with(view)));
            }
        }

        // ------------------------------------------------------------ dex
        void dexList() {
            text(category.label() + " 도감", LEFT, TOP, INK);
            long seen = category.entries().stream().filter(e -> e.status() == Status.SEEN).count();
            right("등록 " + category.registered() + " · 미획득 " + seen + " / " + category.total(), LEFT + PAGE, TOP, DIM);
            progress(LEFT, 44, 200, category.registered() / (double) category.total(), seen / (double) category.total());

            List<Entry> slots = filtered();
            int pages = Math.max(1, (slots.size() + PER_PAGE - 1) / PER_PAGE);
            int page = Math.min(state.page(), pages - 1);
            for (int i = 0; i < PER_PAGE; i++) {
                int index = page * PER_PAGE + i;
                if (index >= slots.size()) break;
                Entry entry = slots.get(index);
                int x = LEFT + 6 + (i % GRID_COLS) * 24, y = 56 + (i / GRID_COLS) * 24;
                if (entry == null) {
                    image("ui/slot_locked", x, y);
                    continue;
                }
                image(entry.id().equals(state.selected()) ? "ui/slot_selected" : "ui/slot_normal", x, y);
                image("item/" + entry.icon(), x + 2, y + 2, entry.found() ? null : SILHOUETTE);
                click(x, y, 20, 20, p -> go(p, state.select(entry.id())));
            }
            if (category.entries().isEmpty()) {
                centered("샘플 데이터는 광물만 있어요", LEFT, PAGE, 160, DIM);
            } else {
                text("■ 등록   ■ 미획득(실루엣)   ? 미발견", LEFT + 6, 156, DIM);
            }

            for (int i = 0; i < Filter.values().length; i++) {
                Filter filter = Filter.values()[i];
                boolean on = filter == state.filter();
                button(on ? "dark" : "paper", 36, filter.label, LEFT + i * 40, BUTTON_Y, on ? PAPER : INK,
                        p -> go(p, state.filter(filter)));
            }
            button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, p -> go(p, state.page(Math.max(0, page - 1))));
            centered((page + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
            button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, p -> go(p, state.page(Math.min(pages - 1, page + 1))));
        }

        /** Entries for the grid in dex order; null marks a not-yet-discovered slot. */
        List<Entry> filtered() {
            List<Entry> all = new ArrayList<>(category.entries());
            while (all.size() < category.total()) all.add(null);
            return switch (state.filter()) {
                case ALL -> all;
                case FOUND -> all.stream().filter(e -> e != null && e.found()).toList();
                case MISSING -> all.stream().filter(e -> e == null || !e.found()).toList();
            };
        }

        void dexDetail() {
            Entry e = category.entries().stream().filter(x -> x.id().equals(state.selected())).findFirst().orElse(null);
            if (e == null) {
                centered("아직 기록된 아이템이 없어요", RIGHT, PAGE, 140, DIM);
                return;
            }
            boolean found = e.found();
            int no = category.entries().indexOf(e) + 1;
            image("ui/frame_" + (found ? e.frame() : "locked"), RIGHT, TOP);
            image("portrait/" + e.icon(), RIGHT + 68, TOP + 10, found ? null : SILHOUETTE);
            text(String.format("No.%03d", no), RIGHT + 5, TOP + 4, PAPER);
            if (!found) right("미획득", RIGHT + 195, TOP + 4, PAPER);
            else if (e.isNew()) right("NEW", RIGHT + 195, TOP + 4, STAMP);

            text(e.name(), RIGHT, 125, INK);
            for (int i = 0; i < 4; i++) image(i < e.rarity() ? "ui/pip_on" : "ui/pip_off", RIGHT + 170 + i * 8, 126);
            right(e.rarityLabel(), RIGHT + 166, 125, found ? PURPLE : DIM);

            String[][] rows = {{"획득처", e.where()}, {"획득 방법", e.how()}, {"누적 획득", String.format("%,d 개", e.obtained())}};
            for (int i = 0; i < rows.length; i++) {
                text(rows[i][0], RIGHT, 141 + i * 12, DIM);
                text(rows[i][1], RIGHT + 48, 141 + i * 12, INK);
            }
            if (found) {
                for (int i = 0; i < e.description().size(); i++) text(e.description().get(i), RIGHT, 179 + i * 12, BODY);
            } else {
                text("등록 조건 · " + e.name() + " 1개 획득", RIGHT, 179, RED);
                right("0 / 1", RIGHT + PAGE, 179, INK);
                text(e.description().getFirst(), RIGHT, 191, DIM);
            }

            text("활용", RIGHT, 213, DIM);
            if (found) {
                for (int i = 0; i < e.uses().size() && i < 3; i++) {
                    int x = RIGHT + 48 + i * 24;
                    image("ui/slot_normal", x, 207);
                    image("item/" + e.uses().get(i), x + 2, 209);
                }
                if (e.uses().isEmpty()) text("기록 없음", RIGHT + 48, 213, DIM);
                if (e.sellPrice() > 0) right("판매 " + e.sellPrice() + " 코인", RIGHT + PAGE, 213, GOLD);
            } else {
                for (int i = 0; i < 3; i++) image("ui/slot_locked", RIGHT + 48 + i * 24, 207);
                button("paper", 52, "획득 힌트", RIGHT + 148, 207, INK,
                        p -> p.sendMessage(Component.text("[도감] " + e.name() + " · " + e.description().getFirst(), GOLD)));
            }

            image("item/gold_nugget", RIGHT, BUTTON_Y + 2);
            text("등록 보상", RIGHT + 20, BUTTON_Y + 6, INK);
            text(e.reward() + " 코인", RIGHT + 60, BUTTON_Y + 6, GOLD);
            String key = "entry:" + e.id();
            if (!found) {
                button("off", 60, "보상 받기", RIGHT + 140, BUTTON_Y, DIM, null);
            } else if (state.claimed().contains(key)) {
                button("off", 60, "수령 완료", RIGHT + 140, BUTTON_Y, DIM, null);
            } else {
                button("green", 60, "보상 받기", RIGHT + 140, BUTTON_Y, WHITE, p -> {
                    p.sendMessage(Component.text("[도감] " + e.name() + " 등록 보상 " + e.reward() + " 코인 (샘플)", GOLD));
                    go(p, state.claim(key));
                });
            }
        }

        // ------------------------------------------------------------ rewards
        void rewardCategory() {
            text("수집 보상 · " + category.label(), LEFT, TOP, INK);
            right("등록 " + category.registered() + " / " + category.total(), LEFT + PAGE, TOP, DIM);
            if (category.milestones().isEmpty()) {
                centered("이 카테고리의 보상은 준비 중이에요", LEFT, PAGE, 120, DIM);
                return;
            }
            milestones(category.milestones(), LEFT, 49);
        }

        void rewardGlobal() {
            text("전체 도감 보상", RIGHT, TOP, INK);
            right("전체 " + catalog.registered() + " / " + catalog.total(), RIGHT + PAGE, TOP, DIM);
            milestones(catalog.globalMilestones(), RIGHT, 49);
            int left = claimable();
            text("받을 수 있는 보상 " + left + "개", RIGHT, 162, left > 0 ? GREEN : DIM);
            if (left > 0) {
                button("green", 100, "모두 받기 (" + left + ")", RIGHT + 100, BUTTON_Y, WHITE, p -> {
                    State next = state;
                    for (Category cat : catalog.categories())
                        for (Milestone m : cat.milestones())
                            if (isClaimable(m)) next = next.claim(m.id());
                    for (Milestone m : catalog.globalMilestones()) if (isClaimable(m)) next = next.claim(m.id());
                    p.sendMessage(Component.text("[도감] 보상 " + left + "개를 받았어요 (샘플)", GOLD));
                    go(p, next);
                });
            } else {
                button("off", 100, "모두 받음", RIGHT + 100, BUTTON_Y, DIM, null);
            }
        }

        void milestones(List<Milestone> list, int x, int y) {
            for (Milestone m : list) {
                boolean locked = m.state() == MilestoneState.LOCKED;
                image("ui/slot_normal", x, y);
                image("item/" + m.icon(), x + 2, y + 2);
                boolean claimable = isClaimable(m);
                text(m.title(), x + 26, y + 1, locked ? DIM : claimable ? GREEN : INK);
                text(m.reward(), x + 26, y + 12, DIM);
                if (claimable) {
                    button("green", 46, "받기", x + 154, y, WHITE, p -> {
                        p.sendMessage(Component.text("[도감] " + m.title() + " 보상: " + m.reward() + " (샘플)", GOLD));
                        go(p, state.claim(m.id()));
                    });
                } else {
                    String label = m.state() == MilestoneState.DONE || state.claimed().contains(m.id()) ? "완료"
                            : locked ? "잠김" : m.progress() + "/" + m.goal();
                    button("off", 46, label, x + 154, y, DIM, null);
                }
                y += 26;
            }
        }

        boolean isClaimable(Milestone m) {
            return m.state() == MilestoneState.CLAIMABLE && !state.claimed().contains(m.id());
        }

        int claimable() {
            int count = 0;
            for (Category cat : catalog.categories()) for (Milestone m : cat.milestones()) if (isClaimable(m)) count++;
            for (Milestone m : catalog.globalMilestones()) if (isClaimable(m)) count++;
            return count;
        }

        // ------------------------------------------------------------ stats
        void statsOverview() {
            text("수집 통계", LEFT, TOP, INK);
            text("전체 수집률", LEFT, 46, DIM);
            int pct = (int) Math.round(100.0 * catalog.registered() / catalog.total());
            right(catalog.registered() + " / " + catalog.total() + " · " + pct + "%", LEFT + PAGE, 46, GREEN);
            progress(LEFT, 58, 200, catalog.registered() / (double) catalog.total(), 0);
            text("카테고리별", LEFT, 69, DIM);
            int y = 81;
            for (Category cat : catalog.categories()) {
                image("item/" + cat.icon(), LEFT, y);
                text(cat.label(), LEFT + 20, y + 4, INK);
                progress(LEFT + 54, y + 6, 100, cat.registered() / (double) cat.total(), 0);
                right(cat.registered() + " / " + cat.total(), LEFT + PAGE, y + 4, INK);
                click(LEFT, y, PAGE, 16, p -> go(p, state.with(View.DEX).category(cat)));
                y += 18;
            }
        }

        void statsDetail() {
            text("최근 등록", RIGHT, TOP, INK);
            int y = 43;
            for (Recent r : catalog.recent()) {
                image("item/" + r.icon(), RIGHT, y);
                text(r.name(), RIGHT + 20, y + 4, INK);
                text(r.category(), RIGHT + 24 + TextWidths.width(Component.text(r.name())), y + 4, DIM);
                right(r.date(), RIGHT + PAGE, y + 4, DIM);
                y += 18;
            }
            text("가장 많이 얻은 아이템", RIGHT, 122, INK);
            y = 135;
            for (int i = 0; i < catalog.top().size(); i++) {
                TopItem t = catalog.top().get(i);
                text(String.valueOf(i + 1), RIGHT, y + 4, GOLD);
                image("item/" + t.icon(), RIGHT + 10, y);
                text(t.name(), RIGHT + 30, y + 4, INK);
                right(String.format("%,d", t.count()), RIGHT + PAGE, y + 4, INK);
                y += 18;
            }
            text("서버 수집 순위", RIGHT, 194, DIM);
            right("#" + catalog.serverRank() + " / " + catalog.serverPlayers() + "명", RIGHT + PAGE, 194, INK);
            text("서버 최초 등록", RIGHT, 208, DIM);
            right(catalog.firstDiscovery(), RIGHT + PAGE, 208, RED);
            button("paper", 100, "서버 순위 보기", RIGHT + 100, BUTTON_Y, INK,
                    p -> p.sendMessage(Component.text("[도감] 서버 순위는 준비 중이에요", GOLD)));
        }

        // ------------------------------------------------------------ primitives
        void go(Player player, State next) {
            coreUi.open(player, build(coreUi, coreUi.getGlyphs().all(), catalog, next));
        }

        void image(String name, int x, int y) {
            image(name, x, y, null);
        }

        /** {@code tint} multiplies the image colours; {@link #SILHOUETTE} turns an item into a dark shape. */
        void image(String name, int x, int y, TextColor tint) {
            Glyph glyph = Objects.requireNonNull(g.get("dex/" + name), "missing glyph dex/" + name);
            var element = b.glyph("img" + ids++, glyph).position(x, y);
            if (tint != null) element.tint(tint);
            element.add();
        }

        void click(int x, int y, int w, int h, Consumer<Player> onClick) {
            b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick).add();
        }

        void text(String value, int x, int y, TextColor color) {
            Component text = Component.text(value, color);
            b.label("txt" + ids++, text).position(x, y)
                    .size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
        }

        void right(String value, int rightX, int y, TextColor color) {
            text(value, rightX - TextWidths.width(Component.text(value)), y, color);
        }

        void centered(String value, int x, int width, int y, TextColor color) {
            text(value, x + (width - TextWidths.width(Component.text(value))) / 2, y, color);
        }

        /** A pre-drawn button glyph with centred text; a null action draws it without a hotspot (disabled). */
        void button(String kind, int width, String label, int x, int y, TextColor color, Consumer<Player> onClick) {
            image("ui/btn_" + kind + "_" + width, x, y);
            centered(label, x, width, y + 6, color);
            if (onClick != null) click(x, y, width, 20, onClick);
        }

        /** Bar background plus power-of-two fill pieces (green = registered, tan = seen only). */
        void progress(int x, int y, int width, double done, double seen) {
            image("ui/bar_" + width, x, y);
            int inner = width - 2, cursor = x + 1;
            int filled = (int) Math.round(inner * Math.clamp(done, 0, 1));
            int extra = (int) Math.round(inner * Math.clamp(seen, 0, 1));
            cursor = pieces("fill", Math.min(filled, inner), cursor, y + 1);
            pieces("seen", Math.min(extra, inner - filled), cursor, y + 1);
        }

        int pieces(String kind, int amount, int x, int y) {
            for (int piece = 128; piece >= 1; piece /= 2) {
                while (amount >= piece) {
                    image("ui/" + kind + "_" + piece, x, y);
                    x += piece;
                    amount -= piece;
                }
            }
            return x;
        }
    }
}
