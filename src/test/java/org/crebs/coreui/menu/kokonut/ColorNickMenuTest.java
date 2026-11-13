package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.nickname.NickColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the title screen's tab row and the 색 닉네임 tab, built offline with stand-in glyphs. */
class ColorNickMenuTest {
    /** Every glyph exists (16 px, all text-line offsets), as with a fully loaded pack. */
    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) { return new Glyph((String) key, 16, 16, 8, 16); }
        @Override public boolean containsKey(Object key) { return true; }
    };
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(ColorNickMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final List<NickColor> ALL = NickColor.ALL;

    private static NickColor c(String id) {
        return NickColor.byId(id);
    }

    /** Records what the clicked hotspots asked for. */
    private static final class Log implements ColorNickMenu.Actions, TitleMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void select(Player p, NickColor color) { calls.add("select " + color); }
        public void page(Player p, int page) { calls.add("page " + page); }
        public void equip(Player p, NickColor color) { calls.add("equip " + color); }
        public void unequip(Player p) { calls.add("unequip"); }
        public void titles(Player p) { calls.add("titles"); }
        public void close(Player p) { calls.add("close"); }
        public void select(Player p, TitleMenu.Title title) { calls.add("select #" + title.id()); }
        public void search(Player p) { calls.add("search"); }
        public void clearSearch(Player p) { calls.add("clearSearch"); }
        public void equip(Player p, TitleMenu.Title title) { calls.add("equip #" + title.id()); }
        public void colorNick(Player p) { calls.add("colorNick"); }
    }

    private static ColorNickMenu.View view(List<NickColor> owned, NickColor equipped, NickColor selected,
                                           int page, boolean ready, boolean busy) {
        return new ColorNickMenu.View("코코넛", null, null, ready, busy, owned, equipped, selected, page);
    }

    /** Distinct letter colors of a label (a gradient has several). */
    private static java.util.Set<net.kyori.adventure.text.format.TextColor> colors(Component component) {
        java.util.Set<net.kyori.adventure.text.format.TextColor> out = new java.util.HashSet<>();
        if (component instanceof net.kyori.adventure.text.TextComponent text && !text.content().isEmpty() && component.color() != null)
            out.add(component.color());
        for (Component child : component.children()) out.addAll(colors(child));
        return out;
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<String> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> ((GlyphElement) e).glyph().id()).toList();
    }

    /** Clicks the hotspot that covers ({@code x}, {@code y}); fails when there is none. */
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

    private static boolean clickable(CoreMenu menu, int x, int y) {
        return menu.getElements().stream().filter(HotspotElement.class::isInstance).map(e -> e.properties().bounds())
                .anyMatch(b -> x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height());
    }

    @Test
    void tabRowGoesBackToTitlesAndKeepsColorTabActive() {
        Log log = new Log();
        CoreMenu menu = ColorNickMenu.build(GLYPHS, view(ALL, null, c("black"), 0, true, false), log);
        assertEquals(ColorNickMenu.ID, menu.getId());
        assertTrue(images(menu).containsAll(List.of("menu/ui/tab_off", "menu/ui/tab_green", "menu/ui/tab_green_join")));
        assertTrue(texts(menu).containsAll(List.of("칭호", "색 닉네임")));
        click(menu, 20, 5);
        assertEquals(List.of("titles"), log.calls);
        assertFalse(clickable(menu, 120, 5), "the active tab is not a button");
    }

    @Test
    void listsOwnedColorsEightPerPageWithEquippedMark() {
        Log log = new Log();
        CoreMenu first = ColorNickMenu.build(GLYPHS, view(ALL, c("dark_blue"), c("black"), 0, true, false), log);
        assertTrue(texts(first).containsAll(List.of("43 / 43개", "1/6", "남색 · 장착 중", "회색")));
        assertFalse(texts(first).contains("진회색"), "the ninth color is on the next page");
        assertEquals(1, images(first).stream().filter("dex/ui/slot_selected"::equals).count());

        click(first, 20, 75);  // second row
        click(first, 200, 262); // ">"
        assertEquals(List.of("select dark_blue", "page 1"), log.calls);
        assertFalse(clickable(first, 150, 262), "no previous page on the first page");

        CoreMenu second = ColorNickMenu.build(GLYPHS, view(ALL, c("dark_blue"), c("white"), 1, true, false), log);
        assertTrue(texts(second).containsAll(List.of("2/6", "진회색", "하양")));
        CoreMenu last = ColorNickMenu.build(GLYPHS, view(ALL, null, c("gradient11"), 5, true, false), log);
        assertTrue(texts(last).containsAll(List.of("6/6", "그라데이션 11")));
        assertFalse(clickable(last, 200, 262), "no next page on the last page");
    }

    @Test
    void namesAreDrawnInTheirColor() {
        CoreMenu menu = ColorNickMenu.build(GLYPHS, view(List.of(c("aqua")), null, c("aqua"), 0, true, false), new Log());
        long colored = menu.getElements().stream().filter(LabelElement.class::isInstance).map(e -> e.properties().label())
                .filter(t -> t.equals(Component.text("코코넛", NamedTextColor.AQUA))).count();
        assertEquals(3, colored, "row, name plate and detail line");
    }

    @Test
    void equipAndUnequipButtonsFollowTheSelection() {
        Log log = new Log();
        // Selected but not equipped, nothing equipped: 장착 only.
        CoreMenu plain = ColorNickMenu.build(GLYPHS, view(ALL, null, c("red"), 1, true, false), log);
        click(plain, 400, 262);
        assertFalse(clickable(plain, 330, 262), "nothing to unequip");
        // Another color equipped: 장착 and 해제.
        CoreMenu other = ColorNickMenu.build(GLYPHS, view(ALL, c("gold"), c("red"), 1, true, false), log);
        click(other, 400, 262);
        click(other, 330, 262);
        assertEquals(List.of("equip red", "equip red", "unequip"), log.calls);
        // The selected color is the equipped one: "장착 중" is dead, 해제 works.
        CoreMenu on = ColorNickMenu.build(GLYPHS, view(ALL, c("red"), c("red"), 1, true, false), log);
        assertFalse(clickable(on, 400, 262));
        assertTrue(clickable(on, 330, 262));
        assertTrue(texts(on).contains("빨강 · 장착 중"));
    }

    @Test
    void busyAndLoadingFramesOfferNoChange() {
        CoreMenu busy = ColorNickMenu.build(GLYPHS, view(ALL, c("gold"), c("red"), 1, true, true), new Log());
        assertTrue(texts(busy).contains("처리 중"));
        assertFalse(clickable(busy, 400, 262));
        assertFalse(clickable(busy, 330, 262));

        CoreMenu loading = ColorNickMenu.build(GLYPHS, view(List.of(), null, null, 0, false, false), new Log());
        assertTrue(texts(loading).containsAll(List.of("색상 정보를 불러오는 중이에요", "잠시만 기다려 주세요")));
        assertFalse(clickable(loading, 400, 262));
    }

    @Test
    void emptyPaletteExplainsItself() {
        CoreMenu menu = ColorNickMenu.build(GLYPHS, view(List.of(), null, null, 0, true, false), new Log());
        assertTrue(texts(menu).containsAll(List.of("0 / 43개", "보유한 색상이 없어요", "1/1")));
    }

    @Test
    void pickKeepsSelectionThenEquippedThenFirstRow() {
        assertEquals(c("white"), ColorNickMenu.pick(ALL, 0, c("white"), c("black")));
        assertEquals(c("dark_green"), ColorNickMenu.pick(ALL, 0, null, c("dark_green")));
        assertEquals(c("dark_gray"), ColorNickMenu.pick(ALL, 1, null, c("dark_green")));
        // A color whose permission was taken away is no longer selectable.
        assertEquals(c("red"), ColorNickMenu.pick(List.of(c("red")), 0, c("blue"), null));
        assertNull(ColorNickMenu.pick(List.of(), 0, c("blue"), c("blue")));
    }

    @Test
    void everyPaletteColorHasItsOwnLabel() {
        assertEquals(ALL.size(), ALL.stream().map(NickColor::label).distinct().count());
    }

    @Test
    void titleScreenShowsColorTabOnlyWhenAvailable() {
        var title = TitleMenu.Title.of(7, "<gold>개척자", "첫 발걸음");
        Log log = new Log();
        CoreMenu with = TitleMenu.build(GLYPHS, new TitleMenu.View("코코넛", null, null, true, false,
                List.of(title), List.of(title), null, title, 0, "", true), log);
        assertTrue(images(with).contains("menu/ui/tab_green_off"));
        assertTrue(texts(with).contains("색 닉네임"));
        click(with, 120, 5);
        assertEquals(List.of("colorNick"), log.calls);

        CoreMenu without = TitleMenu.build(GLYPHS, new TitleMenu.View("코코넛", null, null, true, false,
                List.of(title), List.of(title), null, title, 0, "", false), log);
        assertFalse(images(without).contains("menu/ui/tab_green_off"));
        assertFalse(clickable(without, 120, 5));
    }

    @Test
    void hexAndGradientColorsGetTintedDyesAndColoredNames() {
        NickColor hex = c("a75a02"), gradient = c("gradient1");
        CoreMenu menu = ColorNickMenu.build(GLYPHS, view(List.of(hex, gradient), gradient, gradient, 0, true, false), new Log());
        var tints = menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> (GlyphElement) e)
                .filter(e -> e.glyph().id().equals("item/white_dye")).map(GlyphElement::tint).toList();
        assertEquals(List.of(hex.first(), gradient.first()), tints);
        assertTrue(texts(menu).containsAll(List.of("#A75A02", "그라데이션 1 · 장착 중", "그라데이션 · 2색")));
        long gradientNames = menu.getElements().stream().filter(LabelElement.class::isInstance).map(e -> e.properties().label())
                .filter(t -> PlainTextComponentSerializer.plainText().serialize(t).equals("코코넛") && colors(t).size() > 1).count();
        assertTrue(gradientNames >= 3, "row, name plate, detail and current lines use the gradient");
    }
}
