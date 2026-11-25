package org.crebs.coreui.menu.travel;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.crebs.coreui.menu.travel.TravelCatalog.Category;
import org.crebs.coreui.menu.travel.TravelCatalog.Live;
import org.crebs.coreui.menu.travel.TravelCatalog.Server;
import org.crebs.coreui.menu.travel.TravelCatalog.Status;
import org.crebs.coreui.menu.travel.TravelMenu.State;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the server travel screen, built offline with stand-in glyphs and fixed proxy reports. */
class TravelMenuTest {
    /** Vanilla item icons only have the font provider for offset 2, like the real pack. */
    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) {
            String id = (String) key;
            return new Glyph(id, 16, 16, 8, 16, id.startsWith("item/") ? 1 << 2 : Glyph.ALL_OFFSETS);
        }
        @Override public boolean containsKey(Object key) { return true; }
    };
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(TravelMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    /** Today's network as the proxy reports it: only spawn and home1 are registered; the player is on home1. */
    private static final TravelCatalog TODAY = TravelCatalog.of(Map.of(
            "spawn", new Live(true, 3, 100), "home1", new Live(true, 7, 100)), true, "home1");
    /** Centre of the 이동 button. */
    private static final int[] GO = {TravelMenu.BUTTON_X + 25, TravelMenu.BUTTON_Y + 10};

    private static final class Log implements TravelMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void go(Player p, State next) { calls.add("go " + next.category() + " " + next.server() + " " + next.scroll()); }
        public void travel(Player p, Server server) { calls.add("travel " + server.id()); }
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<String> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> ((GlyphElement) e).glyph().id()).toList();
    }

    private static void click(CoreMenu menu, int x, int y) {
        for (var element : menu.getElements()) {
            if (!(element instanceof HotspotElement spot)) continue;
            var b = spot.properties().bounds();
            if (x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height()) {
                spot.onClick().accept(PLAYER);
                return;
            }
        }
        fail("no hotspot at " + x + "," + y);
    }

    private static int[] tab(int i) {
        return new int[]{TravelMenu.TAB_X + 24, TravelMenu.GRID_Y + i * TravelMenu.ROW_H + 9};
    }

    private static int[] row(int i) {
        return new int[]{TravelMenu.LIST_X + 80, TravelMenu.GRID_Y + i * TravelMenu.ROW_H + 9};
    }

    @Test
    void definitionsCoverThePlannedServers() {
        List<String> ids = TravelCatalog.CATEGORIES.stream().flatMap(g -> g.servers().stream()).map(TravelCatalog.Definition::id).toList();
        assertEquals(List.of("lobby", "spawn", "home1", "home2", "home3", "home4", "home5", "wild1", "wild2", "wild3", "wild4"), ids);
        // Before the first report every planned server shows, as 확인 중.
        TravelCatalog waiting = TravelCatalog.of(Map.of(), false, null);
        assertEquals(5, waiting.category("home").servers().size());
        assertTrue(waiting.category("home").servers().stream().allMatch(s -> s.status() == Status.UNKNOWN && s.count().equals("…")));
        // After it, every planned server still shows; those the proxy does not have are 준비 중.
        assertEquals(List.of("전체", "서버", "거주", "야생"), TODAY.categories().stream().map(Category::name).toList());
        assertEquals(List.of(11, 2, 5, 4), TODAY.categories().stream().map(c -> c.servers().size()).toList());
        assertEquals(Status.NOT_READY, TODAY.category("home").server("home2").status());
        assertEquals("-", TODAY.category("home").server("home2").count());
        assertEquals(Status.SMOOTH, TODAY.category("server").server("spawn").status());
        assertEquals(10, TODAY.category(TravelCatalog.ALL).online());
        assertEquals(10, TODAY.online(), "전체 is not counted twice");
        assertEquals(TravelCatalog.ALL, TODAY.categoryOf("home1").id(), "전체 comes first");
    }

    @Test
    void opensOnThePlayersOwnServerWhichCannotBeTravelledTo() {
        Log log = new Log();
        CoreMenu menu = TravelMenu.build(GLYPHS, TODAY, State.START, log);
        List<String> texts = texts(menu);
        assertTrue(texts.containsAll(List.of("서버 이동", "전체 접속 10명", "전체", "서버", "거주", "야생",
                "로비", "스폰", "거주 1채널", "거주 2채널", "7/100", "3/100", "원활", "현재 위치")));
        assertFalse(texts.contains("이동"));
        List<String> images = images(menu);
        assertEquals(1, images.stream().filter("travel/ui/here"::equals).count());
        assertTrue(images.contains("furniture/ui/button_50_off"));
        click(menu, GO[0], GO[1]);
        assertEquals(List.of(), log.calls, "already on this server");
    }

    @Test
    void travellingToSpawn() {
        Log log = new Log();
        click(TravelMenu.build(GLYPHS, TODAY, State.START, log), tab(1)[0], tab(1)[1]);
        CoreMenu server = TravelMenu.build(GLYPHS, TODAY, new State("server", null, 0), log);
        assertTrue(texts(server).containsAll(List.of("로비", "스폰", "3/100", "처음 접속하면 도착하는 곳이에요.", "준비 중")),
                "서버 starts on 로비, which is not on the proxy yet");
        click(server, row(1)[0], row(1)[1]);
        CoreMenu spawn = TravelMenu.build(GLYPHS, TODAY, new State("server", "spawn", 0), log);
        assertTrue(texts(spawn).containsAll(List.of("모험가들이 모이는 중심 마을이에요.", "이동")));
        click(spawn, row(1)[0], row(1)[1]);   // already chosen: nothing
        click(spawn, GO[0], GO[1]);
        assertEquals(List.of("go server null 0", "go server spawn 0", "travel spawn"), log.calls);
    }

    @Test
    void serversNotOnTheProxyShowButCannotBeEntered() {
        Log log = new Log();
        CoreMenu wild = TravelMenu.build(GLYPHS, TODAY, new State("wild", null, 0), log);
        assertTrue(texts(wild).containsAll(List.of("야생 1채널", "야생 2채널", "야생 3채널", "야생 4채널", "-")));
        assertEquals(4, images(wild).stream().filter(id -> id.startsWith("travel/ui/row")).count());
        assertEquals(5, texts(wild).stream().filter("준비 중"::equals).count(), "four rows and the button");
        assertTrue(images(wild).contains("furniture/ui/button_50_off"));
        click(wild, GO[0], GO[1]);
        click(TravelMenu.build(GLYPHS, TODAY, new State("server", "lobby", 0), log), GO[0], GO[1]);
        assertEquals(List.of(), log.calls);

        CoreMenu home = TravelMenu.build(GLYPHS, TODAY, new State("home", null, 0), log);
        assertTrue(texts(home).containsAll(List.of("거주 1채널", "거주 2채널", "거주 3채널", "거주 4채널")));
        assertFalse(texts(home).contains("거주 5채널"), "the fifth channel is one scroll down");
        assertEquals(1, TravelMenu.maxScroll(TODAY.category("home")));
    }

    @Test
    void anEmptyCategoryStillSaysSo() {
        TravelCatalog empty = new TravelCatalog(List.of(new Category("x", "분류", "bell", "설명", List.of())), null);
        Log log = new Log();
        CoreMenu menu = TravelMenu.build(GLYPHS, empty, State.START, log);
        assertTrue(texts(menu).containsAll(List.of("아직 열린 서버가 없어요", "준비 중")));
        click(menu, GO[0], GO[1]);
        assertEquals(List.of(), log.calls);
    }

    @Test
    void beforeTheFirstReportServersCanStillBeChosen() {
        TravelCatalog waiting = TravelCatalog.of(Map.of(), false, null);
        Log log = new Log();
        CoreMenu start = TravelMenu.build(GLYPHS, waiting, State.START, log);
        assertTrue(texts(start).containsAll(List.of("로비", "…", "확인 중", "이동")), "no current server: the first category");
        click(start, GO[0], GO[1]);
        assertEquals(List.of("travel lobby"), log.calls, "the proxy decides when nothing is known yet");
    }

    @Test
    void downAndFullServersCannotBeEntered() {
        assertEquals(Status.UNKNOWN, Server.status(null));
        assertEquals(Status.DOWN, Server.status(new Live(false, 0, 0)));
        assertEquals(Status.SMOOTH, Server.status(new Live(true, 5, 0)));
        assertEquals(Status.SMOOTH, Server.status(new Live(true, 79, 100)));
        assertEquals(Status.BUSY, Server.status(new Live(true, 80, 100)));
        assertEquals(Status.FULL, Server.status(new Live(true, 100, 100)));

        TravelCatalog catalog = TravelCatalog.of(Map.of("home1", new Live(true, 1, 100), "home2", new Live(true, 100, 100),
                "home3", new Live(false, 0, 0)), true, "home1");
        for (String[] pick : new String[][]{{"home2", "포화", "100/100"}, {"home3", "점검 중", "-"}}) {
            Log log = new Log();
            CoreMenu menu = TravelMenu.build(GLYPHS, catalog, new State("home", pick[0], 0), log);
            assertTrue(texts(menu).contains(pick[2]));
            assertEquals(2, texts(menu).stream().filter(pick[1]::equals).count(), "in the row and on the button");
            click(menu, GO[0], GO[1]);
            assertEquals(List.of(), log.calls, pick[0]);
        }
    }

    @Test
    void fiveHomeChannelsScroll() {
        Map<String, Live> live = new HashMap<>();
        for (int i = 1; i <= 5; i++) live.put("home" + i, new Live(true, i, 100));
        TravelCatalog catalog = TravelCatalog.of(live, true, "home1");
        Category home = catalog.category("home");
        assertEquals(1, TravelMenu.maxScroll(home));
        Log log = new Log();
        int x = TravelMenu.TRACK_X + 6, top = TravelMenu.GRID_Y + 2, bottom = TravelMenu.GRID_Y + TravelMenu.TRACK_H - 2;
        CoreMenu first = TravelMenu.build(GLYPHS, catalog, new State("home", null, 0), log);
        assertTrue(texts(first).contains("거주 4채널"));
        assertFalse(texts(first).contains("거주 5채널"));
        click(first, x, bottom);
        CoreMenu last = TravelMenu.build(GLYPHS, catalog, new State("home", null, 1), log);
        assertTrue(texts(last).containsAll(List.of("거주 2채널", "거주 5채널")));
        click(last, x, top);
        assertEquals(List.of("go home null 1", "go home null 0"), log.calls);
    }

    @Test
    void theAllTabListsEveryServerAndOpensScrolledToTheOwnOne() {
        TravelCatalog onWild4 = TravelCatalog.of(Map.of("wild4", new Live(true, 2, 100)), true, "wild4");
        CoreMenu menu = TravelMenu.build(GLYPHS, onWild4, State.START, new Log());
        List<String> texts = texts(menu);
        assertTrue(texts.containsAll(List.of("야생 1채널", "야생 4채널", "2/100", "현재 위치")), "the last four rows");
        assertFalse(texts.contains("로비"));
        assertEquals(7, TravelMenu.maxScroll(onWild4.category(TravelCatalog.ALL)));
        // Scrolled back to the top by the player: stays there.
        assertTrue(texts(TravelMenu.build(GLYPHS, onWild4, new State(null, null, 0), new Log())).contains("로비"));
    }

    @Test
    void everyGlyphSitsWhereItsFontCanDrawItAndRowTextStaysInsideItsRow() {
        for (State state : List.of(State.START, new State("server", null, 0), new State("wild", null, 0))) {
            for (var element : TravelMenu.build(GLYPHS, TODAY, state, new Log()).getElements()) {
                var b = element.properties().bounds();
                if (element instanceof GlyphElement image) {
                    assertTrue(image.glyph().supportsOffset(Math.floorMod(b.y(), 9)), image.glyph().id() + " at y " + b.y());
                } else if (element instanceof LabelElement && b.y() > TravelMenu.GRID_Y && b.y() < TravelMenu.GRID_Y + TravelMenu.TRACK_H) {
                    boolean inTab = b.x() < TravelMenu.LIST_X;
                    int end = inTab ? TravelMenu.TAB_X + TravelMenu.TAB_W : TravelMenu.LIST_X + TravelMenu.ROW_W;
                    assertTrue(b.x() + b.width() <= end, "text ends at x " + (b.x() + b.width()) + ", its button at " + end);
                }
            }
        }
    }
}
