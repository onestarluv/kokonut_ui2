package org.crebs.coreui.menu.kokonut;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the 잔상 book screen and the 잔상 tab on the other 꾸미기 screens, built offline with stand-in glyphs. */
class AfterimageMenuTest {
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(AfterimageMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final List<AfterimageMenu.Effect> EFFECTS = List.of(
            new AfterimageMenu.Effect("flame", "불꽃", "blaze_powder", true),
            new AfterimageMenu.Effect("heart", "하트", "red_dye", true),
            new AfterimageMenu.Effect("petal_display", "벚꽃 잎", "pink_petals", false));

    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) { return new Glyph((String) key, 16, 16, 8, 16); }
        @Override public boolean containsKey(Object key) { return true; }
    };

    private static final class Log implements AfterimageMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void show(Player p, AfterimageMenu.Effect e) { calls.add("show " + e.id()); }
        public void page(Player p, int page) { calls.add("page " + page); }
        public void equip(Player p, AfterimageMenu.Effect e) { calls.add("equip " + e.id()); }
        public void unequip(Player p) { calls.add("unequip"); }
        public void titles(Player p) { calls.add("titles"); }
        public void colorNick(Player p) { calls.add("colorNick"); }
        public void damageSkin(Player p) { calls.add("damageSkin"); }
        public void close(Player p) { calls.add("close"); }
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
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

    @Test
    void tabSitsAfterTheOtherTabs() {
        assertEquals(108, AfterimageMenu.tabX(false, false));
        assertEquals(200, AfterimageMenu.tabX(true, false));
        assertEquals(292, AfterimageMenu.tabX(true, true));
        // The fourth tab still ends before the close button.
        assertTrue(AfterimageMenu.tabX(true, true) + 88 <= 432);
    }

    @Test
    void listShowsStateAndOtherTabsLeadBack() {
        Log log = new Log();
        CoreMenu menu = AfterimageMenu.build(GLYPHS,
                new AfterimageMenu.View(EFFECTS, "flame", EFFECTS.get(1), 0, true, true), log);
        List<String> texts = texts(menu);
        assertTrue(texts.contains("2 / 3개"), texts.toString());
        assertTrue(texts.contains("사용 중"));
        assertTrue(texts.contains("잠김"));
        click(menu, 20, 5);
        click(menu, 120, 5);
        click(menu, 210, 5);
        assertEquals(List.of("titles", "colorNick", "damageSkin"), log.calls);
    }

    @Test
    void lockedEffectCannotBeEquipped() {
        Log log = new Log();
        CoreMenu locked = AfterimageMenu.build(GLYPHS,
                new AfterimageMenu.View(EFFECTS, null, EFFECTS.get(2), 0, false, false), log);
        assertTrue(texts(locked).contains("아직 얻지 못한 잔상이에요."));
        CoreMenu usable = AfterimageMenu.build(GLYPHS,
                new AfterimageMenu.View(EFFECTS, null, EFFECTS.get(1), 0, false, false), log);
        click(usable, 240 + 150, 258 + 5);
        assertEquals(List.of("equip heart"), log.calls);
    }

    @Test
    void otherScreensDrawTheTabOnlyWhenGiven() {
        List<String> opened = new ArrayList<>();
        var view = new DamageSkinMenu.View(true, List.of(), 0, null, null, 0, true);
        CoreMenu without = DamageSkinMenu.build(GLYPHS, view, new NoDamageActions());
        assertFalse(texts(without).contains("잔상"));
        CoreMenu with = DamageSkinMenu.build(GLYPHS, view, p -> opened.add("afterimage"), new NoDamageActions());
        assertTrue(texts(with).contains("잔상"));
        click(with, AfterimageMenu.tabX(true, true) + 10, 5);
        assertEquals(List.of("afterimage"), opened);
    }

    private static final class NoDamageActions implements DamageSkinMenu.Actions {
        public void select(Player p, DamageSkinMenu.Skin skin) { }
        public void page(Player p, int page) { }
        public void equip(Player p, DamageSkinMenu.Skin skin) { }
        public void unequip(Player p) { }
        public void titles(Player p) { }
        public void colorNick(Player p) { }
        public void close(Player p) { }
    }
}
