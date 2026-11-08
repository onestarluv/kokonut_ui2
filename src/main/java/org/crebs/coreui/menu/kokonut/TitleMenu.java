package org.crebs.coreui.menu.kokonut;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import kr.gomsv.kokonutCore.api.title.TitleCatalog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Kokonut-Core titles as a CoreUi dialog (the dex's book layout). Data, ownership and saving stay in Core's
 * {@link TitleCatalog}; this class only draws. Only load it after confirming Kokonut-Core is enabled.
 *
 * Left page: search field and the owned titles; right page: chat preview, description and equip buttons.
 * Search opens a small native dialog with a text field (dialog bodies cannot hold inputs that our canvas clicks
 * would send), then reopens this screen filtered by {@link TitleSearch}.
 * Text never straddles y = 153, where the lower book tiles start (they are painted later and would cover it).
 */
public final class TitleMenu {
    static final String ID = "kokonut-title";
    static final int WIDTH = 480, HEIGHT = 292, ROWS = 7, DESC_LINES = 6;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258, SEARCH_W = 176;
    private static final int[] ROW_Y = {72, 99, 126, 153, 180, 207, 234};
    // Same colours as the dex (ItemDexMenu) and the mailbox.
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), WHITE = TextColor.color(0xFFFFFF),
            PAPER = TextColor.color(0xF6ECD0), STAMP = TextColor.color(0xFFD86A), HINT = TextColor.color(0xA8946A);
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** One owned title as drawn: tag-free name and description lines (no clicks, hovers or custom fonts). */
    record Title(long id, Component name, String plain, List<Component> lines, String description) {
        static Title of(long id, String title, String description) {
            Component name = parse(title);
            List<Component> lines = new ArrayList<>();
            String text = description == null ? "" : description.replace("\\n", "\n");
            if (!text.isBlank()) for (String line : text.split("\\R", -1)) lines.add(parse(line));
            return new Title(id, name, PLAIN.serialize(name), List.copyOf(lines),
                    String.join(" ", lines.stream().map(PLAIN::serialize).toList()));
        }
    }

    /** What one frame shows. {@code shown} is {@code owned} after the search; {@code equipped} may be null. */
    record View(String player, net.kyori.adventure.text.object.PlayerHeadObjectContents head, int[] face, boolean ready, boolean busy,
                List<Title> owned, List<Title> shown, Long equipped, Title selected, int page, String query) {
        int pages() {
            return Math.max(1, (shown.size() + ROWS - 1) / ROWS);
        }

        Title equippedTitle() {
            return equipped == null ? null : owned.stream().filter(t -> t.id() == equipped).findFirst().orElse(null);
        }
    }

    /** Per-player screen state carried between frames. */
    private record State(String query, int page, Long selected) { }

    interface Actions {
        void select(Player p, Title title);
        void page(Player p, int page);
        void search(Player p);
        void clearSearch(Player p);
        void equip(Player p, Title title);
        void unequip(Player p);
        void close(Player p);
    }

    private TitleMenu() {
    }

    /** {@code /칭호} and the main menu: false (Core's chest GUI) while the glyphs are not loaded. */
    public static boolean open(CoreUi coreUi, TitleCatalog catalog, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("menu/ui/field_176") || !g.containsKey("menu/ui/plate_200") || !g.containsKey("menu/ui/tag_200") || !g.containsKey("menu/ui/px_2") || !g.containsKey("dex/ui/page_0_0")) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, catalog, player, new State("", 0, null));
        return true;
    }

    private static void show(CoreUi coreUi, TitleCatalog catalog, Player player, State state) {
        if (!player.isOnline()) return;
        List<Title> owned = catalog.ready(player)
                ? catalog.owned(player).stream().map(e -> Title.of(e.id(), e.title(), e.description())).toList() : List.of();
        List<Title> shown = TitleSearch.filter(owned, state.query(), t -> new TitleSearch.Target(t.id(), t.plain(), t.description()));
        Long equipped = catalog.equipped(player).isPresent() ? catalog.equipped(player).getAsLong() : null;
        int pages = Math.max(1, (shown.size() + ROWS - 1) / ROWS);
        int page = Math.clamp(state.page(), 0, pages - 1);
        Title selected = pick(shown, page, state.selected(), equipped);
        View view = new View(displayName(player), MainMenu.head(player), coreUi.getSkinFaces().face(player), catalog.ready(player), catalog.busy(player), owned, shown, equipped, selected, page, state.query());
        State now = new State(state.query(), page, selected == null ? null : selected.id());
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, new Actions() {
            public void select(Player p, Title title) { show(coreUi, catalog, p, new State(now.query(), now.page(), title.id())); }
            public void page(Player p, int to) { show(coreUi, catalog, p, new State(now.query(), to, null)); }
            public void search(Player p) { askQuery(coreUi, catalog, p, now); }
            public void clearSearch(Player p) { show(coreUi, catalog, p, new State("", 0, now.selected())); }
            public void equip(Player p, Title title) { change(coreUi, catalog, p, now, title.id()); }
            public void unequip(Player p) { change(coreUi, catalog, p, now, null); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** The kept selection if still listed, else the equipped title on this page, else the page's first row. */
    private static Title pick(List<Title> shown, int page, Long wanted, Long equipped) {
        if (wanted != null) for (Title t : shown) if (t.id() == wanted) return t;
        List<Title> rows = shown.subList(Math.min(shown.size(), page * ROWS), Math.min(shown.size(), (page + 1) * ROWS));
        if (equipped != null) for (Title t : rows) if (t.id() == equipped) return t;
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Saves through Core (which sends the chat message), shows "처리 중" meanwhile and redraws when it finishes. */
    private static void change(CoreUi coreUi, TitleCatalog catalog, Player player, State state, Long id) {
        if (catalog.busy(player)) return;
        catalog.equip(player, id, () -> {
            boolean done = id == null ? catalog.equipped(player).isEmpty()
                    : catalog.equipped(player).isPresent() && catalog.equipped(player).getAsLong() == id;
            if (done) player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
            else player.playSound(player, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
            if (stillOpen(coreUi, player)) show(coreUi, catalog, player, state);
        });
        if (stillOpen(coreUi, player)) show(coreUi, catalog, player, state);
    }

    /** The server nickname when set, else the Minecraft name (see {@link CoreBalance#displayName}). */
    static String displayName(Player player) {
        return CoreBalance.displayName(player);
    }

    private static boolean stillOpen(CoreUi coreUi, Player player) {
        return player.isOnline() && coreUi.getSession(player).map(s -> s.getMenu().getId().equals(ID)).orElse(false);
    }

    /** Native dialog with a text field; "검색" reopens the list filtered, "취소" reopens it unchanged. */
    private static void askQuery(CoreUi coreUi, TitleCatalog catalog, Player player, State state) {
        coreUi.removeSession(player);
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton search = ActionButton.builder(Component.text("검색")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> {
                    String query = Objects.requireNonNullElse(response.getText("query"), "").strip();
                    show(coreUi, catalog, player, new State(query, 0, null));
                }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi,
                        () -> show(coreUi, catalog, player, state)), once)).build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("칭호 검색"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "칭호 이름이나 초성, 번호로 찾을 수 있어요.\n예) 코코넛 · ㅋㅋㄴ · #12 — 비우고 검색하면 전체 목록"), 240)))
                        .inputs(List.of(DialogInput.text("query", Component.text("검색어"))
                                .initial(state.query()).maxLength(32).width(200).build()))
                        .build())
                .type(DialogType.confirmation(search, cancel))));
    }

    private static void onMain(CoreUi coreUi, Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(coreUi, task);
    }

    /** One frame; pure apart from the callbacks, so offline checks can build it with sample data. */
    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        Canvas c = new Canvas(g);
        // Chrome: book, title tab (joined to the book), close.
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab", 16, 0);
        c.item("name_tag", 20, 2);
        c.text("칭호", 38, 6, WHITE);
        c.image("menu/ui/tab_join", 17, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: search field and the list.
        boolean searching = !v.query().isEmpty();
        c.text(searching ? "검색 결과" : "보유 칭호", LEFT, TOP, INK);
        c.right(searching ? v.shown().size() + " / " + v.owned().size() + "개" : v.owned().size() + "개", LEFT + PAGE, TOP, DIM);
        // Dark search field of the dex concept; clicking it opens the input dialog.
        c.image("menu/ui/field_176", LEFT, 44);
        if (searching) c.fitted(v.query(), LEFT + 5, 49, SEARCH_W - 10, PAPER);
        else c.text("이름 · 초성 · #번호로 검색", LEFT + 5, 49, HINT);
        c.click(LEFT, 44, SEARCH_W, 18, act::search, HoverEvent.showText(Component.text("눌러서 검색어 입력")));
        if (searching) c.button("paper", 20, "x", LEFT + PAGE - 20, 43, INK, act::clearSearch);

        if (!v.ready()) c.centered("칭호 정보를 불러오는 중이에요", LEFT, PAGE, 120, DIM);
        else if (v.owned().isEmpty()) c.centered("보유한 칭호가 없어요", LEFT, PAGE, 120, DIM);
        else if (v.shown().isEmpty()) {
            c.centered("검색 결과가 없어요", LEFT, PAGE, 117, DIM);
            c.centered("다른 이름이나 초성으로 찾아보세요", LEFT, PAGE, 129, DIM);
        }
        int from = v.page() * ROWS;
        for (int i = 0; i < ROWS && from + i < v.shown().size(); i++) {
            Title t = v.shown().get(from + i);
            int y = ROW_Y[i];
            boolean selected = v.selected() != null && v.selected().id() == t.id();
            boolean on = v.equipped() != null && v.equipped() == t.id();
            c.image(selected ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(on ? "name_tag" : "paper", LEFT + 2, y + 2);
            // Backing at y (the row's own text line, also for the row at y = 153), name at y + 2, number below.
            c.tagged(t.name(), t.plain(), LEFT + 24, y + 2, PAGE - 24);
            c.text("#" + t.id() + (on ? " · 장착 중" : ""), LEFT + 25, y + 12, on ? GREEN : DIM);
            c.click(LEFT, y, PAGE, 20, p -> act.select(p, t), tooltip(t));
        }
        // Pager where the dex has its pager.
        int pages = v.pages();
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, v.page() > 0 ? p -> act.page(p, v.page() - 1) : null);
        c.centered((v.page() + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, v.page() + 1 < pages ? p -> act.page(p, v.page() + 1) : null);

        // Right page, laid out like the dex detail: scene frame, name, info rows, description, bottom buttons.
        Title s = v.selected();
        if (s == null) {
            c.centered(v.ready() ? "칭호를 고르면 정보가 보여요" : "잠시만 기다려 주세요", RIGHT, PAGE, 140, DIM);
            return c.b.build();
        }
        boolean on = v.equipped() != null && v.equipped() == s.id();
        // Frame: the title over the player's head on vanilla-style name plates, as other players see it.
        c.image("dex/ui/frame_field", RIGHT, TOP);
        c.text("#" + s.id(), RIGHT + 5, TOP + 4, PAPER);
        if (on) c.right("장착 중", RIGHT + 195, TOP + 4, STAMP);
        int cx = RIGHT + PAGE / 2;
        c.plated(s.name(), s.plain(), cx, 55, WHITE);
        c.plated(Component.text(v.player()), v.player(), cx, 68, WHITE);
        // Face at 16 px (2x) standing on the ground line (y 97); the 8x8 head object until the skin is loaded.
        if (v.face() != null) c.face(v.face(), cx - 8, 81, 2);
        else if (v.head() != null) c.b.playerHead("head", v.head()).position(cx - 4, 81).add();

        c.tagged(s.name(), s.plain(), RIGHT, 125, PAGE);
        Title current = v.equippedTitle();
        c.text("번호", RIGHT, 141, DIM);
        c.text("#" + s.id(), RIGHT + 48, 141, INK);
        c.text("상태", RIGHT, 153, DIM);
        c.text(on ? "장착 중" : "보유 중", RIGHT + 48, 153, on ? GREEN : INK);
        c.text("현재 장착", RIGHT, 165, DIM);
        if (current == null) c.text("없음", RIGHT + 48, 165, INK);
        else c.tagged(current.name(), current.plain(), RIGHT + 48, 165, PAGE - 48);

        if (s.lines().isEmpty()) c.text("설명이 없어요", RIGHT, 179, DIM);
        for (int i = 0; i < s.lines().size() && i < DESC_LINES; i++) {
            Component line = s.lines().get(i);
            boolean cut = i == DESC_LINES - 1 && s.lines().size() > DESC_LINES;
            c.rich(cut ? Component.text(PLAIN.serialize(line) + " …") : line, PLAIN.serialize(line), RIGHT, 179 + i * 12, PAGE, BODY);
        }
        c.click(RIGHT, 177, PAGE, 72, p -> { }, tooltip(s));

        if (!v.ready()) c.button("off", 60, "준비 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (v.busy()) c.button("off", 60, "처리 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else if (on) c.button("off", 60, "장착 중", RIGHT + 140, BUTTON_Y, DIM, null);
        else c.button("green", 60, "장착", RIGHT + 140, BUTTON_Y, WHITE, p -> act.equip(p, s));
        if (v.ready() && !v.busy() && v.equipped() != null)
            c.button("paper", 52, "해제", RIGHT + 84, BUTTON_Y, INK, act::unequip);
        return c.b.build();
    }

    /** Full name and description, for names cut with "…" and descriptions longer than the page. */
    private static HoverEventSource<?> tooltip(Title t) {
        Component text = t.name();
        for (Component line : t.lines()) text = text.append(Component.newline()).append(line.colorIfAbsent(TextColor.color(0xAAAAAA)));
        return HoverEvent.showText(text);
    }

    /** MiniMessage from the database without clicks, hovers or fonts (menu text is measured in the menu font). */
    static Component parse(String mini) {
        Component parsed;
        try {
            parsed = MINI.deserialize(mini == null ? "" : mini);
        } catch (RuntimeException malformed) {
            parsed = Component.text(Objects.requireNonNullElse(mini, ""));
        }
        return clean(parsed);
    }

    private static Component clean(Component component) {
        Component out = component.clickEvent(null).hoverEvent(null).insertion(null).font(null);
        return out.children(out.children().stream().map(TitleMenu::clean).toList());
    }

    private static boolean bold(Component component) {
        return component.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                || component.children().stream().anyMatch(TitleMenu::bold);
    }

    private static TextColor firstColor(Component component) {
        if (component.color() != null) return component.color();
        for (Component child : component.children()) {
            TextColor color = firstColor(child);
            if (color != null) return color;
        }
        return null;
    }

    private static final class Canvas {
        final Map<String, Glyph> g;
        final CoreMenuBuilder b = CoreMenu.builder(ID).title(Component.text("칭호"))
                .theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
        int ids;

        Canvas(Map<String, Glyph> g) {
            this.g = g;
        }

        void image(String id, int x, int y) {
            b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
        }

        /** Vanilla item icon ({@code item/<key>}, offset-2 positions only), or the "?" icon. */
        void item(String key, int x, int y) {
            Glyph icon = g.get("item/" + key);
            if (icon != null && !icon.supportsOffset(Math.floorMod(y, 9))) icon = null;
            image(icon == null ? "menu/ui/unknown_item" : icon.id(), x, y);
        }

        void click(int x, int y, int w, int h, Consumer<Player> onClick, HoverEventSource<?> hover) {
            var spot = b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick);
            if (hover != null) spot.hover(hover);
            spot.add();
        }

        void label(Component text, int x, int y, int width) {
            b.label("txt" + ids++, text).position(x, y).size(Math.max(1, Math.min(width, WIDTH - x)), 9).add();
        }

        void text(String value, int x, int y, TextColor color) {
            Component text = Component.text(value, color);
            label(text, x, y, TextWidths.width(text));
        }

        /** Text cut with "…" to {@code max} px. */
        void fitted(String value, int x, int y, int max, TextColor color) {
            if (max <= 0) return;
            text(cut(value, max), x, y, color);
        }

        /**
         * Styled text (title colours and gradients) when it fits, else its plain text cut with "…" in its first colour.
         * Bold adds about a pixel per character, which the width table does not count. Returns the drawn width.
         */
        int rich(Component value, String plain, int x, int y, int max, TextColor fallback) {
            int width = TextWidths.width(value) + (bold(value) ? plain.codePointCount(0, plain.length()) : 0);
            if (width <= max) {
                label(value.colorIfAbsent(fallback), x, y, width);
                return width;
            }
            String shown = cut(plain, max);
            Component text = Component.text(shown, Objects.requireNonNullElse(firstColor(value), fallback));
            label(text, x, y, TextWidths.width(text));
            return TextWidths.width(text);
        }

        /**
         * Text centred on {@code cx} over a vanilla-style name plate ({@code menu/ui/plate_<w>}, 25 % black, 11 px).
         * The plate's top is on an earlier text line than the text (text at y + 2), so the text paints over it.
         */
        /**
         * A title name at ({@code x}, {@code y}) on a dark backing ({@code menu/ui/tag_<w>}, 11 px, drawn at y - 2):
         * titles use chat colours meant for a dark background, so light ones vanish on parchment without it.
         * The backing's top is never on a later text line than the text, so the text paints over it. Returns its width.
         */
        int tagged(Component value, String plain, int x, int y, int max) {
            int width = Math.min(max - 6, TextWidths.width(value) + (bold(value) ? plain.codePointCount(0, plain.length()) : 0));
            int tag = Math.min(200, Math.max(24, (width + 6 + 7) / 8 * 8));
            if (tag > max) tag = Math.max(24, max / 8 * 8);
            image("menu/ui/tag_" + tag, x, y - 2);
            rich(value, plain, x + 3, y, tag - 6, WHITE);
            return tag;
        }

        void plated(Component value, String plain, int cx, int y, TextColor color) {
            int max = 192;
            int width = Math.min(max, TextWidths.width(value) + (bold(value) ? plain.codePointCount(0, plain.length()) : 0));
            int plate = Math.min(200, Math.max(24, (width + 6 + 7) / 8 * 8));
            image("menu/ui/plate_" + plate, cx - plate / 2, y);
            rich(value, plain, cx - width / 2, y + 2, max, color);
        }

        /** A face from {@link org.crebs.coreui.skin.SkinFaces}: one tinted {@code menu/ui/px_<scale>} dot per pixel. */
        void face(int[] colours, int x, int y, int scale) {
            Glyph dot = Objects.requireNonNull(g.get("menu/ui/px_" + scale), "missing glyph menu/ui/px_" + scale);
            for (int i = 0; i < 64; i++) {
                b.glyph("img" + ids++, dot).position(x + (i % 8) * scale, y + (i / 8) * scale)
                        .tint(TextColor.color(colours[i])).add();
            }
        }

        static String cut(String value, int max) {
            String shown = value == null ? "" : value;
            if (TextWidths.width(Component.text(shown)) <= max) return shown;
            while (!shown.isEmpty() && TextWidths.width(Component.text(shown + "…")) > max)
                shown = shown.substring(0, shown.offsetByCodePoints(shown.length(), -1));
            return shown + "…";
        }

        void right(String value, int rightX, int y, TextColor color) {
            text(value, rightX - TextWidths.width(Component.text(value)), y, color);
        }

        void centered(String value, int x, int width, int y, TextColor color) {
            text(value, x + (width - TextWidths.width(Component.text(value))) / 2, y, color);
        }

        void button(String kind, int width, String label, int x, int y, TextColor color, Consumer<Player> onClick) {
            image("dex/ui/btn_" + kind + "_" + width, x, y);
            centered(label, x, width, y + 6, color);
            if (onClick != null) click(x, y, width, 20, onClick, null);
        }
    }
}
