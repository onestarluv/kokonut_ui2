package org.crebs.coreui.menu.travel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.travel.TravelCatalog.Category;
import org.crebs.coreui.menu.travel.TravelCatalog.Server;
import org.crebs.coreui.menu.travel.TravelCatalog.Status;
import org.crebs.coreui.menu.vanilla.VanillaCanvas;

import java.util.List;
import java.util.Map;

import static org.crebs.coreui.menu.vanilla.VanillaCanvas.HINT;
import static org.crebs.coreui.menu.vanilla.VanillaCanvas.LABEL;

/**
 * Server travel screen ({@code /coreui travel} and the main menu's 이동 button) in the vanilla container style of
 * the furniture workbench. Whether servers are up, their players and the player's own server come from the proxy
 * (Kokonut-velocity → Kokonut-Core {@code NetworkServers}); 이동 asks the proxy to move the player.
 * <ul>
 *   <li>Left: the server categories (로비 · 스폰 · 거주 · 야생) as stonecutter-style buttons; the chosen one is dark.</li>
 *   <li>Middle: the chosen category's servers, four rows visible with a scroll bar — name, player count and a
 *       coloured dot (원활 · 혼잡 from 80 % · 포화 at the server's max players · 점검 중 when down · 확인 중 before the
 *       first report). The server the player is on
 *       has a green arrow. Hovering a row shows the server's description. A category without servers says so.</li>
 *   <li>Bottom: the chosen server's name and description, and 이동 — dark for a full or closed server, and for the
 *       server the player is already on.</li>
 * </ul>
 * The screen opens on the player's own category and server. Choosing a category or a server only selects it;
 * nothing moves until 이동 is pressed. Travelling only switches server (no arrival points).
 * <p>The panel ({@code tools/dex_glyphs.py travel_glyphs}) already contains the category area, the list area and
 * the scroll track; coordinates here must match it. Vanilla item icons sit at y % 9 == 2.</p>
 */
public final class TravelMenu {
    static final String ID = "kokonut-travel";
    static final int WIDTH = 252, HEIGHT = 136;
    static final String PANEL = "travel/ui/panel_" + WIDTH;
    /** Category buttons (48 x 18) and server rows (166 x 18) share the same four 18 px lines. */
    static final int GRID_Y = 19, ROW_H = 18, ROWS = 4;
    static final int TAB_X = 9, TAB_W = 48;
    static final int LIST_X = 61, ROW_W = 166, NAME_X = 12;
    static final int TRACK_X = LIST_X + ROW_W + 4, TRACK_H = ROWS * ROW_H, SCROLLER_W = 12, SCROLLER_H = 15;
    /** The chosen server under the list: name, two description lines, the button on the right. */
    static final int INFO_X = 8, INFO_Y = 97;
    static final int BUTTON_X = WIDTH - 8 - VanillaCanvas.BUTTON_W, BUTTON_Y = 108;
    /** Text on a tan button or row, and on the dark chosen one; the "you are here" arrow in both. */
    static final TextColor ROW_TEXT = TextColor.color(0x2E2618), ROW_HERE = TextColor.color(0x1F5A12),
            ON_TEXT = TextColor.color(0xFFFFFF), ON_DIM = TextColor.color(0xE0CA9F), ON_HERE = TextColor.color(0xA6E88A);

    /**
     * What is chosen: category id ({@code null} = the current server's), server id ({@code null} = the current
     * server when it is in that category, else the category's first) and the first visible server row.
     */
    public record State(String category, String server, int scroll) {
        /** The 전체 tab (the first one) with the player's own server chosen and scrolled into view (scroll -1). */
        public static final State START = new State(null, null, -1);

        State category(Category c) { return new State(c.id(), null, 0); }
        State server(Category c, Server s) { return new State(c.id(), s.id(), scroll); }
        State scroll(int row) { return new State(category, server, row); }
    }

    public interface Actions {
        void go(Player p, State next);
        void travel(Player p, Server server);
    }

    private TravelMenu() {
    }

    /** False while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey(PANEL) || !g.containsKey("travel/ui/row") || !g.containsKey("travel/ui/tab")
                || !g.containsKey("travel/ui/here") || !g.containsKey("travel/ui/dot_smooth")
                || !g.containsKey("furniture/ui/slot") || !g.containsKey("furniture/ui/button_50")) return false;
        player.playSound(player, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.1f);
        show(coreUi, player, State.START);
        return true;
    }

    private static void show(CoreUi coreUi, Player player, State state) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), catalog(coreUi), state, new Actions() {
            public void go(Player p, State next) { show(coreUi, p, next); }
            public void travel(Player p, Server server) {
                coreUi.close(p);
                if (!sendTravel(coreUi, p, server)) {
                    Messages.send(p, "서버 이동 기능을 쓸 수 없어요. (Kokonut-Core 확인 필요)");
                    return;
                }
                p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4f, 1.2f);
                Messages.send(p, server.name() + "(으)로 이동해요.");
            }
        }));
    }

    /** The network from Core, or every server as 확인 중 without Core (or with an older Core). */
    private static TravelCatalog catalog(CoreUi coreUi) {
        if (coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
            try {
                TravelCatalog catalog = org.crebs.coreui.menu.kokonut.NetworkBridge.catalog(coreUi.getServer());
                if (catalog != null) return catalog;
            } catch (LinkageError olderCore) {
                // falls through
            }
        }
        return TravelCatalog.of(Map.of(), false, null);
    }

    private static boolean sendTravel(CoreUi coreUi, Player player, Server server) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) return false;
        try {
            return org.crebs.coreui.menu.kokonut.NetworkBridge.travel(coreUi.getServer(), player, server.id());
        } catch (LinkageError olderCore) {
            return false;
        }
    }

    /** Rows the category's server list can be scrolled down by. */
    static int maxScroll(Category category) {
        return Math.max(0, category.servers().size() - ROWS);
    }

    /** One frame; pure apart from the callbacks, so tests and the preview tool can build it. */
    public static CoreMenu build(Map<String, Glyph> g, TravelCatalog catalog, State state, Actions act) {
        VanillaCanvas c = new VanillaCanvas(g, ID, "서버 이동", WIDTH, HEIGHT);
        c.image(PANEL, 0, 0);
        c.text("서버 이동", 8, 6, LABEL);
        c.right("전체 접속 " + catalog.online() + "명", WIDTH - 8, 6, HINT);

        List<Category> categories = catalog.categories();
        Category found = catalog.category(state.category());
        if (found == null) found = catalog.categoryOf(catalog.current());
        if (found == null && !categories.isEmpty()) found = categories.getFirst();
        Category category = found;
        if (category == null) {
            c.text("이동할 수 있는 서버가 없어요", INFO_X, INFO_Y, HINT);
            c.button("이동", BUTTON_X, BUTTON_Y, null, tip("이동할 서버가 없어요"));
            return c.build();
        }
        Server picked = category.server(state.server());
        if (picked == null && catalog.current() != null) picked = category.server(catalog.current());
        if (picked == null && !category.servers().isEmpty()) picked = category.servers().getFirst();
        Server chosen = picked;

        // Categories: one button per line (the panel has room for four).
        for (int i = 0; i < categories.size() && i < ROWS; i++) {
            Category cat = categories.get(i);
            int y = GRID_Y + i * ROW_H;
            boolean on = cat == category;
            c.image(on ? "travel/ui/tab_on" : "travel/ui/tab", TAB_X, y);
            c.item(cat.icon(), TAB_X + 1, y + 1);
            c.fitted(cat.name(), TAB_X + 20, y + 5, TAB_W - 20 - 2, on ? ON_TEXT : ROW_TEXT);
            c.click(TAB_X, y, TAB_W, ROW_H, on ? p -> { } : p -> act.go(p, state.category(cat)), categoryTip(cat));
        }

        // Servers of the category: rows from the scrolled position.
        List<Server> servers = category.servers();
        int maxScroll = maxScroll(category);
        // A negative scroll (on opening) brings the chosen server into view; otherwise the player's scroll stays.
        int scroll = state.scroll() >= 0 ? Math.clamp(state.scroll(), 0, maxScroll)
                : Math.clamp(chosen == null ? 0 : servers.indexOf(chosen) - (ROWS - 1), 0, maxScroll);
        for (int i = 0; i < ROWS && scroll + i < servers.size(); i++) {
            Server s = servers.get(scroll + i);
            int y = GRID_Y + i * ROW_H;
            boolean on = s == chosen, here = s.id().equals(catalog.current());
            Status status = s.status();
            c.image(on ? "travel/ui/row_on" : "travel/ui/row", LIST_X, y);
            if (here) c.image("travel/ui/here", LIST_X + 4, y + 5, on ? ON_HERE : ROW_HERE);
            // From the right edge: status label, its dot, the player count; the name takes what is left.
            int labelX = LIST_X + ROW_W - 4 - c.width(status.label), dotX = labelX - 9, countRight = dotX - 6;
            String count = s.count();
            c.text(status.label, labelX, y + 5, on ? ON_DIM : ROW_TEXT);
            c.image(status.dot(), dotX, y + 6);
            c.right(count, countRight, y + 5, on ? ON_DIM : ROW_TEXT);
            c.fitted(s.name(), LIST_X + NAME_X, y + 5, countRight - c.width(count) - 4 - (LIST_X + NAME_X), on ? ON_TEXT : ROW_TEXT);
            c.click(LIST_X, y, ROW_W, ROW_H, on ? p -> { } : p -> act.go(p, state.server(category, s)), serverTip(s, here));
        }
        // Scroll bar: the handle shows the position (dark when everything fits); clicking the track above or below
        // it moves a page.
        int handleY = GRID_Y + (maxScroll == 0 ? 0 : Math.round((TRACK_H - SCROLLER_H) * scroll / (float) maxScroll));
        c.image(maxScroll == 0 ? "travel/ui/scroller_off" : "furniture/ui/scroller", TRACK_X, handleY);
        if (scroll > 0) {
            c.click(TRACK_X, GRID_Y, SCROLLER_W, handleY - GRID_Y, p -> act.go(p, state.scroll(Math.max(0, scroll - ROWS))), tip("위로"));
        }
        if (scroll < maxScroll) {
            int below = handleY + SCROLLER_H;
            c.click(TRACK_X, below, SCROLLER_W, GRID_Y + TRACK_H - below, p -> act.go(p, state.scroll(Math.min(maxScroll, scroll + ROWS))), tip("아래로"));
        }

        if (servers.isEmpty()) c.text("아직 열린 서버가 없어요", LIST_X + NAME_X, GRID_Y + 5, ON_DIM);
        if (chosen == null) {
            c.text(category.name(), INFO_X, INFO_Y, LABEL);
            c.text(category.description(), INFO_X, INFO_Y + 12, HINT);
            c.button("준비 중", BUTTON_X, BUTTON_Y, null, tip(category.name() + " 서버는 아직 준비 중이에요"));
            return c.build();
        }

        // The chosen server and 이동.
        boolean here = chosen.id().equals(catalog.current());
        int textWidth = BUTTON_X - 6 - INFO_X;
        c.fitted(chosen.name(), INFO_X, INFO_Y, textWidth, LABEL);
        for (int i = 0; i < chosen.description().size() && i < 2; i++) {
            c.fitted(chosen.description().get(i), INFO_X, INFO_Y + 12 + i * 11, textWidth, HINT);
        }
        if (here) {
            c.button("현재 위치", BUTTON_X, BUTTON_Y, null, tip("지금 이 서버에 있어요"));
        } else if (chosen.status().open) {
            c.button("이동", BUTTON_X, BUTTON_Y, p -> act.travel(p, chosen), tip(chosen.name() + "(으)로 이동해요"));
        } else {
            c.button(chosen.status().label, BUTTON_X, BUTTON_Y, null, tip(switch (chosen.status()) {
                case FULL -> "지금은 정원이 가득 찼어요. 잠시 후 다시 시도해 주세요.";
                case NOT_READY -> "아직 열리지 않은 서버예요.";
                default -> "지금은 들어갈 수 없는 서버예요.";
            }));
        }
        return c.build();
    }

    private static HoverEvent<Component> tip(String text) {
        return HoverEvent.showText(Component.text(text));
    }

    private static final TextColor GREY = TextColor.color(0xAAAAAA);

    /** The category's name over its description, server count and players. */
    private static HoverEvent<Component> categoryTip(Category cat) {
        return HoverEvent.showText(Component.text(cat.name()).append(Component.newline())
                .append(Component.text(cat.description(), GREY)).append(Component.newline())
                .append(Component.text(cat.servers().isEmpty() ? "준비 중" : "서버 " + cat.servers().size() + "개 · 접속 " + cat.online() + "명", GREY)));
    }

    /** The server's name over its description and player count, like an item tooltip. */
    private static HoverEvent<Component> serverTip(Server s, boolean here) {
        Component tip = Component.text(s.name() + (here ? " (현재 위치)" : ""));
        for (String line : s.description()) tip = tip.append(Component.newline()).append(Component.text(line, GREY));
        return HoverEvent.showText(tip.append(Component.newline())
                .append(Component.text("접속 " + s.count() + " · " + s.status().label, GREY)));
    }
}
